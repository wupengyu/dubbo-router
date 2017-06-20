package com.alibaba.dubbo.rpc.cluster.router.modulo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.cluster.Router;
import com.alibaba.dubbo.rpc.cluster.router.MockInvoker;
import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Description: 自定义模路由器测试类</p>
 * <pre></pre>
 * <p>Company: 远峰科技</p>
 *
 * @author wupengyu
 * @date 2017/6/15 15:02
 */
public class ModuloRouterTest {

    private URL SCRIPT_URL = URL.valueOf("modulo://" + NetUtils.getLocalHost() + "/com.wpy.TestService");

    /**
     * 在url添加路由器所需参数
     */
    private URL getRouteUrl(String rule, String divisorArgumentName, Integer dividend) {
        //规则
        URL url = rule == null ? SCRIPT_URL : SCRIPT_URL.addParameterAndEncoded(Constants.RULE_KEY, rule);
        //参数属性名
        url = divisorArgumentName == null ? url : url.addParameterAndEncoded(ModuloRouter.DIVISOR_ARGUMENT_NAME, divisorArgumentName);
        //被除数
        url = dividend == null ? url : url.addParameter(ModuloRouter.DIVIDEND, dividend);
        return url;
    }

    /**
     * RpcInvocation 或 参数 为 Null 返回原有invokers
     */
    @Test
    public void testRoute_NullParam() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                "id", 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 都匹配
        Invoker<String> invoker = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker);

        List<Invoker<String>> invokersNull = new ArrayList<Invoker<String>>();

        //造会话域
        Class<?>[] classes = {String.class};
        RpcInvocation rpcInvocationNull = new RpcInvocation();
        RpcInvocation rpcInvocationArgumentNull = new RpcInvocation("testMethod", classes, null);

        //会话域为空
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), rpcInvocationNull);
        Assert.assertEquals(1, fileredInvokers.size());

        //参数为空/找不到参数
        fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), rpcInvocationArgumentNull);
        Assert.assertEquals(1, fileredInvokers.size());
    }

    /**
     * 没有找到除数对应的参数 则返回所有Invoker
     */
    @Test
    public void testRoute_ReturnAll() {
        //除数属性名为 ： id
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host != " + NetUtils.getLocalHost() + " & modulo = 1", "id", 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        Invoker<String> invoker1 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        Invoker<String> invoker2 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        Invoker<String> invoker3 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker1);
        invokers.add(invoker2);
        invokers.add(invoker3);

        //造参数
        Class<?>[] classes = {String.class};
        // json里没有id
        Object[] arguments = {"{\"host\":11,\"name\":\"aaa\"}"};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(3, fileredInvokers.size());
    }

    /**
     * 默认 force=true
     * 没有匹配上的则返回空
     * 模没有匹配上
     */
    @Test
    public void testRoute_NotMatch() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                "id", 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 都匹配
        Invoker<String> invoker = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker);

        //造参数
        Class<?>[] classes = {String.class};
        //12除2模为0 模不匹配
        Object[] arguments = {"{\"id\":12,\"name\":\"aaa\"}"};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(0, fileredInvokers.size());
    }

    /**
     * 设置 force = false
     * 没有匹配上的则返回所有invoker
     * 模没有匹配上
     */
    @Test
    public void testRoute_force() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        URL url = getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                "id", 2);
        url = url.addParameter("force", false);
        Router router = new ModuloRouterFactory().getRouter(url);

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 都匹配
        Invoker<String> invoker = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker);

        //造参数
        Class<?>[] classes = {String.class};
        //12除2模为0 模不匹配
        Object[] arguments = {"{\"id\":12,\"name\":\"aaa\"}"};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(1, fileredInvokers.size());
    }

    /**
     * 模一致的前提
     * 按照ip+port 过滤
     */
    @Test
    public void testRoute_FilterWithIpPort() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                "id", 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 都匹配
        Invoker<String> invoker1 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        //ip不匹配
        Invoker<String> invoker2 = new MockInvoker<String>(URL.valueOf("dubbo://0.0.0.0:20880/com.wpy.TestService"));
        //port不匹配
        Invoker<String> invoker3 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20881/com.wpy.TestService"));
        invokers.add(invoker1);
        invokers.add(invoker2);
        invokers.add(invoker3);

        //造参数
        Class<?>[] classes = {String.class};
        //11除2模为1 模匹配
        Object[] arguments = {"{\"id\":11,\"name\":\"aaa\"}"};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(1, fileredInvokers.size());
    }


    /**
     * 对ip+port+modulo进行一对一匹配分发
     * 模为 0 匹配 ip=本机ip  port=20880
     * 模为 1 匹配 ip=192.168.1.1  port=20880 （与第一条ip不同）
     * 模为 2 匹配 ip=本机ip  port=20881 （与第一条port不同）
     */
    @Test
    public void testRouteOneToOne() {
        //本条路由条件 dividend = 3
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl(
                "host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 0" +
                        " => host = " + "192.168.1.1" + " & port = 20880" + " & modulo = 1" +
                        " => host = " + NetUtils.getLocalHost() + " & port = 20881" + " & modulo = 2",
                "id", 3));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880
        Invoker<String> invoker1 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        //ip=192.168.1.1, port=20880
        Invoker<String> invoker2 = new MockInvoker<String>(URL.valueOf("dubbo://192.168.1.1:20880/com.wpy.TestService"));
        //ip=本机ip, port=20881
        Invoker<String> invoker3 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20881/com.wpy.TestService"));
        invokers.add(invoker1);
        invokers.add(invoker2);
        invokers.add(invoker3);

        //造参数
        Class<?>[] classes = {String.class};
        //模为0
        Object[] arguments0 = {"{\"id\":12,\"name\":\"aaa\"}"};
        //模为1
        Object[] arguments1 = {"{\"id\":13,\"name\":\"aaa\"}"};
        //模为2
        Object[] arguments2 = {"{\"id\":14,\"name\":\"aaa\"}"};


        //模为0匹配第一个提供者
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments0));
        Assert.assertEquals(1, fileredInvokers.size());
        Assert.assertEquals(NetUtils.getLocalHost(), fileredInvokers.get(0).getUrl().getHost());
        Assert.assertEquals(20880, fileredInvokers.get(0).getUrl().getPort());

        //模为1匹配第二个提供者
        fileredInvokers.clear();
        fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments1));
        Assert.assertEquals(1, fileredInvokers.size());
        Assert.assertEquals("192.168.1.1", fileredInvokers.get(0).getUrl().getHost());
        Assert.assertEquals(20880, fileredInvokers.get(0).getUrl().getPort());

        //模为2匹配第三个提供者
        fileredInvokers.clear();
        fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments2));
        Assert.assertEquals(1, fileredInvokers.size());
        Assert.assertEquals(NetUtils.getLocalHost(), fileredInvokers.get(0).getUrl().getHost());
        Assert.assertEquals(20881, fileredInvokers.get(0).getUrl().getPort());
    }

    /**
     * 支持条件可以为 = 或 !=
     * 多个值用逗号分隔，以星号结尾表示通配地址段
     * 匹配规则跟官方提供的条件路由一致
     *
     */
    @Test
    public void testRoute_Multiple_Ip_Port() {
        //本条路由条件 ip = 本机ip ip != 192.168.1.1,192.168.1.2
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " +NetUtils.getLocalHost() + " &host != 192.168.1.1,192.168.1.2 & port = 20880" + " & modulo = 1",
                "id", 2));

        //测试*匹配 本条路由条件 ip = 本机ip ip != 192.168.1.*
        Router router1 = new ModuloRouterFactory().getRouter(getRouteUrl("host = " +NetUtils.getLocalHost() + " &host != 192.168.1.1,192.168.1.2 & port = 20880" + " & modulo = 1",
                "id", 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 匹配
        Invoker<String> invoker1 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        //ip = 192.168.1.1 port =20880 不匹配
        Invoker<String> invoker2 = new MockInvoker<String>(URL.valueOf("dubbo://192.168.1.1:20880/com.wpy.TestService"));
        //ip = 192.168.1.1 port =20880 不匹配
        Invoker<String> invoker3 = new MockInvoker<String>(URL.valueOf("dubbo://192.168.1.2:20880/com.wpy.TestService"));
        invokers.add(invoker1);
        invokers.add(invoker2);
        invokers.add(invoker3);

        //造参数
        Class<?>[] classes = {String.class};
        //11除2模为1 模匹配
        Object[] arguments = {"{\"id\":11,\"name\":\"aaa\"}"};

        //路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(1, fileredInvokers.size());

        //路由
        fileredInvokers.clear();
        fileredInvokers = router1.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(1, fileredInvokers.size());
    }

}
