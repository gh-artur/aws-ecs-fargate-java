# Anotações de estudo

## Git

- Quando uma pasta dentro de um repositório tem seu próprio `.git`, o Git a trata como submodule (modo `160000` na tree) e não inclui o conteúdo no repositório pai.
- Para corrigir: remover o `.git` interno, depois `git rm --cached <pasta>` para tirar do índice sem deletar os arquivos do disco, e por fim `git add <pasta>/` para rastrear o conteúdo normalmente.
- `--cached` no `git rm` remove apenas do índice; sem ele, os arquivos são deletados do disco também.

## LocalStack

- Ferramenta que **emula serviços da AWS localmente** (SNS, SQS, S3, DynamoDB, Lambda, etc.) rodando num container Docker — permite desenvolver e testar sem tocar na nuvem real (sem custo, sem internet, sem poluir ambiente compartilhado).
- Para usar com o cliente AWS, sobrescreve-se o **endpoint** do client para apontar para o LocalStack (`http://localhost:4566`) em vez do endpoint real da AWS.
- Casos de uso comuns no dia a dia: desenvolvimento local, testes automatizados em CI (sem credenciais AWS reais) e onboarding/prototipagem.
- **Ressalva:** o emulador não é 100% idêntico ao serviço real — diferenças de comportamento, permissões (IAM), latência e limites podem aparecer só na AWS de verdade. Fluxo saudável: testar rápido localmente → validar no ambiente real antes de produção.

### Comandos

```bash
# Subir o LocalStack (porta 4566 — endpoint http://localhost:4566)
docker run --rm -d -p 4566:4566 --name localstack localstack/localstack

# Parar
docker stop localstack
```

- Para interagir com os serviços use `awslocal <comando>` (wrapper do LocalStack) ou `aws --endpoint-url=http://localhost:4566 <comando>`.
- No LocalStack o account id é sempre `000000000000` (usado na montagem dos ARNs, ex: `arn:aws:sqs:us-east-1:000000000000:product-events`).

## ECS Task Definition

- A cada deploy o CDK registra uma nova revisão da task definition, mesmo que não haja alterações no conteúdo.
- Ao fazer deploy de uma nova revisão, a AWS mantém os tasks antigos rodando durante a transição. Esse mecanismo é o **rolling update**, orquestrado pelo **ECS deployment controller**:
  1. ECS sobe novos tasks com a nova revisão
  2. O target group registra os novos tasks e reporta ao ECS se passaram no health check
  3. Somente após os novos tasks estarem saudáveis, o ECS drena e encerra os tasks antigos
  4. O target group desregistra os tasks antigos
- O **target group** tem papel passivo: apenas informa o resultado do health check. Quem decide o ritmo e a ordem do processo é o ECS.
- O target group sempre existe quando há um ALB — ele é a ponte entre o ALB e os tasks. O que é opcional é o **health check configurado nele**:
  - **Com health check** (como neste projeto) — ECS aguarda a aplicação responder 200 em `/actuator/health` antes de avançar
  - **Sem health check configurado** — target group ainda existe, mas o ECS avança assim que o task entra em RUNNING, sem validar se a aplicação está pronta
  - **Sem ALB** (tasks standalone) — não há target group; o ECS verifica apenas o estado RUNNING do container
- O comportamento é controlado por `minimumHealthyPercent` e `maximumPercent`. O padrão do CDK com `desiredCount(2)` é 100%/200%, ou seja, sobe 2 novos tasks antes de derrubar os 2 antigos — garantindo zero downtime.

## Connection Pool (HikariCP)

- O Spring Boot usa o **HikariCP** como pool de conexões padrão, com tamanho máximo de **10 conexões por instância** da aplicação.
- Com `desiredCount(2)` no ECS, são 2 tasks rodando, totalizando **20 conexões** abertas no RDS (10 × 2) — confirmado no console do RDS.
- Importante dimensionar `max_connections` do RDS levando em conta o número de tasks × tamanho do pool, especialmente ao escalar (auto-scaling vai até 4 tasks = 40 conexões).

## AWS Security Groups

- Security Groups controlam o tráfego de rede nos recursos AWS, definindo quais portas ficam abertas para receber requisições e de quais origens.
- No ECS Fargate com ALB, existem dois security groups distintos: um para o ALB (aceita tráfego externo) e um para os tasks Fargate (aceita tráfego vindo do ALB).
- O CDK cria e associa esses security groups automaticamente ao usar `ApplicationLoadBalancedFargateService`.

## SNS → SQS (fan-out)

- Padrão de mensageria assíncrona para **desacoplar** produtor e consumidor: o `project01` publica eventos no tópico SNS sem saber quem consome, e cada consumidor (ex: `project02`) lê de sua própria fila SQS.
  ```
  ProductService (project01) → SNS (product-events) → SQS (fila) → project02 (service02)
  ```
- **Fan-out:** um mesmo tópico SNS pode ter várias filas SQS assinadas, e cada uma recebe uma cópia da mensagem — permite adicionar novos consumidores sem mexer no produtor.
- **Assinatura no CDK:** a fila é assinada no tópico com `SqsSubscription`. O CDK gera automaticamente a policy que autoriza o SNS a enviar mensagens para a fila. (Atenção: a `SnsStack` expõe um `SnsTopic` (wrapper de event target); para assinar é preciso o `Topic` por baixo via `.getTopic().addSubscription(...)`.)
- **Raw message delivery:** sem ele, a fila recebe o envelope completo do SNS (JSON com metadados, e o payload dentro do campo `Message`). Com raw delivery ativado, a fila recebe só o conteúdo publicado — facilita a desserialização no consumidor.
- **Consumo:** o consumidor faz *polling* na fila (AWS SDK SQS ou Spring Cloud AWS), processa a mensagem e a deleta. Mensagem não deletada volta a ficar visível após o *visibility timeout* (garante reprocessamento em caso de falha).
- **Configuração via env vars:** o `project02` recebe URL/nome da fila e região por variáveis de ambiente, injetadas pela `Service02Stack` no CDK — mesmo esquema usado para injetar o ARN do SNS no `Service01`.
