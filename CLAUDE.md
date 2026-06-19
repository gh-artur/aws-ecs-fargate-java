# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Three independent subprojects, each with its own build tool and purpose:

| Directory | Build | Purpose |
|---|---|---|
| `aws_project01/` | Gradle | Spring Boot REST service (product CRUD) — publishes events to SNS; deployed to ECS Fargate |
| `aws_project02/` | Gradle | Spring Boot service — consumes product events from an SQS queue (JMS listener); deployed to ECS Fargate |
| `aws_cdk/` | Maven | AWS CDK infrastructure that provisions the AWS resources |

## Spring Boot Services (aws_project01, aws_project02)

Both use Gradle with the Palantir Docker plugin. Run commands from within each project directory.

```bash
# Build JAR
./gradlew build

# Run locally
./gradlew bootRun

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "br.com.ghartur.aws_project01.AwsProject01ApplicationTests"

# Build Docker image (linux/amd64, even on ARM)
./gradlew docker
```

- Java 17, Spring Boot 3.2.x (snapshot channel)
- `spring-boot-starter-web` + `spring-boot-starter-actuator` + `validation` + `data-jpa` (MariaDB driver) + AWS SDK SNS + Lombok
- Port: **8080**; health-check endpoint: `/actuator/health`
- Docker image naming: the `docker` block in `build.gradle` sets only `name "${project.group}/${project.name}"` (no version), so `./gradlew docker` produces **only the `latest` tag** — group is the Docker Hub username (`afsantos22`), name comes from `settings.gradle`. The version-specific tag (e.g. `1.0.3`) is **not** created automatically; you must tag it by hand before pushing (see Deployment workflow below)
- Dockerfile expects a pre-built exploded JAR in `build/dependency/` (produced by the `unpack` Gradle task, which the `docker` task depends on automatically)
- SNS publishing (aws_project01): `ProductService` publishes a `product-events` event via `ProductPublisher` on create/update/delete. Two profiles wire the SNS client — `SnsConfig` (default, real AWS, reads `aws.sns.topic.product.events.arn`) and `SnsCreate` (profile `local`, points at LocalStack `http://localhost:4566` and creates the topic on startup). The profile `local` also has `SqsCreateSubscribe`, which creates the SQS queue and subscribes it to the topic on LocalStack
- SQS consuming (aws_project02): runs on port **9090**; `ProductEventConsumer` is a `@JmsListener` on the `product-events` queue (Amazon SQS Java Messaging Library over JMS). It unwraps the SNS envelope (`SnsMessage` → `Envelope` → `ProductEvent`), logs the event, and **persists a `ProductEventLog` to DynamoDB** via `ProductEventLogRepository`. Two profiles wire the JMS listener factory — `JmsConfig` (default, real AWS) and `JmsConfigLocal` (profile `local`, LocalStack endpoint). The producer (project01) and consumer (project02) must keep their `EventType` enum values identical, since the value travels as a string in the payload
- DynamoDB persistence (aws_project02): `DynamoDBConfig` wires the DynamoDB client/mapper (`spring-data-dynamodb`) reading `aws.region`. The `ProductEventLog` entity maps to the `product-events` table with a composite key — partition key `pk` (the product `code`) and sort key `sk` (`<eventType>_<timestamp>`) — plus a 10-minute `ttl`. `ProductEventLogRepository` (`@EnableScan` `CrudRepository`) exposes `findAllByPk` / `findAllByPkAndSkStartsWith`. The table name must match the DynamoDB table created by `DdbStack`

## AWS CDK Infrastructure (aws_cdk)

Maven-based CDK project; deploy commands use the CDK CLI.

```bash
# Synthesize CloudFormation templates
cd aws_cdk
mvn -e -q compile exec:java   # same as `cdk synth` via cdk.json

# Deploy all stacks
cdk deploy --all

# Deploy a single stack
cdk deploy Vpc
cdk deploy Cluster
cdk deploy Rds
cdk deploy Sns
cdk deploy Ddb
cdk deploy Service01
cdk deploy Service02
```

The `Rds` stack requires a `databasePassword` CloudFormation parameter at deploy time — see Deployment workflow.

### Stacks (registered in `AwsCdkApp.java`)

