package org.grouphq.groupservice.group.domain.outbox;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.web.objects.egress.PublicMember;

/**
 * Marker interface for objects sent in events.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Group.class, name = "Group"),
    @JsonSubTypes.Type(value = Member.class, name = "Member"),
    @JsonSubTypes.Type(value = PublicMember.class, name = "PublicMember"),
    @JsonSubTypes.Type(value = ErrorData.class, name = "ErrorData")
})
public interface EventDataModel {
}
