package org.grouphq.groupservice.config;

import lombok.RequiredArgsConstructor;
import org.grouphq.groupservice.group.demo.CharacterGeneratorService;
import org.grouphq.groupservice.group.demo.GroupDemoLoader;
import org.grouphq.groupservice.group.demo.GroupGeneratorService;
import org.grouphq.groupservice.group.domain.groups.GroupEventService;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.members.MemberEventService;
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

    private final GroupProperties groupProperties;

    private final GroupGeneratorService groupGeneratorService;

    private final CharacterGeneratorService characterGeneratorService;

    private final GroupService groupService;

    private final GroupEventService groupEventService;

    private final MemberEventService memberEventService;

    @Bean
    @ConditionalOnProperty(name = "group.loader.enabled", havingValue = "true")
    public GroupDemoLoader groupDemoLoader() {
        return new GroupDemoLoader(groupProperties, groupGeneratorService, characterGeneratorService,
            groupService, groupEventService, memberEventService);
    }
}
