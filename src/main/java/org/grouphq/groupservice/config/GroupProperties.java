package org.grouphq.groupservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the group-service application.
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "group")
public class GroupProperties {

    private Loader loader = new Loader();

    /**
     * Configuration properties for the group loader.
     */
    @Setter
    @Getter
    public static class Loader {
        private boolean enabled;
        private GroupServiceJobs groupServiceJobs = new GroupServiceJobs();

        /**
         * Configuration properties for the group loader jobs.
         */
        @Setter
        @Getter
        public static class GroupServiceJobs {
            private LoadGroups loadGroups = new LoadGroups();
            private ExpireGroups expireGroups = new ExpireGroups();
            private LoadMembers loadMembers = new LoadMembers();

            /**
             * Configuration properties for the "load groups" job.
             */
            @Setter
            @Getter
            public static class LoadGroups {
                private int initialDelay;
                private int fixedDelay;
                private int initialGroupCount;
                private int fixedGroupAdditionCount;
            }

            /**
             * Configuration properties for the "expire groups" job.
             */
            @Setter
            @Getter
            public static class ExpireGroups {
                private int initialDelay;
                private int fixedDelay;
                private int groupLifetime;
            }

            /**
             * Configuration properties for the "load members" job.
             */
            @Setter
            @Getter
            public static class LoadMembers {
                private int initialDelay;
                private int fixedDelay;
                private int memberJoinMaxDelay;
            }
        }
    }
}
