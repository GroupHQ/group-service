Feature: Groups
  GroupHQ allows anyone to join, leave, and view pre-made groups that are managed by the GroupHQ system.

  Scenario: There exists active groups
    Given there are active groups
    When I request groups
    Then I should be given a list of active groups

  Scenario: Always have active groups
    Given any time
    When I request groups
    Then I should be given a list of at least 3 active groups

  Scenario: User joining group
    Given there is an active group
    When I join the group
    Then I should be a member of the group
    And the group's current member size should increase by one

  Scenario: User leaving a group
    Given I am in an active group
    When I leave the group
    Then I should no longer be an active member of that group
    And the group's current member size should decrease by one
