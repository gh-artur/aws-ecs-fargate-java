# Anotações de estudo

## Git

- Quando uma pasta dentro de um repositório tem seu próprio `.git`, o Git a trata como submodule (modo `160000` na tree) e não inclui o conteúdo no repositório pai.
- Para corrigir: remover o `.git` interno, depois `git rm --cached <pasta>` para tirar do índice sem deletar os arquivos do disco, e por fim `git add <pasta>/` para rastrear o conteúdo normalmente.
- `--cached` no `git rm` remove apenas do índice; sem ele, os arquivos são deletados do disco também.

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
