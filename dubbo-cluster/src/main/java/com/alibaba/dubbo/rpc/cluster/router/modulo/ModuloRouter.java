package com.alibaba.dubbo.rpc.cluster.router.modulo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Router;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Description: 取模路由实体类
 * 本路由按照取模规则给消费者匹配对应的提供者
 * <p>
 * 例如：设置的路由url为：
 * route://0.0.0.0/com.wpy.service.ProviderTest?
 * category=routers&dynamic=false&force=false&name=test&priority=0
 * &router=modulo&argumentClass=com.wpy.SplitTableRouterId&argumentProperty=id&dividend=3
 * &rule=host = 10.10.1.178 & port = 20882 & modulo = 0
 * => host = 10.10.1.178 & port = 20883 & modulo = 1
 * => host = 10.10.1.178 & port = 20884 & modulo = 2
 * <p>
 * router=modulo 代表要对提供者进行过滤的路由器是本路由器
 * dividend=3 代表这次取模的被除数是3，除数来自消费者里的第一个参数
 * rule= 后面的内容都是根据模来进行匹配的条件
 * => 这个符号区分为一条新的匹配条件
 * 匹配条件一： host = 10.10.1.178 & port = 20882 & modulo = 0 代表如果模为0 则在提供者里面选取host为10.10.1.178、port为20882的提供者放行，其他的全部过滤掉
 * 匹配条件二： host = 10.10.1.178 & port = 20883 & modulo = 0 代表如果模为1 则在提供者里面选取host为10.10.1.178、port为20882的提供者放行，其他的全部过滤掉
 * 匹配条件三： host = 10.10.1.178 & port = 20884 & modulo = 0 代表如果模为2 则在提供者里面选取host为10.10.1.178、port为20882的提供者放行，其他的全部过滤掉
 * <p>
 * </p>
 * <pre></pre>
 * <p>Company: 远峰科技</p>
 *
 * @author wupengyu
 * @date 2017/6/6 16:26
 */
public class ModuloRouter implements Router, Comparable<Router> {

    private static final Logger logger = LoggerFactory.getLogger(ModuloRouter.class);

    //url
    private final URL url;

    //优先级
    private final int priority;

    //当路由结果为空时，是否强制执行，如果不强制执行，路由结果为空的路由规则将自动失效，可不填，本路由缺省为true
    private final boolean force;

    //匹配条件
    private final List<ModuloMatchPair> moduloMatchPairs;

    //指定参数所对应的属性名称
    private final String divisorArgumentName;

    //用于取模的被除数
    private final int dividend;

    //被除数
    public static final String DIVIDEND = "dividend";

    //除数对应的属性名
    public static final String DIVISOR_ARGUMENT_NAME = "divisorArgumentName";

    //模
    public static final String MODULO = "modulo";

