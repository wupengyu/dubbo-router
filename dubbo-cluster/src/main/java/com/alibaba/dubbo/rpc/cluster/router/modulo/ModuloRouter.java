package com.alibaba.dubbo.rpc.cluster.router.modulo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Router;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Description: 取模路由实体类</p>
 * <pre></pre>
 * <p>Company: 远峰科技</p>
 *
 * @author wupengyu
 * @date 2017/6/6 16:26
 */
public class ModuloRouter implements Router, Comparable<Router> {

    private static final Logger logger = LoggerFactory.getLogger(ModuloRouter.class);

    private final URL url;

    //优先级
    private final int priority;

    //匹配条件
    private final List<ModuloMatchPair> moduloMatchPairs;

    //指定参数对应的类名
    private final String argumentClassName;

    //指定参数所对应的属性名称
    private final String argumentPropertyName;

    //用于取模的被除数
    private final int dividend;

    //常量
    public static final String DIVIDEND = "dividend";

    public static final String MODULO = "modulo";

    public static final String ARGUMENT_CLASS = "argumentClass";

    public static final String ARGUMENT_PROPERTY = "argumentProperty";


    /**
     * 初始化
     *
     * @param url
     */
    public ModuloRouter(URL url) {
        this.url = url;
        this.priority = url.getParameter(Constants.PRIORITY_KEY, 0);

        try {
            int dividend = url.getParameter(DIVIDEND, 0);
            if(dividend == 0 ){
                throw new IllegalArgumentException("Illegal modulo route dividend!");
            }
            this.dividend = dividend;

            String argumentClassName = url.getParameter(ARGUMENT_CLASS, "");
            if (argumentClassName == null || argumentClassName.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal modulo route argumentClassName!");
            }
            this.argumentClassName = argumentClassName;

            String argumentPropertyName = url.getParameter(ARGUMENT_PROPERTY, "");
            if (argumentPropertyName == null || argumentPropertyName.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal modulo route argumentPropertyName!");
            }
            this.argumentPropertyName = argumentPropertyName;

            String rules = url.getParameterAndDecoded(Constants.RULE_KEY);
            if (rules == null || rules.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal route rule!");
            }

            //取模规则列表
            String[] ruleArr = rules.split("=>");

            List<ModuloMatchPair> _moduloMatchPairs = new ArrayList<ModuloMatchPair>();
            for (String rule : ruleArr) {
                Integer modulo = null;

                Map<String, MatchPair> condition = StringUtils.isBlank(rule) || "true".equals(rule) ? new HashMap<String, MatchPair>() : parseRule(rule);
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
                _moduloMatchPairs.add(new ModuloMatchPair(condition, modulo));
            }

            this.moduloMatchPairs = _moduloMatchPairs;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public URL getUrl() {
        return url;
    }

    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        //判空处理
        if (invokers == null || invokers.size() == 0 || invocation == null || invocation.getArguments() == null ||
                invocation.getParameterTypes() == null) {
            return invokers;
        }

        List<Invoker<T>> result = new ArrayList<Invoker<T>>();

        try {
            //找到路由指定参数对应的位置
            Class<?>[] parameterType = invocation.getParameterTypes();
            if (parameterType == null || parameterType.length == 0) {
                return result;
            }
            Integer index = null;
            for (int i = 0; i < parameterType.length; i++) {
                if (parameterType[i].getName().equals(argumentClassName)) {
                    index = i;
                    break;
                }
            }

            //找不到该参数则返回所有invoker
            if (index == null) {
                return invokers;
            }

            //取到路由指定参数的数值进行取模
            Object[] arguments = invocation.getArguments();
            if (arguments.length == 0) {
                return result;
            }
            Integer argument = (Integer) getFieldValueByName(argumentPropertyName, arguments[index]);

            //计算得到模
            int modulo = argument % dividend;

            //按照路由规则进行过滤
            for (Invoker<T> invoker : invokers) {
                for (ModuloMatchPair moduloMatchPair : moduloMatchPairs) {
                    if (moduloMatchPair.isMatch(modulo, invoker.getUrl())) {
                        result.add(invoker);
                        break;
                    }
                }
            }

            if (CollectionUtils.isEmpty(result))
                logger.error("dubbo.moduloRouter : 没有匹配上的provider.");

            return result;
        } catch (Throwable t) {
            logger.error("Failed to execute modulo router rule: " + getUrl() + ", invokers: " + invokers + ", cause: " + t.getMessage(), t);
        }

        return result;
    }

    /**
     * 根据属性名获取属性值
     */
    private Object getFieldValueByName(String fieldName, Object o) {
        try {
            String firstLetter = fieldName.substring(0, 1).toUpperCase();
            String getter = "get" + firstLetter + fieldName.substring(1);
            Method method = o.getClass().getMethod(getter, new Class[]{});
            Object value = method.invoke(o, new Object[]{});
            return value;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private static Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");

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
     * 条件匹配
     *
     * @param condition ： 条件
     * @param url       ： dubbo url
     * @return
     */
    private static boolean matchCondition(Map<String, MatchPair> condition, URL url) {
        Map<String, String> sample = url.toMap();
        for (Map.Entry<String, String> entry : sample.entrySet()) {
            String key = entry.getKey();
            MatchPair pair = condition.get(key);
            if (pair != null && !pair.isMatch(entry.getValue(), null)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按照优先级进行排序
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
     * URL条件匹配类
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
     * 取模匹配类
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

        public boolean isMatch(int _modulo, URL url) {
            if (url == null) {
                return false;
            }

            if (modulo != _modulo) {
                return false;
            }
            if (!matchCondition(condition, url)) {
                return false;
            }
            return true;
        }

    }

}
