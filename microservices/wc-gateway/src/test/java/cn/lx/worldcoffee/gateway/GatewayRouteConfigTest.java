package cn.lx.worldcoffee.gateway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteConfigTest {

    @Test
    void applicationYmlShouldKeepRequiredRoutes() throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(input).as("application.yml should exist").isNotNull();
            root = yaml.load(input);
        }

        Map<String, Object> spring = map(root.get("spring"));
        Map<String, Object> cloud = map(spring.get("cloud"));
        Map<String, Object> gateway = map(cloud.get("gateway"));
        List<Map<String, Object>> routes = list(gateway.get("routes"));

        Set<String> routeIds = routes.stream()
                .map(route -> String.valueOf(route.get("id")))
                .collect(Collectors.toSet());

        assertThat(routeIds).contains(
                "wc-admin",
                "wc-shop",
                "wc-community",
                "wc-user",
                "wc-message-notification",
                "wc-message-chat",
                "wc-ai",
                "wc-uploads"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }
}
