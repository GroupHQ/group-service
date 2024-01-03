package org.grouphq.groupservice.group.domain.groups;

import com.github.javafaker.Faker;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.exceptions.GroupDoesNotExistException;
import org.grouphq.groupservice.group.domain.exceptions.MemberNotFoundException;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service for performing business logic related to groups and their members.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GroupService {

    private final GroupRepository groupRepository;

    private final MemberRepository memberRepository;

    private final ExceptionMapper exceptionMapper;
    
    private static final String CANNOT_FETCH_GROUP_MESSAGE = "Cannot fetch group with id: ";

    public Mono<Group> findGroupById(Long id) {
        log.info("Getting group with id: {}", id);
        return groupRepository.findById(id)
            .switchIfEmpty(
                Mono.error(new GroupDoesNotExistException(CANNOT_FETCH_GROUP_MESSAGE + id)));
    }

    public Mono<Group> findGroupByIdWithActiveMembers(Long id) {
        return groupRepository.findById(id)
            .switchIfEmpty(
                Mono.error(new GroupDoesNotExistException(CANNOT_FETCH_GROUP_MESSAGE + id)))
            .flatMap(group -> memberRepository.getActiveMembersByGroup(group.id())
                .map(Member::convertMembersToPublicMembers)
                .collectList()
                .map(group::withMembers))
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Mono<Group> findGroupByIdWithAllMembers(Long id) {
        return groupRepository.findById(id)
            .switchIfEmpty(
                Mono.error(new GroupDoesNotExistException(CANNOT_FETCH_GROUP_MESSAGE + id)))
            .flatMap(group -> memberRepository.getMembersByGroup(group.id())
                .map(Member::convertMembersToPublicMembers)
                .collectList()
                .map(group::withMembers))
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Flux<Group> findActiveGroups() {
        log.info("Getting all groups by active status");
        return groupRepository.findGroupsByStatus(GroupStatus.ACTIVE);
    }

    public Flux<Group> findActiveGroupsWithMembers() {
        return groupRepository.findGroupsByStatus(GroupStatus.ACTIVE)
            .flatMap(group ->
                memberRepository.getActiveMembersByGroup(group.id())
                    .map(Member::convertMembersToPublicMembers)
                    .collectList()
                    .map(group::withMembers));
    }

    public Flux<Group> findActiveGroupsCreatedBefore(Instant cutoffDate) {
        return groupRepository.findActiveGroupsCreatedBefore(cutoffDate, GroupStatus.ACTIVE);
    }

    public Mono<Group> createGroup(String title, String description, int maxGroupSize) {
        final Group group = Group.of(title, description, maxGroupSize, GroupStatus.ACTIVE);
        log.info("Creating group: {}", group);
        return groupRepository.save(group);
    }

    public Mono<Group> updateStatus(Long groupId, GroupStatus status) {
        log.info("Updating group status for group with id: {} to status: {}", groupId, status);
        return groupRepository.findById(groupId)
            .switchIfEmpty(
                Mono.error(new GroupDoesNotExistException(CANNOT_FETCH_GROUP_MESSAGE + groupId)))
            .then(
                groupRepository.updateStatusByGroupId(groupId, status)
                    .flatMap(group -> memberRepository.autoDisbandActiveMembers(groupId)
                        .then(Mono.just(group)))
            )
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Mono<Group> disbandGroup(Long groupId, GroupStatus disbandmentStatus) {
        if (disbandmentStatus != GroupStatus.DISBANDED && disbandmentStatus != GroupStatus.AUTO_DISBANDED) {
            throw new IllegalArgumentException("New status must be DISBANDED or AUTO_DISBANDED");
        }

        log.info("Disbanding group with id: {}", groupId);
        return groupRepository.findById(groupId)
            .switchIfEmpty(
                Mono.error(new GroupDoesNotExistException(CANNOT_FETCH_GROUP_MESSAGE + groupId)))
            .flatMap(group -> memberRepository.autoDisbandActiveMembers(groupId).collectList())
            .flatMap(members -> groupRepository.updateStatusByGroupId(groupId, disbandmentStatus))
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Mono<Member> addMember(Long groupId, String username, String websocketId) {
        log.info("Adding member to group with id: {}", groupId);
        return groupRepository.findById(groupId)
            .switchIfEmpty(Mono.error(new GroupDoesNotExistException(CANNOT_FETCH_GROUP_MESSAGE + groupId)))
            .flatMap(group ->
                memberRepository.save(Member.of(websocketId, username, groupId)))
            .flatMap(member ->
                groupRepository.updatedLastMemberActivityByGroupId(groupId, member.createdDate())
                .thenReturn(member))
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Mono<Member> removeMember(Long groupId, Long memberId, String websocketId) {
        final UUID websocketUuid = UUID.fromString(websocketId);
        log.info("Removing member from group with member id: {}", memberId);
        return memberRepository.findMemberByIdAndWebsocketId(memberId, websocketUuid)
            .switchIfEmpty(Mono.error(new MemberNotFoundException("Cannot remove member")))
            .flatMap(group ->
                memberRepository.removeMemberFromGroup(memberId, websocketUuid, MemberStatus.LEFT))
            .flatMap(member ->
                groupRepository.updatedLastMemberActivityByGroupId(groupId, member.exitedDate())
                    .thenReturn(member))
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Mono<Member> findActiveMemberForUser(String websocketId) {
        return memberRepository.findMemberByWebsocketIdAndMemberStatus(
            UUID.fromString(websocketId), MemberStatus.ACTIVE
        );
    }

    public Group generateGroup() {
        final Faker faker = new Faker();

        final int maxCapacity = faker.number().numberBetween(2, 64);

        return Group.of(faker.lorem().sentence(), faker.lorem().sentence(20),
            maxCapacity, GroupStatus.ACTIVE);
    }
}
