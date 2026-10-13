package com.dsa.flawless.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("g1-measure")   // 측정할 때만 켜기
public class PlainHttpConnectorConfig {
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> plainConnector() {
        return factory -> {
            Connector c = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            c.setPort(8081);
            factory.addAdditionalConnectors(c);
        };
    }
}