```
Vpc → Cluster ─┬→ Service01
Vpc → Rds  ─────┘
Sns ───────────┬→ Service01 (publish)
               └→ Service02 (SQS subscription + consume)
Cluster ────────→ Service02
Ddb ────────────→ Service02 (read/write event log)
```

- **VpcStack**: VPC with 2 AZs, no NAT gateways (cost optimisation); RDS uses the isolated subnets
- **ClusterStack**: ECS cluster `cluster-01` inside the VPC
- **RdsStack**: MySQL 8.0 `DatabaseInstance` (`db.t4g.micro` Graviton, single-AZ, 10 GB gp2) in isolated subnets
  - Password supplied via the `databasePassword` `CfnParameter` (`noEcho`, min length 8) at deploy time
  - Exports `rds-endpoint` and `rds-password` outputs, which `Service01Stack` imports via `Fn.importValue`
  - Cost-tuned for study: MySQL **8.0** (not 5.7, which incurs Extended Support charges), Graviton instance, gp2 10 GB, no automated backups (`backupRetention` 0), and `removalPolicy DESTROY` for clean teardown. Note: gp3 is not used because MySQL requires a 20 GB minimum for it
- **SnsStack**: SNS topic `product-events` with an email subscription (`arturfrancisco86@gmail.com`). The topic is passed into both `Service01Stack` (publisher) and `Service02Stack` (which subscribes its SQS queue to it)
- **DdbStack**: DynamoDB table `product-events` (provisioned 1 RCU / 1 WCU, partition key `pk`, sort key `sk`, `ttl` attribute, `removalPolicy DESTROY`). The table is passed into `Service02Stack`, which grants `grantReadWriteData` to the task role
- **Service01Stack**: `ApplicationLoadBalancedFargateService` running `aws_project01`
  - 512 CPU / 1024 MiB, desired count 2, port 8080, public ALB and public IPs
  - Injects `SPRING_DATASOURCE_*` env vars from the RDS stack outputs
  - Injects `AWS_REGION` and `AWS_SNS_TOPIC_PRODUCT_EVENTS_ARN` (the real topic ARN, overriding the `product-events` default in `application.properties`), and grants the task role `grantPublish` on the SNS topic
  - Auto-scaling: min 2 / max 4 tasks, target 50% CPU, 60 s cooldowns
  - Target group `deregistration_delay` lowered to 30 s (faster deploys/rollbacks)
  - CloudWatch log group: `Service01`
  - Image pulled from Docker Hub: `afsantos22/siecola_aws_project01:1.0.3`
- **Service02Stack**: `ApplicationLoadBalancedFargateService` running `aws_project02`
  - Creates the SQS queue `product-events` plus a dead-letter queue `product-events-dlq` (`maxReceiveCount` 3), and subscribes the queue to the SNS topic (`SqsSubscription`)
  - 512 CPU / 1024 MiB, desired count 2, port 9090, public ALB and public IPs
  - Injects `AWS_REGION` and `AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME`, grants the task role `grantConsumeMessages` on the queue, and grants `grantReadWriteData` on the DynamoDB `product-events` table (passed in from `DdbStack`)
  - Auto-scaling and `deregistration_delay` (30 s) configured like Service01
  - CloudWatch log group: `Service02`
  - Image pulled from Docker Hub: `afsantos22/siecola_aws_project02:1.0.4`

### Deployment workflow

1. Bump `version` in `aws_project01/build.gradle`.
2. Build and push a new Docker image from `aws_project01/` to Docker Hub. Because `./gradlew docker` only produces the `latest` tag, the version tag must be applied by hand:
   ```bash
   ./gradlew docker
   docker tag afsantos22/siecola_aws_project01:latest afsantos22/siecola_aws_project01:<version>
   docker push afsantos22/siecola_aws_project01:<version>
   ```
3. Update the image tag in `Service01Stack.java` if the version changed.
4. Deploy. The RDS password parameter is required whenever the `Rds` stack is part of the deploy:
   ```bash
   # all stacks (parameter must be qualified with the stack name under --all)
   cdk deploy --all --parameters Rds:databasePassword=<password> --require-approval never

   # just the service (no RDS parameter needed)
   cdk deploy Service01
   ```

`aws_project02` follows the same image workflow under the name `afsantos22/siecola_aws_project02`, with the tag referenced in `Service02Stack.java`.
