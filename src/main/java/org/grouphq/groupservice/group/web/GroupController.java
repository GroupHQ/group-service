package org.grouphq.groupservice.group.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.members.Member;
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
}
