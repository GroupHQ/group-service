package org.grouphq.groupservice.group.web.objects;

import static org.assertj.core.api.Assertions.assertThat;

import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

@JsonTest
@Tag("UnitTest")
class MemberJsonTest {

    @Autowired
    private JacksonTester<Member> json;

    @Test
    void serializeToObject() throws IOException {
        final Member member = GroupTestUtility.generateFullMemberDetails();
        final JsonContent<Member> jsonContent = json.write(member);

        assertThat(jsonContent).extractingJsonPathNumberValue("@.id")
            .isEqualTo(member.id());
        assertThat(jsonContent).extractingJsonPathStringValue("@.username")
            .isEqualTo(member.username());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.groupId")
            .isEqualTo(member.groupId());
        assertThat(jsonContent).extractingJsonPathStringValue("@.memberStatus")
            .isEqualTo(member.memberStatus().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.joinedDate")
            .isEqualTo(member.joinedDate().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.exitedDate")
            .isNullOrEmpty();
        assertThat(jsonContent).extractingJsonPathStringValue("@.createdDate")
            .isEqualTo(member.createdDate().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.lastModifiedDate")
            .isEqualTo(member.lastModifiedDate().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.createdBy")
            .isEqualTo(member.createdBy());
        assertThat(jsonContent).extractingJsonPathStringValue("@.lastModifiedBy")
            .isEqualTo(member.lastModifiedBy());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.version")
            .isEqualTo(member.version());
    }
}
