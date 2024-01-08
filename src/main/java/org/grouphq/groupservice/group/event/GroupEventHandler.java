package org.grouphq.groupservice.group.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.groups.GroupEventService;
import org.grouphq.groupservice.group.domain.members.MemberEventService;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.RequestEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A class for handling group events.
 * <p>This class is responsible for handling group events. It is a Spring Cloud Function
 * that is integrated with Spring Cloud Stream. It is a consumer of group events.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
public class GroupEventHandler {

    private final GroupEventService groupEventService;

    private final MemberEventService memberEventService;

    private final Validator validator;


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
    public Consumer<Flux<GroupJoinRequestEvent>> groupJoinRequests() {
        return flux -> flux
            .flatMap(groupJoinRequestEvent -> {
                log.info("Group join request received: {}", groupJoinRequestEvent);
                return validateRequest(groupJoinRequestEvent)
                    .then(memberEventService.joinGroup(groupJoinRequestEvent))
                    .doOnError(throwable ->
                        log.info("Cannot join group: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        memberEventService.joinGroupFailed(groupJoinRequestEvent, throwable))
                    .doOnError(throwable -> log.error(
                        "Error processing group join failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                log.error("""
                    Error received out-of-stream for GroupJoinRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    @Bean
    public Consumer<Flux<GroupLeaveRequestEvent>> groupLeaveRequests() {
        return flux -> flux
            .flatMap(groupLeaveRequestEvent -> {
                log.info("Group leave request received: {}", groupLeaveRequestEvent);
                return validateRequest(groupLeaveRequestEvent)
                    .then(memberEventService.removeMember(groupLeaveRequestEvent))
                    .doOnError(throwable ->
                        log.info("Cannot leave group: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        memberEventService.removeMemberFailed(groupLeaveRequestEvent, throwable))
                    .doOnError(throwable -> log.error(
                        "Error processing group leave failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                log.error("""
                    Error received out-of-stream for GroupLeaveRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    @Bean
    public Consumer<Flux<GroupCreateRequestEvent>> groupCreateRequests() {
        return flux -> flux
            .flatMap(groupCreateRequestEvent -> {
                log.info("Group create request received: {}", groupCreateRequestEvent);

                return validateRequest(groupCreateRequestEvent)
                    .then(groupEventService.createGroup(groupCreateRequestEvent))
                    .doOnError(throwable ->
                        log.info("Cannot create group: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        groupEventService.createGroupFailed(groupCreateRequestEvent, throwable)
                            .then(Mono.empty()))
                    .doOnError(throwable -> log.error(
                        "Error processing group creation failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                log.error("""
                    Error received out-of-stream for GroupCreateRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    @Bean
    public Consumer<Flux<GroupStatusRequestEvent>> groupStatusRequests() {
        return flux -> flux
            .flatMap(groupStatusRequestEvent -> {
                log.info("Group status request received: {}", groupStatusRequestEvent);

                return validateRequest(groupStatusRequestEvent)
                    .then(groupEventService.updateGroupStatus(groupStatusRequestEvent))
                    .doOnError(throwable ->
                        log.info("Cannot update group status: {}", throwable.getMessage()))
                    .onErrorResume(throwable ->
                        groupEventService.updateGroupStatusFailed(groupStatusRequestEvent, throwable))
                    .doOnError(throwable -> log.error(
                        "Error processing group status update failure: {}", throwable.getMessage()))
                    .onErrorResume(throwable -> Mono.empty());
            })
            .doOnError(throwable ->
                log.error("""
                    Error received out-of-stream for GroupStatusRequestEvent consumer!
                    Attempting to resume stream""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty())
            .subscribe();
    }

    private <T extends RequestEvent> Mono<RequestEvent> validateRequest(T requestEvent) {
        log.info("Validating request: {}", requestEvent);
        final Set<ConstraintViolation<T>> violations = validator.validate(requestEvent);

        final List<String> violationInfo = violations.stream()
            .map(ConstraintViolation::getMessage)
            .toList();

        return violations.isEmpty() ? Mono.just(requestEvent) :
            Mono.error(new IllegalArgumentException(violationInfo.toString()));
    }
}