    /**
     * 初始化
     *
     * @param url
     *
     * url 示例：route://0.0.0.0/com.wpy.service.ProviderTest?category=routers&runtime=true&priority=10&name=test&router=modulo&divisorArgumentName=id&dividend=3&rule=host = 172.16.135.47 & port = 20882 & modulo = 0 => host = 172.16.135.47 & port = 20883 & modulo = 1 => host = 172.16.135.47 & port = 20884 & modulo = 2
     *
     */
    public ModuloRouter(URL url) {
        this.url = url;
        this.priority = url.getParameter(Constants.PRIORITY_KEY, 0);
        this.force = url.getParameter(Constants.FORCE_KEY, true);

        try {
            int dividend = url.getParameter(DIVIDEND, 0);
            if (dividend == 0) {
                throw new IllegalArgumentException("Illegal modulo route dividend!");
            }
            this.dividend = dividend;

            String divisorArgumentName = url.getParameter(DIVISOR_ARGUMENT_NAME, "");
            if (divisorArgumentName == null || divisorArgumentName.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal modulo route divisorArgumentName!");
            }
            this.divisorArgumentName = divisorArgumentName;

            String rules = url.getParameterAndDecoded(Constants.RULE_KEY);
            if (rules == null || rules.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal route rule!");
            }

            //取模规则列表
            String[] ruleArr = rules.split("=>");

            List<ModuloMatchPair> moduloMatchPairs = new ArrayList<ModuloMatchPair>();
            for (String rule : ruleArr) {
                //取得分发规则列表
                Map<String, MatchPair> condition = StringUtils.isBlank(rule) || "true".equals(rule) ? new HashMap<String, MatchPair>() : parseRule(rule);

                //从分发规则里单独拎出module条件，如果分发规则里没有module条件则不添加到分发规则列表
                Integer modulo = null;
                if (condition.containsKey(MODULO)) {
                    Set<String> set = condition.get(MODULO).matches;
                    Iterator<String> it = set.iterator();
                    while (it.hasNext()) {
                        modulo = Integer.parseInt(it.next());
                    }
                    condition.remove(MODULO);
                } else {
                    continue;
                }
                moduloMatchPairs.add(new ModuloMatchPair(condition, modulo));
            }

            this.moduloMatchPairs = moduloMatchPairs;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 路由方法
     *
     * @param invokers   ： 提供者
     * @param url        ： url
     * @param invocation ：会话域
     * @return invokers : 过滤后留下的提供者
     * @throws RpcException
     */
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        //判空处理 提供者为空/会话域为空/会话域没有参数 都直接返回
        if (invokers == null || invokers.size() == 0 || invocation == null || invocation.getArguments() == null
                || invocation.getArguments().length <= 0) {
            return invokers;
        }

        List<Invoker<T>> result = new ArrayList<Invoker<T>>();

        //从会话域里的第一个参数中找到取模所需要的除数
        Integer divisor = getDivisorFromInvocation(invocation);

        //没有找到需要的除数则返回所有的提供者
        if (divisor == null) {
            return invokers;
        }

        try {
            //计算得到模
            int modulo = divisor % dividend;

            //按照路由规则进行过滤
            for (Invoker<T> invoker : invokers) {
                for (ModuloMatchPair moduloMatchPair : moduloMatchPairs) {
                    if (moduloMatchPair.isMatch(modulo, invoker.getUrl())) {
                        result.add(invoker);
                        break;
                    }
                }
            }

            if (result.size() > 0) {
                return result;
            } else if (force) {
                logger.warn("The route result is empty and force execute. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey() + ", router: " + url.getParameterAndDecoded(Constants.RULE_KEY));
                return result;
            }
        } catch (Throwable t) {
            logger.error("Failed to execute modulo router rule: " + getUrl() + ", invokers: " + invokers + ", cause: " + t.getMessage(), t);
        }

        return invokers;
    }

    public URL getUrl() {
        return url;
    }

    private static Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");

    /**
     * 转化路由rule
     *
     * @param rule
     * @return
     * @throws ParseException
     */
    private static Map<String, MatchPair> parseRule(String rule)
            throws ParseException {
        Map<String, MatchPair> condition = new HashMap<String, MatchPair>();
        if (StringUtils.isBlank(rule)) {
            return condition;
        }
        // 匹配或不匹配Key-Value对
        MatchPair pair = null;
        // 多个Value值
        Set<String> values = null;
        final Matcher matcher = ROUTE_PATTERN.matcher(rule);
        while (matcher.find()) { // 逐个匹配
            String separator = matcher.group(1);
            String content = matcher.group(2);
            // 表达式开始
            if (separator == null || separator.length() == 0) {
                pair = new MatchPair();
                condition.put(content, pair);
            }
            // KV开始
            else if ("&".equals(separator)) {
                if (condition.get(content) == null) {
                    pair = new MatchPair();
                    condition.put(content, pair);
                } else {
                    condition.put(content, pair);
                }
            }
            // KV的Value部分开始
            else if ("=".equals(separator)) {
                if (pair == null)
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());

                values = pair.matches;
                values.add(content);
            }
            // KV的Value部分开始
            else if ("!=".equals(separator)) {
                if (pair == null)
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());

                values = pair.mismatches;
                values.add(content);
            }
            // KV的Value部分的多个条目
            else if (",".equals(separator)) { // 如果为逗号表示
                if (values == null || values.size() == 0)
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                values.add(content);
            } else {
                throw new ParseException("Illegal route rule \"" + rule
                        + "\", The error char '" + separator + "' at index "
                        + matcher.start() + " before \"" + content + "\".", matcher.start());
            }
        }
        return condition;
    }

