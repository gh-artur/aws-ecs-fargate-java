# aws-ecs-fargate-java

Projeto de estudo para praticar o deploy de aplicações Java na AWS usando ECS Fargate.

## Ferramentas praticadas

### Linguagem e runtime
- **Java 17** — linguagem principal das aplicações e da infraestrutura CDK

### Aplicação
- **Spring Boot 3.2** — framework para construção dos serviços REST
- **Spring Data JPA + MariaDB/MySQL** — persistência do CRUD de produtos
- **AWS SDK (SNS)** — publicação de eventos de produto em tópico SNS
- **Spring JMS + Amazon SQS Java Messaging** — consumo de eventos da fila SQS (project02)
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
- **AWS SQS** — fila (com dead-letter queue) que consome o tópico SNS e alimenta o project02

### Observabilidade e operação
- **AWS CloudWatch** — coleta e visualização de logs dos containers
- **AWS Console** — monitoramento e diagnóstico dos recursos provisionados

## Estrutura do projeto

```
aws_project01/   # Serviço Spring Boot REST — CRUD de produtos, publica eventos no SNS (Gradle)
aws_project02/   # Serviço Spring Boot — consome eventos da fila SQS via JMS listener (Gradle)
aws_cdk/         # Infraestrutura AWS CDK (Maven)
```

## Notas de custo (RDS)

> **MySQL 8.0 em vez de 5.7 (divergência do curso):** o curso orienta a usar o **MySQL 5.7**, porém essa versão já saiu do suporte padrão da AWS e a RDS **cobra automaticamente o Extended Support por vCPU-hora**. Em testes de estudo isso foi de longe o maior custo da stack (~$0.10/vCPU-hora). Por isso a `RdsStack` usa **`MysqlEngineVersion.VER_8_0`**, que continua em suporte padrão e elimina essa cobrança. A aplicação Spring/JPA funciona igual no 8.0.

Outros ajustes de custo aplicados na `RdsStack` para ambiente de estudo:
- **Graviton (`db.t4g.micro`)** em vez de `db.t3.micro` — mesma performance, mais barato.
- **Storage `gp3`** em vez do `gp2` padrão.
- **Sem backups automáticos** (`backupRetention` 0, `deleteAutomatedBackups`) — não há snapshots cobrados.
- **`removalPolicy DESTROY` + `deletionProtection false`** — permite `cdk destroy` limpo ao encerrar o estudo.

💡 Para estudo, o maior economizador é **`cdk destroy --all` ao terminar o dia**: RDS, Fargate e ALB cobram por hora ligados, não por uso.
