# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Three independent subprojects, each with its own build tool and purpose:

| Directory | Build | Purpose |
|---|---|---|
| `aws_project01/` | Gradle | Spring Boot service — deployed to ECS Fargate |
| `aws_project02/` | Gradle | Spring Boot service — currently a skeleton, no controllers yet |
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
- SNS publishing: `ProductService` publishes a `product-events` event via `ProductPublisher` on create/update/delete. Two profiles wire the SNS client — `SnsConfig` (default, real AWS, reads `aws.sns.topic.product.events.arn`) and `SnsCreate` (profile `local`, points at LocalStack `http://localhost:4566` and creates the topic on startup)

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
cdk deploy Service01
```

The `Rds` stack requires a `databasePassword` CloudFormation parameter at deploy time — see Deployment workflow.

### Stacks (registered in `AwsCdkApp.java`)

```
Vpc → Cluster ─┐
Vpc → Rds  ────┼→ Service01
Sns ───────────┘
```

- **VpcStack**: VPC with 2 AZs, no NAT gateways (cost optimisation); RDS uses the isolated subnets
- **ClusterStack**: ECS cluster `cluster-01` inside the VPC
- **RdsStack**: MySQL 5.7 `DatabaseInstance` (`db.t3.micro`, single-AZ, 10 GB) in isolated subnets
  - Password supplied via the `databasePassword` `CfnParameter` (`noEcho`, min length 8) at deploy time
  - Exports `rds-endpoint` and `rds-password` outputs, which `Service01Stack` imports via `Fn.importValue`
- **SnsStack**: SNS topic `product-events` with an email subscription (`arturfrancisco86@gmail.com`). The topic is passed into `Service01Stack`, which receives publish permission and the topic ARN
- **Service01Stack**: `ApplicationLoadBalancedFargateService` running `aws_project01`
  - 512 CPU / 1024 MiB, desired count 2, port 8080, public ALB and public IPs
  - Injects `SPRING_DATASOURCE_*` env vars from the RDS stack outputs
  - Injects `AWS_REGION` and `AWS_SNS_TOPIC_PRODUCT_EVENTS_ARN` (the real topic ARN, overriding the `product-events` default in `application.properties`), and grants the task role `grantPublish` on the SNS topic
  - Auto-scaling: min 2 / max 4 tasks, target 50% CPU, 60 s cooldowns
  - CloudWatch log group: `Service01`
  - Image pulled from Docker Hub: `afsantos22/siecola_aws_project01:1.0.3`

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
