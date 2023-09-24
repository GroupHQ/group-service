package org.grouphq.groupservice.group.web.objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

@JsonTest
@Tag("UnitTest")
class GroupJsonTest {
    
    @Autowired
    private JacksonTester<Group> json;
    
    @Test
    void serializeToObject() throws IOException {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final JsonContent<Group> jsonContent = json.write(group);
        
        assertThat(jsonContent).extractingJsonPathNumberValue("@.id")
            .isEqualTo(group.id());
        assertThat(jsonContent).extractingJsonPathStringValue("@.title")
            .isEqualTo(group.title());
        assertThat(jsonContent).extractingJsonPathStringValue("@.description")
            .isEqualTo(group.description());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.maxGroupSize")
            .isEqualTo(group.maxGroupSize());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.currentGroupSize")
            .isEqualTo(group.currentGroupSize());
        assertThat(jsonContent).extractingJsonPathStringValue("@.status")
            .isEqualTo(group.status().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.lastActive")
            .isEqualTo(group.lastActive().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.createdDate")
            .isEqualTo(group.createdDate().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.lastModifiedDate")
            .isEqualTo(group.lastModifiedDate().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.createdBy")
            .isEqualTo(group.createdBy());
        assertThat(jsonContent).extractingJsonPathStringValue("@.lastModifiedBy")
            .isEqualTo(group.lastModifiedBy());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.version")
            .isEqualTo(group.version());
    }
}
