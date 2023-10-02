# group-service
Group Service provides functionality for managing and joining groups.
<hr>

- [Group Service Architecture](#Group-Service-Architecture)
  - [Component Diagram](#Component-Diagram)

## Group Service Architecture
The following container diagram shows Group Services's place in the GroupHQ Software System. 
Shown in the diagram, Group Service communicates with three downstream services (Group Database, an Event Broker, and Config Service), while being called by an upstream service (Group Sync).
<br>

![structurizr-1-GroupHQ_Demo_Containers Alpha 0 1 1 0](https://github.com/GroupHQ/group-service/assets/88041024/a297f6b0-6033-44ec-b2ec-e4690df759e1)

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
