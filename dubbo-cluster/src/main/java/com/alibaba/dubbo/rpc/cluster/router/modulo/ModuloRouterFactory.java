package com.alibaba.dubbo.rpc.cluster.router.modulo;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.cluster.Router;
import com.alibaba.dubbo.rpc.cluster.RouterFactory;

/**
 * <p>Description: 取模路由工厂实体类</p>
 * <pre></pre>
 * <p>Company: 远峰科技</p>
 *
 * @author wupengyu
 * @date 2017/6/6 16:26
 */
public class ModuloRouterFactory implements RouterFactory {
    public static final String NAME = "modulo";

    public Router getRouter(URL url) {
        return new ModuloRouter(url);
    }
}
