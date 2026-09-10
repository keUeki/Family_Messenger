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
     * Metadata key under which RealTimeService registers its Netty port in Nacos
     */
    private static final String NETTY_PORT_METADATA_KEY = "netty-port";

    /**
     * Fallback port, kept in step with the default of RealTimeService's netty.port
     */
    private static final int DEFAULT_NETTY_PORT = 9101;

    /**
     * WebSocket scheme prefix; the client needs a full address including the scheme to connect
     */
    private static final String WS_SCHEME = "ws://";

    @Resource
    private DiscoveryClient discoveryClient;


    public String getServiceInstance(String userId){
        List<ServiceInstance> instances = discoveryClient.getInstances(CommonConstant.DISCOVERY_CLIENT_NAME);
        if (instances.isEmpty()) {
            log.warn("No usable {} instance in Nacos, cannot hand out a nettyUri", CommonConstant.DISCOVERY_CLIENT_NAME);
            return null;
        }
        ServiceInstance instance = new UrlHashLoadBalancer().select(instances, userId);

        return WS_SCHEME + instance.getHost() + ":" + resolveNettyPort(instance) + CommonConstant.NETTY_SERVICE_URI;
    }

    /**
     * The Netty listen port differs from Spring Boot's server.port, so it is read from the
     * registry metadata, falling back to the default port when it is absent.
     */
    private int resolveNettyPort(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String nettyPort = metadata == null ? null : metadata.get(NETTY_PORT_METADATA_KEY);
        if (StringUtils.isNotBlank(nettyPort)) {
            try {
                return Integer.parseInt(nettyPort.trim());
            } catch (NumberFormatException e) {
                log.warn("The {} metadata on the {} instance is not a valid port: {}, falling back to the default port {}",
                        CommonConstant.DISCOVERY_CLIENT_NAME, NETTY_PORT_METADATA_KEY, nettyPort, DEFAULT_NETTY_PORT);
            }
        } else {
            log.warn("The {} instance did not register {} metadata, falling back to the default port {}",
                    CommonConstant.DISCOVERY_CLIENT_NAME, NETTY_PORT_METADATA_KEY, DEFAULT_NETTY_PORT);
        }
        return DEFAULT_NETTY_PORT;
    }
}