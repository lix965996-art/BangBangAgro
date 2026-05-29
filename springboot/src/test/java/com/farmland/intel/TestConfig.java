package com.farmland.intel;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * Test configuration to provide a mock ServerEndpointExporter
 * since the WebSocket container is not available in test environment
 */
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Primary
    public ServerEndpointExporter serverEndpointExporter() {
        // Return a mock exporter that doesn't try to access the unavailable container
        return new MockServerEndpointExporter();
    }
    
    private static class MockServerEndpointExporter extends ServerEndpointExporter {
        @Override
        public void afterPropertiesSet() {
            // Skip initialization in test environment - don't call parent
        }

        @Override
        public void afterSingletonsInstantiated() {
            // Skip endpoint registration in test environment
        }
    }
}
