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

## Próxima fase

- [ ] Adicionar SNS (notificações)
