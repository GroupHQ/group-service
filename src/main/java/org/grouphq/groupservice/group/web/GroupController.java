package org.grouphq.groupservice.group.web;

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
@RequestMapping("groups")
public class GroupController {

    private final GroupService groupService;

    private final MemberService memberService;

    public GroupController(GroupService groupService, MemberService memberService) {
        this.groupService = groupService;
        this.memberService = memberService;
    }

    @GetMapping
    public Flux<Group> getAllGroups() {
        return groupService.getGroups();
    }

    @GetMapping("/{groupId}/members")
    public Flux<PublicMember> getActiveGroupMembers(
        @PathVariable Long groupId
    ) {
        return memberService.getActiveMembers(groupId)
            .map(Member::toPublicMember);
    }
}
