package org.grouphq.groupservice.config;

import lombok.RequiredArgsConstructor;
import org.grouphq.groupservice.group.demo.GroupDemoLoader;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the GroupDemoLoader bean.
 * This bean is only created if the property group.loader.enabled is set to true.
 * This property is set to false by default, and meant to be enabled in production.
 */
@RequiredArgsConstructor
@Configuration
public class GroupDemoLoaderConfig {

    private final GroupService groupService;

    @Bean
    @ConditionalOnProperty(name = "group.loader.enabled", havingValue = "true")
    public GroupDemoLoader groupDemoLoader() {
        return new GroupDemoLoader(groupService);
    }
}
