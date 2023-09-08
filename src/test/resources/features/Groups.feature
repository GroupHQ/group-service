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

  Scenario: Users joining groups
    Given there are active groups
    When I join a group
    Then I should be a member of that group

  Scenario:
