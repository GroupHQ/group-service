package com.grouphq.groupservice.group.web;

import com.grouphq.groupservice.group.domain.exceptions.GroupIsFullException;
import com.grouphq.groupservice.group.domain.exceptions.GroupNotActiveException;
import com.grouphq.groupservice.group.domain.exceptions.InternalServerError;
import com.grouphq.groupservice.group.domain.exceptions.MemberNotActiveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * A controller advice to handle exceptions that may be
 * encountered by the application's controllers.
 */
@ControllerAdvice
public class GroupServiceExceptionHandler {

    @ExceptionHandler(GroupIsFullException.class)
    public ResponseEntity<String> handleGroupIsFullException(
        GroupIsFullException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(GroupNotActiveException.class)
    public ResponseEntity<String> handleGroupNotActiveException(
        GroupNotActiveException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.GONE);
    }

    @ExceptionHandler(MemberNotActiveException.class)
    public ResponseEntity<String> handleMemberNotActiveException(
        MemberNotActiveException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InternalServerError.class)
    public ResponseEntity<String> handleInternalServerError(
        InternalServerError exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
