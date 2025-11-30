package com.example.cache.config;

import java.net.URI;
import java.util.Optional;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        String host = redisProperties.getHost() != null ? redisProperties.getHost() : "127.0.0.1";
        int port = redisProperties.getPort();
        String scheme = "redis";
        String password = redisProperties.getPassword();

        if (StringUtils.hasText(redisProperties.getUrl())) {
            URI redisUri = URI.create(redisProperties.getUrl());
            host = redisUri.getHost() != null ? redisUri.getHost() : host;
            port = redisUri.getPort() > 0 ? redisUri.getPort() : port;
            scheme = Optional.ofNullable(redisUri.getScheme()).orElse(scheme);

            if (!StringUtils.hasText(password) && StringUtils.hasText(redisUri.getUserInfo())) {
                String[] credentials = redisUri.getUserInfo().split(":", 2);
                password = credentials.length == 2 ? credentials[1] : credentials[0];
            }
        }

        Config config = new Config();
        RedisProperties.Cluster cluster = redisProperties.getCluster();
        if (cluster != null && cluster.getNodes() != null && !cluster.getNodes().isEmpty()) {
            String finalScheme = scheme;
            String[] nodeAddresses = cluster.getNodes().stream()
                    .map(node -> String.format("%s://%s", finalScheme, node))
                    .toArray(String[]::new);

            var clusterServers = config.useClusterServers()
                    .addNodeAddress(nodeAddresses)
                    .setScanInterval(2000);

            if (StringUtils.hasText(password)) {
                clusterServers.setPassword(password);
            }

            return Redisson.create(config);
        }

        var singleServer = config.useSingleServer()
                .setAddress(String.format("%s://%s:%d", scheme, host, port))
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20);

        if (StringUtils.hasText(password)) {
            singleServer.setPassword(password);
        }

        return Redisson.create(config);
    }
}
