# group-service
Group Service provides functionality for managing and joining dynamically generated groups.
<hr>

## Contents
- [Synopsis](#Synopsis)
  - [Group Generation](#Group-Generation)
  - [Group Expiration](#Group-Expiration)
  - [Group Membership](#Group-Membership)
  - [Expected Usage](#Expected-Usage)
  - [Requests](#Requests)
- [Setting up the Development Environment](#Setting-up-the-Development-Environment)
  - [Prerequisites](#Prerequisites)
- [Developing](#Developing)
  - [Checks To Pass](#Checks-To-Pass)
    - [Code Style](#Code-Style)
    - [Code Quality](#Code-Quality)
    - [Dependency Vulnerability Check](#Dependency-Vulnerability-Check)
    - [Unit Tests](#Unit-Tests)
    - [Integration Tests](#Integration-Tests)
    - [Manifest Validation](#Manifest-Validation)
  - [User Automated Tests & Regression Testing](#User-Automated-Tests--Regression-Testing)
- [Group Service Architecture](#Group-Service-Architecture)
  - [Component Diagram](#Component-Diagram)
  - [Example of Event Flow: User Joining a Group](#Example-of-Event-Flow-User-Joining-a-Group)
    - [1. Group Sync Publishes a Group Join Request to the Event Broker](#1-Group-Sync-Publishes-a-Group-Join-Request-to-the-Event-Broker)
    - [2. Group Service Consumes this Request From the Broker And Publishes an OutboxEvent to the Broker](#2-Group-Service-Consumes-this-Request-From-the-Broker-And-Publishes-an-OutboxEvent-to-the-Broker)
    - [3. Group Sync Consumes this OutboxEvent and Forwards the Event Info to all Connected Users](#3-Group-Sync-Consumes-this-OutboxEvent-and-Forwards-the-Event-Info-to-all-Connected-Users)

## Synopsis
Group service handles all business logic related to the processing of groups and their members.

Note: in the context of this README, a user or client refers to either an end-user another service application.

### Group Generation
Groups are periodically generated using configurable parameters that can be tuned based on the deployment 
environment. If enabled, groups are generated using the OpenAI API and the Java Faker library. Specifically, a random 
character is selected from a popular show, movie or video game. Using this character, a request is then sent to OpenAI to create a group 
posting (a title and a description) taking the persona of the character in the context of their “universe” 
(i.e. the show, movie, or video game the character is from). If the request fails and retries have been exhausted, or if the OpenAI 
integration is disabled, then a fallback group is created using lorem ipsum text.

### Group Expiration
A group automatically expires after it reaches a certain age. Any group that is expired becomes disbanded, and any 
disbanded group automatically disbands the members currently in that group. 

### Group Membership
The user-membership relationship is a one-to-many relationship. Every time a user joins a group, a member is created 
for them. If a user leaves and joins the same group, they would have two members associated with that group, with only 
the second having an active status. This strategy allows: 
- the creation of a simple and clear member history
- keeping track of the members a group has and what users those members belong to

Currently, users are only allowed to have one active member at any time. 

### Endpoints for Retrieving Data
Group Service provides a few REST endpoints for retrieving data:

- `/groups`: Returns a list of currently active groups and their members
- `/groups/events`: Similar to `/groups`, except each group is wrapped in an event object
- `/groups/my-member`: Returns the user's currently active member, if any, based on their authentication credentials

### Making Requests
Group Service accepts requests posted to an event broker on the queues it monitors. The following types of requests are currently accepted:
- Create Group
- Update Group Status
- Join Group
- Leave Group

After processing a request, group service publishes an event object to another queue that can be consumed by other services.
[Group Sync]([url](https://github.com/GroupHQ/group-sync)) is one of these services.

### Putting it All Together
Clients of Group Service can expect to be able to do the following:
- Retrieve active groups and their members (either as standalone objects or wrapped in an event object)
- Retrieve their currently active member
- Post requests for processing by publishing them to an event queue that group service consumes. Processed requests are published to a separate queue for any services interested.

## Setting up the Development Environment

### Prerequisites
- Recommended Java 21. Minimum Java 17. [Download here](https://www.oracle.com/java/technologies/downloads/)
- An IDE that supports Java. [IntelliJ](https://www.jetbrains.com/idea/) is recommended. A free community edition is 
available.
- Git. [Download here](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git). For Windows users, install
Git using [Git For Windows](https://gitforwindows.org/)
- Recommended to install a Bash terminal. Linux and Mac users should have this by default. Windows users can install
Git Bash using [Git For Windows](https://gitforwindows.org/).
- Kubeconform (for validating Kubernetes manifests). [Download here](https://github.com/yannh/kubeconform?tab=readme-ov-file#Installation)

Group Service uses both a PostgreSQL database and a RabbitMQ event broker. While you can download, configure, and run
these services manually, it's recommended to use the provided docker-compose file to run these services, 
located in the GroupHQ Deployment repository. See the [GroupHQ Deployment README](https://github.com/GroupHQ/groupHQ-deployment?tab=readme-ov-file#local-environment-using-docker)
for more information.

Once you have your backing services ready, you should be able to run the Group Service application, either
through your IDE or through the docker-compose file in the GroupHQ Deployment repository.

Alternatively, you can run the Group Service application in a Kubernetes environment. See the 
[GroupHQ Deployment README](https://github.com/GroupHQ/groupHQ-deployment?tab=readme-ov-file#local-environment-using-kubernetes)
for instructions on setting that up.

### Runtime Configuration
To enable dynamic group loading, add the following environment variable to your runtime configuration:

`GROUP_LOADER_ENABLED=true`

To enable OpenAI integration, add your OpenAI API key to your runtime configuration as an environment variable:

`OPEN_AI_API_KEY=<your-api-key>`

And enable the integration by setting the following environment variable:

`OPENAI_ENABLED=true`

## Developing
When developing new features, it's recommended to follow a test-driven development approach using the classicist style
of testing. What this means is:
1. Prioritize writing tests first over code. At the very minimum, write out test cases for the changes you want to 
make. This will help you think through the design of your changes and catch defects early on.
2. Avoid excessive mocking. Mocks are useful for isolating your code from external dependencies, but they can also
make your tests brittle and hard to maintain. If you find yourself mocking a lot, it may be a sign that the class
under test is more suitable for integration testing. If you are mocking out an external service, consider using
a Testcontainer for simulating the service as a fake type of [test double](https://martinfowler.com/bliki/TestDouble.html).
You can find some examples of this in the `GroupServiceIntegrationTest` class*.
3. Write tests, implement, and then just as important, refactor and review. It's easy to get caught up in 
messy code that passes tests. Take the time to review your code after implementing a feature.


*When testing the event-messaging system with an event broker, use the Spring Cloud Stream Test Binder.
All messaging with the event broker takes place through Spring Cloud Stream. Instead of testing the dependency itself,
rely on the Spring Cloud Stream Test Binder to simulate the broker. This allows you to test the messaging system
without having to worry about the sending and receiving of messages. See the `GroupEventGroupIntegrationTest` class 
for an example of this. See the [Spring Cloud Stream Test Binder documentation](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/spring_integration_test_binder.html)
for more information on the test binder.

### Checks To Pass
When pushing a commit to any branch, the following checks are run:
- **Code Style:** All code must pass the checkstyle rules defined in the `config/checkstyle/checkstyle.xml` file.
- **Code Quality:** All code must pass the PMD rules defined in the `config/pmd/*` files.
- **Dependency Vulnerability Check:** Dependencies with vulnerabilities that meet the specified vulnerability cutoff 
must be reviewed.
- **Unit Tests:** All unit tests must pass.
- **Integration Tests:** All integration tests must pass.
- **Manifest Validation:** Any changes to Kubernetes manifests under the `k8s` folder must pass validation.

For code style, quality, and dependency vulnerability checks, you can view a detailed report on these checks once
they have completed by navigating to the build/reports directory. 
You can run these checks with the following commands (These commands are compatible with the bash terminal. If you are 
using a different terminal, you may need to modify the commands to work in that terminal environment):

#### Code Style
```bash
./gradlew checkstyleMain --stacktrace
./gradlew checkstyleTest --stacktrace
```

#### Code Quality
```bash
./gradlew pmdMain --stacktrace
./gradlew pmdTest --stacktrace
```

#### Dependency Vulnerability Check
```bash
./gradlew dependencyCheckAnalyze --stacktrace -PnvdApiKey="YOUR_NVD_API_KEY"
```
See [here](https://nvd.nist.gov/developers/request-an-api-key) for details on requesting an NVD API key.

#### Unit Tests
```bash
./gradlew testUnit
```

#### Integration Tests
```bash
./gradlew testIntegration
```

#### Manifest Validation
```bash
kustomize build k8s/base | kubeconform -strict -summary -output json
kustomize build k8s/overlays/observability | kubeconform -strict -summary -output json
```

It's recommended to add these commands to your IDE as separate run configurations for quick access.
Make sure you do not commit these run configurations to the version control system, especially
any that may contain sensitive info (such as the NVD API key for the dependency vulnerability check).

### User Automated Tests & Regression Testing
For any features that introduce a new user-facing feature, it's recommended to add automated tests for them to 
the [GroupHQ Continuous Testing Test Suite](https://github.com/GroupHQ/grouphq-continuous-testing-test-suite). 
For more information on how to write these tests, see the associated READEME of that repository.

When any pull request is opened, a request is sent to the [GroupHQ Continuous Testing Proxy Server](https://github.com/GroupHQ/grouphq-continuous-testing-proxy-server)
to run the test suite against the pull request. The length of a test run is expected to vary over time, 
but it's currently pretty quick at just under 10 minutes for around ~200 tests. 
Once the test run is complete, the results will be posted to the pull request, including a link to the test results to 
review if needed.

To learn how to add a new test to the test suite, or run the test suite locally, see the
[GroupHQ Continuous Testing Test Suite README](https://github.com/GroupHQ/grouphq-continuous-testing-test-suite). 
It's recommended to validate at least tests relevant to your feature, as well as any new tests added. 

## Group Service Architecture
The following container diagram shows Group Services' place in the GroupHQ Software System. 
Shown in the diagram, Group Service communicates with two downstream services: a database and an event broker, 
while being called by an upstream service, Group Sync.

![GroupHQ_Demo_Containers_noObservability](https://github.com/GroupHQ/group-service/assets/88041024/3b9b98f1-06a3-49d1-b04f-2ea5943b218a)

### Component Diagram
![structurizr-1-GroupHQ_GroupService_Components](https://github.com/GroupHQ/group-service/assets/88041024/7806d241-b131-44ed-8f12-8975ae426d7c)
<hr>

### Example of Event Flow: User Joining a Group
#### 1. Group Sync Publishes a Group Join Request to the Event Broker
![structurizr-1-GroupHQ_GroupSyncJoinGroupPart1_Dynamic](https://github.com/GroupHQ/group-service/assets/88041024/443ed17a-8237-4ada-a96f-0885ca0bcee8)
#### 2. Group Service Consumes this Request From the Broker And Publishes an OutboxEvent to the Broker
![structurizr-1-GroupHQ_GroupSyncJoinGroupPart2_Dynamic](https://github.com/GroupHQ/group-service/assets/88041024/37f16cf4-f9b0-4c13-a875-0d0ba8df31b3)
#### 3. Group Sync Consumes this OutboxEvent and Forwards the Event Info to all Connected Users
![structurizr-1-GroupHQ_GroupSyncJoinGroupPart3_Dynamic](https://github.com/GroupHQ/group-service/assets/88041024/0fd2812a-60a0-43e3-9d6a-4ac9977e6e1b)
