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
- `spring-boot-starter-web` + `spring-boot-starter-actuator`
- Port: **8080**; health-check endpoint: `/actuator/health`
- Docker image naming: `<group>/<rootProject.name>:<version>` — group is the Docker Hub username (`afsantos22`), name comes from `settings.gradle`, version from `build.gradle`
- Dockerfile expects a pre-built exploded JAR in `build/dependency/` (produced by the `unpack` Gradle task, which the `docker` task depends on automatically)

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
cdk deploy Service01
```

### Stack dependency chain

```
VpcStack → ClusterStack → Service01Stack
```

- **VpcStack**: VPC with 2 AZs, no NAT gateways (cost optimisation)
- **ClusterStack**: ECS cluster `cluster-01` inside the VPC
- **Service01Stack**: `ApplicationLoadBalancedFargateService` running `aws_project01`
  - 512 CPU / 1024 MiB, desired count 2, port 8080, public ALB and public IPs
  - Auto-scaling: min 2 / max 4 tasks, target 50% CPU, 60 s cooldowns
  - CloudWatch log group: `Service01`
  - Image pulled from Docker Hub: `afsantos22/siecola_aws_project01:1.0.0`

### Deployment workflow

1. Build and push a new Docker image from `aws_project01/` to Docker Hub:
   ```bash
   ./gradlew docker
   docker push afsantos22/siecola_aws_project01:<version>
   ```
2. Update the image tag in `Service01Stack.java` if the version changed.
3. Run `cdk deploy Service01` (or `--all` for infrastructure changes).
