# TaskFlow

Gestor inteligente de tareas y proyectos para equipos: Java 21 + Spring Boot 3.4, PostgreSQL, Qdrant (base vectorial), embeddings y RAG con Spring AI, automatizaciones con n8n, y frontend en HTML/CSS/JavaScript puro servido por Spring Boot.

## Arquitectura

```
Navegador (HTML/CSS/JS, Fetch API + JWT)
        │
        ▼
Spring Boot :8080 ──► PostgreSQL (users, projects, tasks, comments, notifications)
   │   │
   │   ├──► Spring AI ──► OpenAI u Ollama (embeddings + LLM)
   │   └──► Qdrant :6333 (un vector por tarea; ID del punto = ID de la tarea)
   │
   └──► webhook n8n (tarea asignada) ◄──► n8n :5678 (workflows programados → /api/n8n/**)
```

Paquetes del backend (`src/main/java/com/taskflow`): `entity`, `repository`, `service`, `controller`, `dto`, `config` (seguridad JWT, propiedades), `exception` (manejo global), `ai` (embeddings, Qdrant, sincronización y RAG).

## Puesta en marcha

Requisitos: Docker y Docker Compose.

```bash
cp .env.example .env        # en Windows: copy .env.example .env
# Edita .env: contraseñas, JWT_SECRET (≥32 caracteres), N8N_API_KEY, N8N_ENCRYPTION_KEY, QDRANT_API_KEY y OPENAI_API_KEY
docker compose up --build
```

| Servicio | URL |
|---|---|
| TaskFlow (frontend + API) | http://localhost:8080 |
| n8n (editor) | http://localhost:5678 (la primera vez pide crear la cuenta de propietario) |
| Qdrant (dashboard) | http://localhost:6333/dashboard |

El **primer usuario registrado** pasa a ser ADMIN. Los volúmenes `postgres_data`, `qdrant_data` y `n8n_data` conservan los datos.

Si un puerto ya está ocupado, cámbialo en `.env` (`APP_PORT`, `N8N_PORT`, `POSTGRES_PORT`, `QDRANT_PORT`).

## Configuración de la IA (API keys)

Las claves se leen solo de variables de entorno; no hay ninguna en el código.

**OpenAI (por defecto)**
```
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
AI_CHAT_MODEL=gpt-4o-mini
AI_EMBEDDING_MODEL=text-embedding-3-small
```
`OPENAI_BASE_URL` admite cualquier API compatible con OpenAI.

**Ollama (local, sin API key)**
```
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://host.docker.internal:11434
AI_CHAT_MODEL=llama3.1
AI_EMBEDDING_MODEL=nomic-embed-text
```

Después ejecuta `docker compose up -d` para aplicar los cambios. Si falta la clave, la aplicación funciona igual, pero la búsqueda y el RAG responden con HTTP 503 (`AI_NOT_CONFIGURED`) y un mensaje claro. Nunca se simulan resultados. Las tareas creadas sin IA quedan como "pendientes de indexar" y se indexan automáticamente cada 5 minutos cuando la IA está disponible. Si cambias de modelo de embeddings (otra dimensión), un ADMIN debe pulsar **Reindexar vectores** en la página Usuarios (`POST /api/admin/ai/reindex`).

## Embeddings, Qdrant y RAG

- Al crear o editar una tarea (también al cambiar su estado), después del commit se genera el embedding de *título + descripción* y se hace un upsert en Qdrant con `id = taskId` y un payload (`projectId`, `status`, `priority`, `dueDate`…). Al eliminar una tarea o un proyecto se borran sus vectores. Si aparece un vector huérfano durante una búsqueda, se elimina.
- **Búsqueda semántica**: embedding de la consulta → búsqueda coseno en Qdrant filtrada por los proyectos a los que el usuario tiene acceso → datos completos desde PostgreSQL.
- **RAG** (`POST /api/ai/ask`): pregunta → embedding → top-K tareas de Qdrant → datos completos y métricas reales de PostgreSQL (atrasadas, abiertas por urgencia, progreso) → contexto → el LLM responde solo con ese contexto, citando `#ID`. Si no hay información suficiente, lo indica.
- **Tareas similares** (`GET /api/tasks/{id}/similar`): usa el endpoint *recommend* de Qdrant con el vector ya almacenado.

## Automatizaciones n8n

Los workflows están en `n8n/workflows/`. Se importan y activan automáticamente al arrancar el contenedor (sus IDs son fijos, así que no se duplican). También pueden importarse a mano desde el editor de n8n.

