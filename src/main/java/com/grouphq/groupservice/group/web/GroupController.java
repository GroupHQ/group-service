package com.grouphq.groupservice.group.web;

import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberService;
import com.grouphq.groupservice.group.web.objects.GroupJoinRequest;
import com.grouphq.groupservice.group.web.objects.GroupLeaveRequest;
import com.grouphq.groupservice.group.web.objects.egress.PublicMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
            .map(member -> new PublicMember(
                member.username(), member.groupId(), member.memberStatus(),
                member.joinedDate(), member.exitedDate()
            ));
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Member> joinGroup(
        @RequestBody @Valid GroupJoinRequest groupJoinRequest
    ) {
        return memberService.joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId()
        );
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> leaveGroup(
        @RequestBody @Valid GroupLeaveRequest groupLeaveRequest
    ) {
        return memberService.removeMember(groupLeaveRequest.memberId());
    }
}
