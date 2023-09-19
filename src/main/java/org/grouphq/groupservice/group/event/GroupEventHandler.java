package org.grouphq.groupservice.group.event;

import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.members.MemberService;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.event.daos.RequestEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A class for handling group events.
 * <p>This class is responsible for handling group events. It is a Spring Cloud Function
 * that is integrated with Spring Cloud Stream. It is a consumer of group events.</p>
 */
@Configuration
public class GroupEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GroupEventHandler.class);

    private final GroupService groupService;

    private final MemberService memberService;

    private final Validator validator;

    public GroupEventHandler(GroupService groupService,
                             MemberService memberService,
                             Validator validator) {
        this.groupService = groupService;
        this.memberService = memberService;
        this.validator = validator;
    }

    /**
     * A consumer for handling group join requests.
     *
     * <p>Recognized by Spring Cloud Function, integrated to Spring Cloud Stream.
     * Context: Spring Cloud Stream subscribes to a Spring Cloud Function ONCE in a reactive
     * context. This is different from an imperative context where SCS calls the consumer for
     * every message.</p>
     *
     * <p>It's important to know this because if the initial subscription ends (e.g. due to
     * an error), SCS will not resubscribe to the consumer. Any subsequent messages will then fail
     * to be handled. Therefore, it is important to handle errors within the consumer's flatMap
     * operation,so that any errors due to processing an item will be handled relative to that
     * item's subscription,and not the subscription of the consumer
     * (which would cause the consumer's subscription to end).</p>
     *
     * @return A consumer for handling group join requests.
     */
    @Bean
    public Consumer<Flux<GroupJoinRequestEvent>> handleGroupJoinRequests() {
        return flux -> flux
            .flatMap(groupJoinRequestEvent -> {
                LOG.info("Group join request received: {}", groupJoinRequestEvent);
                return validateRequest(groupJoinRequestEvent)
                    .then(memberService.joinGroup(groupJoinRequestEvent))
                    .doOnError(throwable ->
                        LOG.error("Error joining group: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        memberService.joinGroupFailed(groupJoinRequestEvent, throwable))
                    .doOnError(throwable -> LOG.error(
                        "Error processing group join failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                LOG.error("""
                    Error received out-of-stream for GroupJoinRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    @Bean
    public Consumer<Flux<GroupLeaveRequestEvent>> handleGroupLeaveRequests() {
        return flux -> flux
            .flatMap(groupLeaveRequestEvent -> {
                LOG.info("Group leave request received: {}", groupLeaveRequestEvent);
                return validateRequest(groupLeaveRequestEvent)
                    .then(memberService.removeMember(groupLeaveRequestEvent))
                    .doOnError(throwable ->
                        LOG.error("Error leaving group: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        memberService.removeMemberFailed(groupLeaveRequestEvent, throwable))
                    .doOnError(throwable -> LOG.error(
                        "Error processing group leave failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                LOG.error("""
                    Error received out-of-stream for GroupLeaveRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    @Bean
    public Consumer<Flux<GroupCreateRequestEvent>> handleGroupCreateRequests() {
        return flux -> flux
            .flatMap(groupCreateRequestEvent -> {
                LOG.info("Group create request received: {}", groupCreateRequestEvent);

                return validateRequest(groupCreateRequestEvent)
                    .then(groupService.createGroup(groupCreateRequestEvent))
                    .doOnError(throwable ->
                        LOG.error("Error creating group: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        groupService.createGroupFailed(groupCreateRequestEvent, throwable))
                    .doOnError(throwable -> LOG.error(
                        "Error processing group creation failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                LOG.error("""
                    Error received out-of-stream for GroupCreateRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    @Bean
    public Consumer<Flux<GroupStatusRequestEvent>> handleGroupStatusRequests() {
        return flux -> flux
            .flatMap(groupStatusRequestEvent -> {
                LOG.info("Group status request received: {}", groupStatusRequestEvent);

                return validateRequest(groupStatusRequestEvent)
                    .then(groupService.updateGroupStatus(groupStatusRequestEvent))
                    .doOnError(throwable ->
                        LOG.error("Error updating group status: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        groupService.updateGroupStatusFailed(groupStatusRequestEvent, throwable))
                    .doOnError(throwable -> LOG.error(
                        "Error processing group status update failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                LOG.error("""
                    Error received out-of-stream for GroupStatusRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    private <T extends RequestEvent> Mono<RequestEvent> validateRequest(T requestEvent) {
        final Set<ConstraintViolation<T>> violations = validator.validate(requestEvent);

        final List<String> violationInfo = violations.stream()
            .map(ConstraintViolation::getMessage)
            .toList();

        return violations.isEmpty() ? Mono.just(requestEvent) :
            Mono.error(new IllegalArgumentException(violationInfo.toString()));
    }
}
