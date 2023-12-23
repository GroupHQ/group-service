package org.grouphq.groupservice.group.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberService;
import org.grouphq.groupservice.group.web.objects.egress.PublicMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * A controller containing endpoints for accessing and managing group info.
 */
@RestController
@RequestMapping("api/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupController {

    private final GroupService groupService;

    private final MemberService memberService;

    @GetMapping
    public Flux<Group> getAllActiveGroups() {
        log.info("Getting all active groups");
        return groupService.findActiveGroupsWithMembers();
    }

    @Deprecated
    @GetMapping("/{groupId}/members")
    public Flux<PublicMember> getActiveGroupMembers(
        @PathVariable Long groupId
    ) {
        log.info("Getting all active group members for group with id: {}", groupId);
        return memberService.getActiveMembers(groupId)
            .map(Member::toPublicMember);
    }
}
