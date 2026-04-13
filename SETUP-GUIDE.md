# Guia Completo: CI/CD com Jenkins + Observabilidade

Passo a passo para configurar GitHub, Jenkins e executar tudo.

---

## O que é Jenkins?

**Jenkins** é uma ferramenta de automação open source usada para implementar **CI/CD** (Integração Contínua / Entrega Contínua).

Na prática, ele observa o repositório Git e, a cada mudança de código, executa automaticamente um conjunto de tarefas chamado **pipeline**:

```
Código enviado → Jenkins detecta → compila → testa → gera imagem Docker → faz deploy
```

Sem Jenkins, esse processo seria manual: você teria que compilar, rodar os testes, buildar e subir o container na mão toda vez que alterasse o código.

**Neste projeto**, o Jenkins roda dentro de um container Docker e executa o `Jenkinsfile` que está na raiz do repositório.

---

## O que é ngrok?

**ngrok** é uma ferramenta que cria um **túnel seguro** entre a internet e a sua máquina local.

O problema sem ngrok:
- Jenkins roda em `http://localhost:8081` — acessível **só na sua máquina**
- O GitHub precisa enviar um aviso (webhook) ao Jenkins quando você fizer um push
- O GitHub está na internet e **não consegue acessar seu localhost**

O ngrok resolve isso criando uma URL pública que redireciona para o seu localhost:

```
GitHub (internet)
    │
    │  POST /github-webhook/
    ▼
https://abc123.ngrok-free.app   ← URL pública criada pelo ngrok
    │
    │  repassa a requisição
    ▼
http://localhost:8081            ← seu Jenkins local
```

---

## Como Jenkins e ngrok se relacionam?

O fluxo completo de automação funciona assim:

```
1. Você faz git push
        │
        ▼
2. GitHub envia webhook para a URL do ngrok
        │
        ▼
3. ngrok repassa a requisição para o Jenkins local (localhost:8081)
        │
        ▼
4. Jenkins inicia o pipeline automaticamente
        │
        ▼
5. Pipeline: compila → testa → build Docker → deploy
        │
        ▼
6. Aplicação atualizada rodando em localhost:8080
```

**Sem ngrok:** você precisa entrar no Jenkins e clicar em "Build Now" manualmente a cada push.  
**Com ngrok:** o push já dispara o pipeline sozinho.

> **Importante:** ngrok é uma solução para **desenvolvimento local**. Em produção, o Jenkins ficaria hospedado em um servidor com IP público e não precisaria do ngrok.

---

## Índice

