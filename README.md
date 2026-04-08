# Spring Boot CI/CD

Aplicação Spring Boot simples com **CI/CD (Jenkins)** e **observabilidade (Prometheus + Grafana)**.

> **Guia completo de configuração:** Veja o [SETUP-GUIDE.md](SETUP-GUIDE.md) para instruções passo a passo.

## Stack

- Java 21 + Spring Boot 3.3
- Spring Actuator + Micrometer (métricas)
- Prometheus + Grafana (monitoramento)
- Jenkins (CI/CD pipeline)
- Docker / Docker Compose

## Estrutura

```
spring-boot-cicd/
├── src/main/java/com/example/cicd/
│   ├── Application.java
│   ├── controller/TaskController.java
│   ├── dto/TaskRequest.java, TaskResponse.java
│   └── service/TaskService.java
├── src/test/java/.../TaskControllerTest.java
├── monitoring/
│   ├── prometheus/prometheus.yml
│   └── grafana/provisioning/...
├── jenkins/
│   ├── Dockerfile          ← imagem Jenkins com Maven + JDK + Docker CLI
│   └── plugins.txt
├── Dockerfile              ← imagem da app Spring Boot
├── Jenkinsfile             ← pipeline CI/CD (4 stages)
├── docker-compose.yml      ← Jenkins + Prometheus + Grafana (tudo junto)
└── pom.xml
```

## Quick Start

```bash
# 1. Subir tudo (Jenkins + Prometheus + Grafana)
docker compose up -d --build

# 2. Obter senha inicial do Jenkins
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# 3. Acessar
#    Jenkins:    http://localhost:8081
#    Prometheus: http://localhost:9090
#    Grafana:    http://localhost:3000 (admin/admin)
#    App (após pipeline): http://localhost:8080/api/tasks
```

## API Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/tasks` | Listar tasks |
| GET | `/api/tasks/{id}` | Buscar por ID |
| POST | `/api/tasks` | Criar task |
| PUT | `/api/tasks/{id}` | Atualizar task |
| DELETE | `/api/tasks/{id}` | Deletar task |

## Monitoramento

| Endpoint | Descrição |
|----------|-----------|
| `/actuator/health` | Health check |
| `/actuator/prometheus` | Métricas Prometheus |
