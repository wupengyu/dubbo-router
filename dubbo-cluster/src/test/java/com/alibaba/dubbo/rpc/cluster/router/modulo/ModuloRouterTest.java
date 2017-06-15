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
    private URL getRouteUrl(String rule, String argumentClass, String argumentProperty, Integer dividend) {
        //规则
        URL url = rule == null ? SCRIPT_URL : SCRIPT_URL.addParameterAndEncoded(Constants.RULE_KEY, rule);
        //参数类名
        url = argumentClass == null ? url : url.addParameterAndEncoded(ModuloRouter.ARGUMENT_CLASS, argumentClass);
        //参数属性名
        url = argumentProperty == null ? url : url.addParameterAndEncoded(ModuloRouter.ARGUMENT_PROPERTY, argumentProperty);
        //被除数
        url = dividend == null ? url : url.addParameter(ModuloRouter.DIVIDEND, dividend);
        return url;
    }

    /**
     * 测试返回空list
     */
    @Test
    public void testRoute_ReturnEmpty() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & modulo = 1",
                ModuloRouterArgument.class.getName(), ModuloRouterArgument.class.getDeclaredFields()[0].getName(), 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        invokers.add(new MockInvoker<String>());
        invokers.add(new MockInvoker<String>());
        invokers.add(new MockInvoker<String>());

        //造参数
        Class<?>[] classes = {ModuloRouterArgument.class};
        Object[] arguments = {new ModuloRouterArgument(11)};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(0, fileredInvokers.size());
    }

    /**
     * 测试返回所有符合条件的Invoker
     */
    @Test
    public void testRoute_ReturnAll() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & modulo = 1",
                ModuloRouterArgument.class.getName(), ModuloRouterArgument.class.getDeclaredFields()[0].getName(), 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        Invoker<String> invoker1 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        Invoker<String> invoker2 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        Invoker<String> invoker3 = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker1);
        invokers.add(invoker2);
        invokers.add(invoker3);

        //造参数
        Class<?>[] classes = {ModuloRouterArgument.class};
        //11除2模为1 模匹配
        Object[] arguments = {new ModuloRouterArgument(11)};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(3, fileredInvokers.size());
    }

    /**
     * RpcInvocation Null检验
     */
    @Test
    public void testRoute_NullParam() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                ModuloRouterArgument.class.getName(), ModuloRouterArgument.class.getDeclaredFields()[0].getName(), 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 都匹配
        Invoker<String> invoker = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker);

        List<Invoker<String>> invokersNull = new ArrayList<Invoker<String>>();

        //造会话域
        Class<?>[] classes = {ModuloRouterArgument.class};
        RpcInvocation rpcInvocationNull = new RpcInvocation();
        RpcInvocation rpcInvocationArgumentNull = new RpcInvocation("testMethod", classes, null);
        RpcInvocation rpcInvocationArgumentError = new RpcInvocation("testMethod", new Class[]{Integer.class}, null);

        //会话域为空
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), rpcInvocationNull);
        Assert.assertEquals(0, fileredInvokers.size());

        //参数为空/找不到参数
        fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), rpcInvocationArgumentNull);
        Assert.assertEquals(0, fileredInvokers.size());

        //参数错误
        fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), rpcInvocationArgumentError);
        Assert.assertEquals(0, fileredInvokers.size());
    }

    /**
     * 按照ip+port 过滤
     */
    @Test
    public void testRoute_FilterWithIpPort() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                ModuloRouterArgument.class.getName(), ModuloRouterArgument.class.getDeclaredFields()[0].getName(), 2));

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
        Class<?>[] classes = {ModuloRouterArgument.class};
        //11除2模为1 模匹配
        Object[] arguments = {new ModuloRouterArgument(11)};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(1, fileredInvokers.size());
    }

    /**
     * 对模进行过滤
     */
    @Test
    public void testRoute_FilterWithModulo() {
        //本条路由条件 ip=本机ip, port=20880, modulo=1，dividend=2;
        Router router = new ModuloRouterFactory().getRouter(getRouteUrl("host = " + NetUtils.getLocalHost() + " & port = 20880" + " & modulo = 1",
                ModuloRouterArgument.class.getName(), ModuloRouterArgument.class.getDeclaredFields()[0].getName(), 2));

        //造提供者
        List<Invoker<String>> invokers = new ArrayList<Invoker<String>>();
        // ip=本机ip, port=20880 都匹配
        Invoker<String> invoker = new MockInvoker<String>(URL.valueOf("dubbo://" + NetUtils.getLocalHost() + ":20880/com.wpy.TestService"));
        invokers.add(invoker);

        //造参数
        Class<?>[] classes = {ModuloRouterArgument.class};
        //12除2模为0 模不匹配
        Object[] arguments = {new ModuloRouterArgument(12)};

        //发起路由
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments));

        //预期结果
        Assert.assertEquals(0, fileredInvokers.size());
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
                ModuloRouterArgument.class.getName(), ModuloRouterArgument.class.getDeclaredFields()[0].getName(), 3));

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
        Class<?>[] classes = {ModuloRouterArgument.class};
        //模为0
        Object[] arguments0 = {new ModuloRouterArgument(12)};
        //模为1
        Object[] arguments1 = {new ModuloRouterArgument(13)};
        //模为2
        Object[] arguments2 = {new ModuloRouterArgument(14)};


        //模为0匹配第一个提供者
        List<Invoker<String>> fileredInvokers = router.route(invokers, URL.valueOf("consumer://" +
                NetUtils.getLocalHost() + "/com.wpy.TestService"), new RpcInvocation("testMethod",
                classes, arguments0));
        Assert.assertEquals(1, fileredInvokers.size());
        Assert.assertEquals(NetUtils.getLocalHost(), fileredInvokers.get(0).getUrl().getHost());
        Assert.assertEquals(20880, fileredInvokers.get(0).getUrl().getPort());

        //模为1匹配第二个提供者
        fileredInvokers.clear();
        fileredInvokers  = router.route(invokers, URL.valueOf("consumer://" +
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


}
