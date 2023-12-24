package org.grouphq.groupservice.group.domain.members;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * A service for performing business logic related to members.
 *
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Deprecated
public class MemberService {
    private final MemberRepository memberRepository;

    /**
     * Deprecated: use {@link GroupService#findGroupByIdWithActiveMembers(Long)}} instead, which returns the
     * {@link org.grouphq.groupservice.group.domain.groups.Group} with a list of active {@link Member}s.
     */
    @Deprecated
    public Flux<Member> getActiveMembers(Long groupId) {
        log.info("Getting all active members for group with id: {}", groupId);
        return memberRepository.getActiveMembersByGroup(groupId);
    }
}