1. [Pré-Requisitos](#1-pré-requisitos)
2. [Arquitetura](#2-arquitetura)
3. [Configuração do GitHub](#3-configuração-do-github)
4. [Subir a Infraestrutura](#4-subir-a-infraestrutura)
5. [Setup do Jenkins (Wizard)](#5-setup-do-jenkins-wizard)
6. [Configurar Ferramentas no Jenkins](#6-configurar-ferramentas-no-jenkins)
7. [Criar o Pipeline Job](#7-criar-o-pipeline-job)
8. [Executar o Pipeline](#8-executar-o-pipeline)
9. [Acessar os Serviços](#9-acessar-os-serviços)
10. [Webhook Automático (Opcional)](#10-webhook-automático-opcional)
11. [Troubleshooting](#11-troubleshooting)
12. [Resumo dos Comandos](#12-resumo-dos-comandos)

---

## 1. Pré-Requisitos

| Software | Versão Mínima | Verificar |
|----------|---------------|-----------|
| **Docker Desktop** | 4.x | `docker --version` |
| **Docker Compose** | v2.x (incluso no Docker Desktop) | `docker compose version` |
| **Git** | 2.x | `git --version` |
| **Conta GitHub** | — | https://github.com |

> **NÃO precisa** ter Java ou Maven instalados — tudo roda dentro dos containers.

---

## 2. Arquitetura

Tudo roda com **um único `docker compose up`**:

```
┌──────────────────────────────────────────────────────────┐
│                    docker compose up                      │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Jenkins     │  │  Prometheus  │  │   Grafana    │  │
│  │   :8081       │  │   :9090      │  │   :3000      │  │
│  └──────┬───────┘  └──────────────┘  └──────────────┘  │
│         │                  ▲                             │
│         │ deploya          │ coleta métricas             │
│         ▼                  │                             │
│  ┌──────────────┐          │                             │
│  │  Spring Boot  │─────────┘                             │
│  │   App :8080   │                                       │
│  └──────────────┘                                        │
│                                                          │
│         Rede Docker: monitoring                          │
└──────────────────────────────────────────────────────────┘
```

**Pipeline (4 stages):**
```
Checkout → Build & Test → Docker Build → Deploy
```

---

## 3. Configuração do GitHub

### 3.1. Criar Repositório

1. Acesse **https://github.com/new**
2. **Repository name**: `spring-boot-cicd`
3. **NÃO** marque "Add README" nem "Add .gitignore" (já temos)
4. Clique **Create repository**

### 3.2. Push do Código

```bash
cd spring-boot-cicd

git init
git add .
git commit -m "feat: spring boot com CI/CD Jenkins e observabilidade"

# Substitua SEU_USUARIO pelo seu username do GitHub
git remote add origin https://github.com/SEU_USUARIO/spring-boot-cicd.git
git branch -M main
git push -u origin main
```

### 3.3. Autenticação

O GitHub não aceita mais senha via HTTPS. Use **Personal Access Token (PAT)**:

1. GitHub → **Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. **Generate new token (classic)**
3. **Scopes**: marque `repo`
4. **Copie o token** (não será mostrado novamente)
5. Quando o Git pedir senha, **cole o token**

### 3.4. Verificar

Acesse `https://github.com/SEU_USUARIO/spring-boot-cicd` e confirme que o `Jenkinsfile`, `Dockerfile` e `docker-compose.yml` estão na raiz.

---

## 4. Subir a Infraestrutura

Um único comando sobe **tudo** (Jenkins + Prometheus + Grafana):

```bash
docker compose up -d --build
```

> Primeira execução leva **5-10 minutos** (download de imagens e build do Jenkins). Próximas vezes são rápidas.

Verificar:
```bash
docker compose logs jenkins -f
```

Aguarde até ver:
```
Jenkins is fully up and running
```

(`Ctrl+C` para sair dos logs)

---

## 5. Setup do Jenkins (Wizard)

### 5.1. Abrir Jenkins

Navegador: **http://localhost:8081**

### 5.2. Desbloquear

Obtenha a senha inicial:
```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Cole no campo "Administrator password" → **Continue**

### 5.3. Plugins

Clique em **"Install suggested plugins"** → aguarde (3-5 min)

> Os plugins essenciais já foram pré-instalados. Se algum falhar aqui, não se preocupe.

### 5.4. Criar Admin

| Campo | Valor |
|-------|-------|
| Username | `admin` |
| Password | `admin` |
| Full name | `Administrador` |
| E-mail | `admin@localhost` |

**Save and Continue** → Deixe a URL como `http://localhost:8081/` → **Save and Finish** → **Start using Jenkins**

---

## 6. Configurar Ferramentas no Jenkins

### 6.1. Abrir configuração

**Manage Jenkins** → **Tools**

### 6.2. JDK

1. Seção **JDK installations** → **Add JDK**
2. **Desmarque** "Install automatically"

| Campo | Valor |
|-------|-------|
| Name | `JDK-21` |
| JAVA_HOME | `/opt/java/openjdk` |

### 6.3. Maven

1. Seção **Maven installations** → **Add Maven**
2. **Desmarque** "Install automatically"

| Campo | Valor |
|-------|-------|
| Name | `Maven-3.9` |
| MAVEN_HOME | `/opt/maven` |

> **Os nomes devem ser exatos** (case-sensitive) — é o que o `Jenkinsfile` espera.

### 6.4. Salvar

Clique **Save**.

---

## 7. Criar o Pipeline Job

### 7.1. Novo Item

1. Dashboard → **New Item**
2. Nome: `spring-boot-cicd`
3. Tipo: **Pipeline**
4. **OK**

### 7.2. Configurar Pipeline

Role até a seção **"Pipeline"**:

| Campo | Valor |
|-------|-------|
| Definition | **Pipeline script from SCM** |
| SCM | **Git** |
| Repository URL | `https://github.com/SEU_USUARIO/spring-boot-cicd.git` |
| Credentials | `- none -` (público) ou adicione PAT (privado)* |
| Branch | `*/main` |
| Script Path | `Jenkinsfile` |

\* Para repositório **privado**: **Add** → **Jenkins** → Kind: `Username with password` → Username: seu user → Password: seu PAT → **Add** → selecione no dropdown.

### 7.3. Salvar

Clique **Save**.

---

## 8. Executar o Pipeline

### 8.1. Build

Na página do job → **Build Now**

### 8.2. Acompanhar

**Build History** → clique no build `#1` → **Console Output**

Você verá os 4 stages:
```
[Checkout]     → git clone do GitHub
[Build & Test] → mvn clean package (compila, testa, gera JAR)
[Docker Build] → docker build da imagem
[Deploy]       → docker run na rede monitoring
```

### 8.3. Stage View

Na página do job, o **Stage View** mostra:
```
┌──────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────┐
│ Checkout │──▶│ Build & Test │──▶│ Docker Build │──▶│  Deploy  │
│    ✅     │   │     ✅        │   │     ✅        │   │    ✅     │
└──────────┘   └──────────────┘   └──────────────┘   └──────────┘
```

### 8.4. Relatórios

Após o build:
- **Test Result**: 3 testes unitários
- **Coverage Report** (JaCoCo): cobertura de código

---

## 9. Acessar os Serviços

| Serviço | URL | Login |
|---------|-----|-------|
| **API Tasks** | http://localhost:8080/api/tasks | — |
| **Health** | http://localhost:8080/actuator/health | — |
| **Prometheus** | http://localhost:9090 | — |
| **Grafana** | http://localhost:3000 | admin / admin |
| **Jenkins** | http://localhost:8081 | admin / admin |

### Testar a API

```bash
# Criar
curl -X POST http://localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"title\":\"Aprender Jenkins\",\"completed\":false}"

# Listar
curl http://localhost:8080/api/tasks

# Atualizar
curl -X PUT http://localhost:8080/api/tasks/1 -H "Content-Type: application/json" -d "{\"title\":\"Aprender Jenkins\",\"completed\":true}"

# Deletar
curl -X DELETE http://localhost:8080/api/tasks/1
```

### Grafana Dashboard

1. http://localhost:3000 → login `admin`/`admin` → skip troca de senha
2. Menu lateral → **Dashboards** → **Spring Boot CI/CD**
3. Faça requisições na API para ver os gráficos popularem

### Prometheus

http://localhost:9090 → digite `tasks_created_total` → **Execute** → aba **Graph**

---

## 10. Webhook Automático com ngrok

Para build automático a cada `git push`, o GitHub precisa conseguir chamar o Jenkins. Como o Jenkins roda localmente, usamos o **ngrok** para criar um túnel público.

### 10.1. O que é ngrok

ngrok cria uma URL pública (ex: `https://abc123.ngrok-free.app`) que redireciona para o seu `localhost`. O GitHub envia o webhook para essa URL e o ngrok repassa ao Jenkins.

```
GitHub → https://abc123.ngrok-free.app/github-webhook/ → localhost:8081
```

### 10.2. Instalar e configurar ngrok

1. Crie conta gratuita em **https://ngrok.com**
2. Baixe e instale o ngrok
3. Autentique com seu token:
```bash
ngrok config add-authtoken SEU_TOKEN
```

### 10.3. Iniciar o túnel

**Sempre aponte para a porta 8081** (porta do Jenkins):
```bash
ngrok http 8081
```

Você verá algo como:
```
Forwarding  https://2c06-xxxx.ngrok-free.app -> http://localhost:8081
```

> **Atenção:** No plano gratuito, a URL muda toda vez que o ngrok é reiniciado. Sempre atualize o webhook no GitHub quando isso acontecer.

### 10.4. Configurar webhook no GitHub

**Repositório → Settings → Webhooks → Add webhook**:

| Campo | Valor |
|-------|-------|
| Payload URL | `https://SUA_URL_NGROK/github-webhook/` |
| Content type | `application/json` |
| Secret | (deixe em branco) |
| Events | `Just the push event` |

> **Atenção à URL:** deve terminar exatamente com `/github-webhook/` (com barra no final).

Clique **Add webhook** — o GitHub enviará um ping de teste (ícone verde = sucesso).

### 10.5. Configurar trigger no Jenkins

1. Dashboard → job **spring-boot-cicd** → **Configure**
2. Seção **Build Triggers**
3. Marque **"GitHub hook trigger for GITScm polling"**
4. Clique **Save**

### 10.6. Testar

Faça um push qualquer:
```bash
git commit --allow-empty -m "test: disparar pipeline"
git push
```

No **ngrok**, deve aparecer:
```
POST /github-webhook/   200 OK
```

No **Jenkins**, um novo build inicia automaticamente em segundos.

### 10.7. Atualizar a URL quando o ngrok reiniciar

1. Reinicie o ngrok: `ngrok http 8081`
2. Copie a nova URL
3. GitHub → Settings → Webhooks → edite → atualize a Payload URL → **Update webhook**
4. Clique **Redeliver** para reenviar o último evento sem precisar fazer novo push

---

## 11. Troubleshooting

### Jenkins não acessa Docker

```
permission denied while trying to connect to the Docker daemon socket
```

Edite `docker-compose.yml`, adicione `user: root` no serviço jenkins:
```yaml
jenkins:
  build: ./jenkins
  user: root          # <-- adicionar
```
Depois: `docker compose down && docker compose up -d`

---

### Porta 8080 já em uso

```bash
docker stop spring-boot-app && docker rm spring-boot-app
```
Rode o pipeline novamente.

---

### Maven/JDK não encontrado

Verifique em **Manage Jenkins → Tools** que os nomes são **idênticos**:
- JDK: `JDK-21` → JAVA_HOME: `/opt/java/openjdk`
- Maven: `Maven-3.9` → MAVEN_HOME: `/opt/maven`

E que "Install automatically" está **desmarcado**.

---

### Prometheus target DOWN

```bash
docker network inspect monitoring    # spring-boot-app deve estar listado
docker exec prometheus wget -qO- http://spring-boot-app:8080/actuator/prometheus | head -3
```

---

### Jenkins não clona repo privado

1. Confirme que o PAT tem scope `repo`
2. Teste: `docker exec jenkins git ls-remote https://github.com/SEU_USUARIO/spring-boot-cicd.git`

---

## 12. Resumo dos Comandos

```bash
# ── SUBIR TUDO ──
docker compose up -d --build

# ── SENHA JENKINS ──
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# ── FLUXO DE TRABALHO ──
git add . && git commit -m "feat: alteração" && git push
# No Jenkins: Build Now

# ── TESTAR ──
curl http://localhost:8080/api/tasks

# ── PARAR TUDO ──
docker stop spring-boot-app && docker rm spring-boot-app
docker compose down

# ── RESETAR (apaga dados) ──
docker stop spring-boot-app 2>/dev/null; docker rm spring-boot-app 2>/dev/null
docker compose down -v
```
