package com.alibaba.dubbo.rpc.cluster.router.modulo;

/**
 * <p>Description:自定义模路由器测试参数类</p>
 * <pre></pre>
 * <p>Company: 远峰科技</p>
 *
 * @author wupengyu
 * @date 2017/6/15 16:08
 */
public class ModuloRouterArgument {

    public ModuloRouterArgument(Integer id){
        this.id = id;
    }
    private Integer id;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

}
