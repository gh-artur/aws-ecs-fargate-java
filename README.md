# aws-ecs-fargate-java

Projeto de estudo para praticar o deploy de aplicações Java na AWS usando ECS Fargate.

## Ferramentas praticadas

### Linguagem e runtime
- **Java 17** — linguagem principal das aplicações e da infraestrutura CDK

### Aplicação
- **Spring Boot 3.2** — framework para construção dos serviços REST
- **Spring Data JPA + MariaDB/MySQL** — persistência do CRUD de produtos
- **AWS SDK (SNS)** — publicação de eventos de produto em tópico SNS
- **Gradle** — build e empacotamento das aplicações

### Containers
- **Docker** — containerização das aplicações com imagens para `linux/amd64`
- **Docker Hub** — registry para armazenamento e distribuição das imagens

### Infraestrutura AWS
- **AWS CDK (Java)** — infraestrutura como código para provisionar todos os recursos AWS
- **AWS ECS Fargate** — execução dos containers sem gerenciar servidores
- **AWS ALB (Application Load Balancer)** — balanceamento de carga e exposição pública dos serviços
- **AWS VPC** — rede isolada com subnets para os recursos
- **AWS RDS (MySQL)** — banco de dados gerenciado em subnet isolada
- **AWS SNS** — tópico de mensageria para eventos de produto

### Observabilidade e operação
- **AWS CloudWatch** — coleta e visualização de logs dos containers
- **AWS Console** — monitoramento e diagnóstico dos recursos provisionados

## Estrutura do projeto

```
aws_project01/   # Serviço Spring Boot (Gradle)
aws_project02/   # Serviço Spring Boot (Gradle) — em desenvolvimento
aws_cdk/         # Infraestrutura AWS CDK (Maven)
```
