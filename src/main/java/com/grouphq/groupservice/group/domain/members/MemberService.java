package com.grouphq.groupservice.group.domain.members;

import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.members.exceptions.GroupIsFullException;
import com.grouphq.groupservice.group.domain.members.exceptions.GroupNotActiveException;
import com.grouphq.groupservice.group.domain.members.exceptions.InternalServerError;
import com.grouphq.groupservice.group.domain.members.exceptions.MemberNotActiveException;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service for performing business logic related to members.
 */
@Service
public class MemberService {

    private final Map<String, RuntimeException> businessExceptions = Map.ofEntries(
        Map.entry("Cannot save member with group because the group is not active",
            new GroupNotActiveException("Cannot save member")),
        Map.entry("Cannot save member with group because the group is full",
            new GroupIsFullException("Cannot save member")),
        Map.entry("Cannot update member because member status is not ACTIVE",
            new MemberNotActiveException("Cannot update member"))
    );

    private final MemberRepository memberRepository;

    private final GroupRepository groupRepository;

    public MemberService(MemberRepository memberRepository, GroupRepository groupRepository) {
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
    }

    public Flux<Member> getActiveMembers(Long groupId) {
        return memberRepository.getActiveMembersByGroup(groupId);
    }

    public Mono<Member> joinGroup(String username, Long groupId) {
        return groupRepository.findById(groupId)
            .flatMap(group -> memberRepository.save(Member.of(username, groupId)))
            .onErrorMap(this::getBusinessException);
    }

    public Mono<Void> removeMember(Long memberId) {
        return memberRepository.removeMemberFromGroup(memberId)
            .onErrorMap(this::getBusinessException);
    }

    private RuntimeException getBusinessException(Throwable throwable) {
        final String message = throwable.getMessage();

        RuntimeException exception = new InternalServerError();

        for (final var entries : businessExceptions.entrySet()) {
            if (message.contains(entries.getKey())) {
                exception = entries.getValue();
                break;
            }
        }

        return exception;
    }
}
