package com.example.cache.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Set;

@Component
public class TomcatThreadPoolMetrics {

    private static final Logger log = LoggerFactory.getLogger(TomcatThreadPoolMetrics.class);

    private final MeterRegistry meterRegistry;

    public TomcatThreadPoolMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void bind() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

            Set<ObjectName> threadPools =
                    mBeanServer.queryNames(new ObjectName("Tomcat:type=ThreadPool,*"), null);

            if (threadPools.isEmpty()) {
                log.warn("Tomcat ThreadPool MBeans not found. " +
                        "확인: server.tomcat.mbeanregistry.enabled=true 인지, WAS가 톰캣인지 확인 필요");
                return;
            }

            for (ObjectName objectName : threadPools) {
                String rawName = objectName.getKeyProperty("name");
                String connectorName = rawName != null
                        ? rawName.replaceAll("^\"|\"$", "")
                        : "unknown";

                log.info("Registering Tomcat thread pool metrics for connector: {}", connectorName);

                // 현재 쓰레드 수
                Gauge.builder("tomcat_thread_pool_current_threads",
                                () -> getAttributeAsDouble(mBeanServer, objectName, "currentThreadCount"))
                        .description("Current thread count of Tomcat connector")
                        .tag("connector", connectorName)
                        .register(meterRegistry);

                // Busy 쓰레드 수
                Gauge.builder("tomcat_thread_pool_busy_threads",
                                () -> getAttributeAsDouble(mBeanServer, objectName, "currentThreadsBusy"))
                        .description("Busy thread count of Tomcat connector")
                        .tag("connector", connectorName)
                        .register(meterRegistry);

                // 최대 쓰레드 수
                Gauge.builder("tomcat_thread_pool_max_threads",
                                () -> getAttributeAsDouble(mBeanServer, objectName, "maxThreads"))
                        .description("Max thread count of Tomcat connector")
                        .tag("connector", connectorName)
                        .register(meterRegistry);

                // 최대 커넥션 수
                Gauge.builder("tomcat_thread_pool_max_connections",
                                () -> getAttributeAsDouble(mBeanServer, objectName, "maxConnections"))
                        .description("Max connections of Tomcat connector")
                        .tag("connector", connectorName)
                        .register(meterRegistry);
            }

        } catch (Exception e) {
            log.warn("Failed to register Tomcat ThreadPool JMX metrics", e);
        }
    }

    private double getAttributeAsDouble(MBeanServer server, ObjectName name, String attr) {
        try {
            Object value = server.getAttribute(name, attr);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.NaN;
        } catch (Exception e) {
            log.debug("Failed to read JMX attribute {} from {}", attr, name, e);
            return Double.NaN;
        }
    }
}
