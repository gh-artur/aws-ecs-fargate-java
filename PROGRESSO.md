# Progresso do projeto

Diário das fases concluídas no curso, com os marcos alcançados em cada etapa.

## Fase 1 — Infraestrutura base na AWS

- VPC com 2 AZs, sem NAT gateways (otimização de custo)
- Cluster ECS (`cluster-01`)
- Serviço Fargate (`aws_project01`) atrás de um Application Load Balancer público
- Health check em `/actuator/health`, auto-scaling de 2 a 4 tasks
- **Marco:** aplicação Spring Boot rodando no ECS Fargate, acessível pela URL do ALB (porta 8080)

## Fase 2 — Banco de dados (RDS)

- Stack RDS com instância MySQL em subnet isolada
- Senha passada via `CfnParameter` no momento do deploy (`noEcho` para mascarar)
- Endpoint e senha exportados como outputs para o Service01 consumir
- Tasks Fargate recebem a conexão via variáveis de ambiente (`SPRING_DATASOURCE_*`)
- **Marco:** aplicação conectada ao banco gerenciado na AWS

## Fase 3 — CRUD de produtos

- Entidade `Product` (validações com Bean Validation, `BigDecimal` para preço, Lombok)
- Camada de repositório (`ProductRepository` com Spring Data)
- Camada de serviço (`ProductService`) separando regras da camada HTTP
- `ProductController` com endpoints REST: listar, buscar por id, buscar por código, criar, atualizar e deletar
- **Marco:** API CRUD completa, seguindo boas práticas de camadas

## Fase 4 — Testes locais

- Container MariaDB rodando localmente via Docker, com as mesmas credenciais do `application.properties`
- Coleção Postman criada para todos os endpoints de produto
- **Marco:** CRUD validado localmente antes de subir para a nuvem

## Fase 5 — Deploy e validação na AWS

- Imagem Docker publicada no Docker Hub e referenciada no `Service01Stack`
- Deploy via CDK e testes dos endpoints pela coleção Postman, trocando a URL para a do ALB
- Verificação no console do RDS: 20 conexões abertas (10 por task × 2 tasks — pool padrão do HikariCP)
- **Marco:** aplicação completa rodando na AWS, requisições funcionando de ponta a ponta

## Fase 6 — Eventos de produto via SNS

Lado da aplicação (`aws_project01`):
- Enum `EventType` (`PRODUCT_CREATED`, `PRODUCT_UPDATE`, `PRODUCT_DELETE`)
- Modelos `ProductEvent` e `Envelope` (payload serializado em JSON com Jackson)
- `ProductPublisher` publica no tópico `product-events` via AWS SDK SNS
- `ProductService` dispara o evento no create/update/delete
- Dois perfis de configuração do client SNS:
  - `SnsConfig` (perfil default / AWS real) — lê o ARN de `aws.sns.topic.product.events.arn`
  - `SnsCreate` (perfil `local`) — aponta para o LocalStack (`http://localhost:4566`) e cria o tópico no startup

Lado da infraestrutura (`aws_cdk`):
- `SnsStack` cria o tópico `product-events` + assinatura de e-mail
- `Service01Stack` recebe o tópico, injeta `AWS_SNS_TOPIC_PRODUCT_EVENTS_ARN` (ARN real, sobrescrevendo o default do `application.properties`) e ganha `grantPublish` na task role
- `AwsCdkApp` passa o tópico para o Service01 e declara a dependência entre as stacks

- **Marco:** evento publicado no SNS a cada POST de produto — validado na AWS com recebimento do e-mail de notificação

## Fase 7 — Consumo de eventos via SQS (project02)

Lado da aplicação (`aws_project02`, porta 9090):
- `ProductEventConsumer` com `@JmsListener` na fila `product-events` (Amazon SQS Java Messaging Library sobre JMS)
- Desempacota o envelope do SNS: `SnsMessage` → `Envelope` → `ProductEvent`, e loga o evento
- Modelos `SnsMessage`, `Envelope`, `ProductEvent` e enum `EventType` alinhado ao `project01`
- Dois perfis de configuração do listener:
  - `JmsConfig` (perfil default / AWS real) — registra o bean `jmsListenerContainerFactory`
  - `JmsConfigLocal` (perfil `local`) — mesmo factory apontando para o LocalStack (`http://localhost:4566`)

Lado da infraestrutura (`aws_cdk`):
- `Service02Stack` cria a fila `product-events` + DLQ `product-events-dlq` (`maxReceiveCount` 3), assina a fila no tópico SNS (`SqsSubscription`), roda o `aws_project02` no Fargate e dá `grantConsumeMessages` à task role
- `AwsCdkApp` registra a `Service02Stack` (depende de Cluster e Sns)
- `deregistration_delay` reduzido para 30 s no Service01 e Service02 (deploy/rollback mais rápidos)

Apoio para testes locais:
- `SqsCreateSubscribe` (perfil `local` do `project01`) cria a fila e a assina no tópico no LocalStack

Percalços resolvidos no caminho:
- Faltava `@Bean` na factory JMS e o override de endpoint para LocalStack no `project02`
- Divergência de enum entre produtor e consumidor (`PRODUCT_UPDATE` vs `PRODUCT_UPDATED`) — alinhado nos dois lados

- **Marco:** fluxo ponta a ponta na AWS — insert/update/delete no `project01` geram eventos consumidos pelo `project02` (confirmado nos logs do CloudWatch)
