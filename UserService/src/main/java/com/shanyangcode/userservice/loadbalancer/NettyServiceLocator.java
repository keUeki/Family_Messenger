package com.shanyangcode.userservice.loadbalancer;

import com.shanyangcode.common.constant.CommonConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NettyServiceLocator {

    /**
     * RealTimeService 在 Nacos 中注册的 Netty 端口元数据 key
     */
    private static final String NETTY_PORT_METADATA_KEY = "netty-port";

    /**
     * 兜底端口，与 RealTimeService 的 netty.port 默认值保持一致
     */
    private static final int DEFAULT_NETTY_PORT = 9101;

    /**
     * WebSocket 协议前缀，前端需要带 scheme 的完整地址才能建立连接
     */
    private static final String WS_SCHEME = "ws://";

    @Resource
    private DiscoveryClient discoveryClient;


    public String getServiceInstance(String userId){
        List<ServiceInstance> instances = discoveryClient.getInstances(CommonConstant.DISCOVERY_CLIENT_NAME);
        if (instances.isEmpty()) {
            log.warn("Nacos 中没有可用的 {} 实例，无法下发 nettyUri", CommonConstant.DISCOVERY_CLIENT_NAME);
            return null;
        }
        ServiceInstance instance = new UrlHashLoadBalancer().select(instances, userId);

        return WS_SCHEME + instance.getHost() + ":" + resolveNettyPort(instance) + CommonConstant.NETTY_SERVICE_URI;
    }

    /**
     * Netty 监听的端口与 Spring Boot 的 server.port 不同，
     * 需要从注册中心的元数据里读取，读取不到时回退到默认端口。
     */
    private int resolveNettyPort(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String nettyPort = metadata == null ? null : metadata.get(NETTY_PORT_METADATA_KEY);
        if (StringUtils.isNotBlank(nettyPort)) {
            try {
                return Integer.parseInt(nettyPort.trim());
            } catch (NumberFormatException e) {
                log.warn("{} 实例的 {} 元数据不是合法端口: {}，回退到默认端口 {}",
                        CommonConstant.DISCOVERY_CLIENT_NAME, NETTY_PORT_METADATA_KEY, nettyPort, DEFAULT_NETTY_PORT);
            }
        } else {
            log.warn("{} 实例未注册 {} 元数据，回退到默认端口 {}",
                    CommonConstant.DISCOVERY_CLIENT_NAME, NETTY_PORT_METADATA_KEY, DEFAULT_NETTY_PORT);
        }
        return DEFAULT_NETTY_PORT;
    }
}