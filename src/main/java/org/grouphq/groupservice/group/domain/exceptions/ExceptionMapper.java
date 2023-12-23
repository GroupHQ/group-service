package org.grouphq.groupservice.group.domain.exceptions;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * This class is used to map exceptions
 * from external application sources to our business exceptions.
 */
@Component
public class ExceptionMapper {

    /**
     * These are used to match exceptions from the database to our business exceptions.
     * The key contains part of the exception message from the database, and the value
     * is the corresponding business exception.
     */
    public final Map<String, RuntimeException> businessExceptions = Map.ofEntries(
        Map.entry("Cannot save member with group because the group is not active",
            new GroupNotActiveException("Cannot save member")),
        Map.entry("Cannot save member with group because the group is full",
            new GroupIsFullException("Cannot save member")),
        Map.entry("Cannot update member because member status is not ACTIVE",
            new MemberNotActiveException("Cannot update member")),
        Map.entry("Group has reached its maximum size",
            new GroupSizeException("Cannot join group because this group has reached its maximum size")),
        Map.entry("new row for relation \"groups\" "
                + "violates check constraint \"groups_max_group_size_check\"",
            new GroupSizeException("Cannot create group due to invalid max size value. "
                + "Max size should be at least 2")),
        Map.entry("Cannot save member because the user has an active member in some group",
            new UserAlreadyInGroupException("Cannot save member"))
    );

    public Throwable getBusinessException(Throwable throwable) {
        final String message = throwable.getMessage();
        Throwable exception = throwable;

        if (message != null) {
            for (final var entries : businessExceptions.entrySet()) {
                if (message.contains(entries.getKey())) {
                    exception = entries.getValue();
                    break;
                }
            }
        }

        return exception;
    }


}
