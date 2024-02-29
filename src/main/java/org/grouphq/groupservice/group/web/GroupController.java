package org.grouphq.groupservice.group.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.web.objects.egress.PublicOutboxEvent;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A controller containing endpoints for accessing and managing group info.
 */
@RestController
@RequestMapping("api/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public Flux<Group> getAllActiveGroups() {
        log.info("Getting all active groups");
        return groupService.findActiveGroupsWithMembers();
    }

    @GetMapping("/events")
    public Flux<PublicOutboxEvent> getCurrentEvents() {
        log.info("Getting all current relevant events for groups and their members");
        return createEventsFromGroups(groupService.findActiveGroupsWithMembers())
            .doOnError(error -> log.error("Error converting groups to public event", error));
    }

    @GetMapping("/my-member")
    public Mono<Member> getMyMembers() {
        log.info("Getting active members for current user");
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .flatMap(authentication -> {
                final String websocketId = authentication.getName();
                return groupService.findActiveMemberForUser(websocketId);
            });
    }

    private Flux<PublicOutboxEvent> createEventsFromGroups(Flux<Group> groupFlux) {
        return groupFlux.flatMap(group -> Mono.just(new PublicOutboxEvent(group.id(), AggregateType.GROUP,
            EventType.GROUP_CREATED, group, EventStatus.SUCCESSFUL, group.createdDate())));
    }
}