| Workflow | Disparador | Flujo |
|---|---|---|
| 01 Recordatorio 1 día antes | Cada hora | `GET /api/n8n/tasks/due-tomorrow` → Split Out → `POST /api/n8n/notifications` |
| 02 Tareas atrasadas | Cada hora | `GET /api/n8n/tasks/overdue` → … |
| 03 Resumen diario | Cron `0 8 * * *` | `GET /api/n8n/summaries/daily` → … |
| 04 Tarea asignada | Webhook `POST /webhook/taskflow-task-assigned` (lo llama TaskFlow) | → `POST /api/n8n/notifications` |

Los workflows 01 a 03 también tienen un disparador manual para probarlos desde el editor.

**Sin duplicados**: cada notificación lleva una `dedupKey` (p. ej. `DUE_REMINDER:{taskId}:{dueDate}:{userId}`), protegida por una restricción UNIQUE en PostgreSQL. Además, los endpoints de n8n excluyen los elementos que ya se notificaron. Si n8n no está disponible cuando se asigna una tarea, el backend crea la notificación directamente con la misma clave.

**Seguridad**: n8n se autentica con la cabecera `X-API-Key` (`N8N_API_KEY`). Las variables `TASKFLOW_BASE_URL` y `TASKFLOW_N8N_API_KEY` se inyectan en el contenedor de n8n.

## Endpoints REST

Autenticación: `Authorization: Bearer <JWT>`, salvo `/api/auth/**` (pública) y `/api/n8n/**` (`X-API-Key`).

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/register` | Registro `{fullName, email, password}` → `{token, user}` |
| POST | `/api/auth/login` | Login `{email, password}` → `{token, user}` |
| GET/PUT | `/api/users/me` | Ver / actualizar perfil (nombre, cambio de contraseña) |
| GET | `/api/users` | Directorio de usuarios activos |
| GET | `/api/admin/users` | (ADMIN) Listar usuarios |
| PUT | `/api/admin/users/{id}` | (ADMIN) Cambiar `role` / `active` / `fullName` |
| POST | `/api/admin/ai/reindex` | (ADMIN) Recrear la colección de Qdrant y reindexar |
| GET/POST | `/api/projects` | Listar proyectos accesibles / crear `{name, description, memberIds}` |
| GET/PUT/DELETE | `/api/projects/{id}` | Ver / editar / eliminar (propietario o ADMIN) |
| GET | `/api/tasks?projectId&status&priority&assigneeId&q&overdue` | Listar con filtros |
| POST | `/api/tasks` | Crear `{title, description, status, priority, dueDate, projectId, assigneeId}` |
| GET/PUT/DELETE | `/api/tasks/{id}` | Ver / editar / eliminar |
| PATCH | `/api/tasks/{id}/status` | Cambiar estado `{status}` |
| GET/POST | `/api/tasks/{id}/comments` | Listar / añadir comentario `{content}` |
| DELETE | `/api/comments/{id}` | Eliminar comentario (autor o ADMIN) |
| GET | `/api/tasks/{id}/similar?limit` | Tareas semánticamente similares |
| GET | `/api/dashboard` | Estadísticas |
| GET | `/api/notifications?unreadOnly` | Notificaciones del usuario |
| GET | `/api/notifications/unread-count` | Contador de no leídas |
| PATCH | `/api/notifications/{id}/read`, `/api/notifications/read-all` | Marcar como leídas |
| DELETE | `/api/notifications/{id}` | Eliminar |
| GET | `/api/ai/status` | Estado de la IA y de Qdrant, tareas indexadas |
| GET | `/api/ai/search?q&projectId&limit` | Búsqueda semántica |
| POST | `/api/ai/ask` | RAG `{question, projectId?}` → `{answer, sources, ...}` |
| GET | `/api/n8n/tasks/due-tomorrow` | (n8n) Tareas que vencen mañana |
| GET | `/api/n8n/tasks/overdue` | (n8n) Tareas atrasadas |
| GET | `/api/n8n/summaries/daily` | (n8n) Resumen por usuario |
| POST | `/api/n8n/notifications` | (n8n) Crear notificación idempotente (201 creada / 200 duplicada) |

Los errores tienen siempre este formato: `{timestamp, status, code, message, path, fieldErrors}`.

## Permisos

- **ADMIN**: acceso a todo, gestión de usuarios y reindexación.
- **Propietario de proyecto**: edita y elimina el proyecto y gestiona sus miembros.
- **Miembro**: ve el proyecto y crea o edita sus tareas. Solo el creador de la tarea, el propietario del proyecto o un ADMIN pueden eliminarla.
- Los responsables de una tarea deben ser propietario o miembro del proyecto.
- La búsqueda y el RAG se filtran en Qdrant por los proyectos accesibles para el usuario.

## Desarrollo local sin Docker

```bash
docker compose up -d postgres qdrant
mvn spring-boot:run   # usa las variables de entorno o los valores por defecto de application.yml
```