    /**
     * 按照优先级进行排序
     *
     * @param o
     * @return
     */
    public int compareTo(Router o) {
        if (o == null || o.getClass() != ModuloRouter.class) {
            return 1;
        }
        ModuloRouter c = (ModuloRouter) o;
        return this.priority == c.priority ? url.toFullString().compareTo(c.url.toFullString()) : (this.priority > c.priority ? 1 : -1);
    }


    /**
     * 匹配条件类
     * 跟官方提供的条件路由匹配规则完全一致
     *
     * matches 对应的是条件里的 =
     * mismatches 对应的是条件里的 !=
     * 多个值用逗号分隔，以星号结尾表示通配地址段
     *
     */
    private static final class MatchPair {
        final Set<String> matches = new HashSet<String>();
        final Set<String> mismatches = new HashSet<String>();

        public boolean isMatch(String value, URL param) {

            for (String match : matches) {
                if (!UrlUtils.isMatchGlobPattern(match, value, param)) {
                    return false;
                }
            }
            for (String mismatch : mismatches) {
                if (UrlUtils.isMatchGlobPattern(mismatch, value, param)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 模条件匹配类
     */
    private static final class ModuloMatchPair {
        /**
         * URL匹配条件列表
         */
        private final Map<String, MatchPair> condition;

        /**
         * 模
         */
        private final int modulo;

        public ModuloMatchPair(Map<String, MatchPair> _condition, int _modulo) {
            this.condition = _condition;
            this.modulo = _modulo;
        }

        /**
         * 是否匹配
         *
         * @param _modulo ： 模
         * @param url ： url
         * @return true or false
         */
        public boolean isMatch(int _modulo, URL url) {
            // url为空 false
            if (url == null) {
                return false;
            }

            //模不相等 false
            if (modulo != _modulo) {
                return false;
            }

            //url里的条件不匹配 false
            if (!matchCondition(condition, url)) {
                return false;
            }

            return true;
        }
    }

    /**
     * 条件匹配
     *
     * @param condition ： 条件
     * @param url       ： dubbo url
     * @return
     */
    private static boolean matchCondition(Map<String, MatchPair> condition, URL url) {
        //将Url里的参数转化成一个Map
        Map<String, String> sample = url.toMap();

        //循环url参数map，跟condition里的Url条件匹配类进行匹配
        for (Map.Entry<String, String> entry : sample.entrySet()) {
            String key = entry.getKey();
            MatchPair pair = condition.get(key);
            //如果匹配条件类为空或者不匹配 false
            if (pair != null && !pair.isMatch(entry.getValue(), null)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从会话域中找到取模所需要的除数
     *
     * @param invocation
     * @return
     */
    private Integer getDivisorFromInvocation(Invocation invocation){
        try {
            //约定取模用的被除数在第一参数里面，第一个参数类型为String,格式为json
            Object[] arguments = invocation.getArguments();
            String json = (String) arguments[0];
            if (!StringUtils.isEmpty(json)) {
                String divisorStr = findValueFromJson(json, divisorArgumentName);
                if (!StringUtils.isEmpty(divisorStr)) {
                    return  Integer.valueOf(divisorStr);
                }
            }
        } catch (Exception e) {
            logger.warn("dubbo.moduloRouter : 未从消费者传过来的参数中找到取模所需要的除数！" + getUrl());
        }
        return null;
    }

    /**
     * 在json中取得指定key的value
     * <p>
     * 例如： 传入的 json={"id":12,"name":"aaa"}，key=id
     * 返回的结果是 12
     *
     * @param json
     * @param key
     * @return 返回String类型的value，未找到返回null
     */
    private String findValueFromJson(String json, String key) {
        json = json.substring(1, json.length() - 1);
        String[] strArr = json.split(",");
        if (strArr != null && strArr.length > 0) {
            for (String str : strArr) {
                String[] keyValue = str.split(":");
                if (keyValue != null && keyValue.length == 2) {
                    String _key = keyValue[0].replace("\"", "");
                    if (_key.equals(key)) {
                        return keyValue[1].replace("\"", "");
                    }
                }
            }
        }
        return null;
    }

}
