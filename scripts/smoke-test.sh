#!/usr/bin/env bash
# Prueba de humo end-to-end de la API de TaskFlow.
# Uso: BASE=http://localhost:8080 N8N_API_KEY=... ./scripts/smoke-test.sh
# Requiere: curl y python3 (para leer JSON).
set -u
BASE="${BASE:-http://localhost:8080}"
KEY="${N8N_API_KEY:?Define N8N_API_KEY}"
PASS=0; FAIL=0
RUN=$(date +%s)
PY=python3; $PY -c "" 2>/dev/null || PY=python

json() { $PY -c "import sys,json; d=json.load(sys.stdin); print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }
check() { # check "descripción" esperado obtenido
  if [ "$2" == "$3" ]; then PASS=$((PASS+1)); echo "  OK   $1"; else FAIL=$((FAIL+1)); echo "  FAIL $1 (esperado: $2, obtenido: $3)"; fi
}
req() { # req MÉTODO RUTA TOKEN [BODY] -> imprime "STATUS<TAB>BODY"
  # El cuerpo se envía por stdin (--data-binary @-): pasarlo como argumento (-d) corrompe
  # los caracteres no ASCII (tildes, ñ) en Git Bash de Windows, que convierte argv a ANSI.
  local out; out=$(printf '%s' "${4:-}" | curl -s -o /tmp/tf_body -w "%{http_code}" -X "$1" "$BASE$2" \
    -H "Content-Type: application/json" ${3:+-H "Authorization: Bearer $3"} ${4:+--data-binary @-})
  printf "%s\t%s" "$out" "$(cat /tmp/tf_body)"
}
status() { echo "$1" | cut -f1; }
body() { echo "$1" | cut -f2-; }

TOMORROW=$($PY -c "import datetime;print(datetime.date.today()+datetime.timedelta(days=1))")
PAST=$($PY -c "import datetime;print(datetime.date.today()-datetime.timedelta(days=3))")

echo "== Autenticación"
R=$(req POST /api/auth/register "" "{\"fullName\":\"Ana Admin\",\"email\":\"ana$RUN@test.com\",\"password\":\"secreto123\"}")
check "registro Ana" 201 "$(status "$R")"; ANA=$(body "$R" | json "['token']"); ANA_ID=$(body "$R" | json "['user']['id']")
R=$(req POST /api/auth/register "" "{\"fullName\":\"Beto Dev\",\"email\":\"beto$RUN@test.com\",\"password\":\"secreto123\"}")
BETO=$(body "$R" | json "['token']"); BETO_ID=$(body "$R" | json "['user']['id']")
R=$(req POST /api/auth/register "" "{\"fullName\":\"Carla Externa\",\"email\":\"carla$RUN@test.com\",\"password\":\"secreto123\"}")
CARLA=$(body "$R" | json "['token']")
check "registro duplicado -> 409" 409 "$(status "$(req POST /api/auth/register "" "{\"fullName\":\"X\",\"email\":\"ana$RUN@test.com\",\"password\":\"secreto123\"}")")"
check "registro inválido -> 400" 400 "$(status "$(req POST /api/auth/register "" '{"fullName":"","email":"no-email","password":"1"}')")"
check "login correcto" 200 "$(status "$(req POST /api/auth/login "" "{\"email\":\"beto$RUN@test.com\",\"password\":\"secreto123\"}")")"
check "login incorrecto -> 401" 401 "$(status "$(req POST /api/auth/login "" "{\"email\":\"beto$RUN@test.com\",\"password\":\"mala\"}")")"
check "sin token -> 401" 401 "$(status "$(req GET /api/projects "")")"
check "/api/users/me" 200 "$(status "$(req GET /api/users/me "$ANA")")"

echo "== Proyectos"
R=$(req POST /api/projects "$ANA" "{\"name\":\"Portal web $RUN\",\"description\":\"Rediseño del portal\",\"memberIds\":[$BETO_ID]}")
check "crear proyecto" 201 "$(status "$R")"; P1=$(body "$R" | json "['id']")
check "listar proyectos (Beto es miembro)" 1 "$(body "$(req GET /api/projects "$BETO")" | $PY -c "import sys,json;print(sum(1 for p in json.load(sys.stdin) if p['id']==$P1))")"
check "Carla sin acceso -> 403" 403 "$(status "$(req GET /api/projects/$P1 "$CARLA")")"
check "Beto no puede editar (no propietario) -> 403" 403 "$(status "$(req PUT /api/projects/$P1 "$BETO" '{"name":"hack"}')")"
check "editar proyecto" 200 "$(status "$(req PUT /api/projects/$P1 "$ANA" "{\"name\":\"Portal web $RUN\",\"description\":\"Rediseño completo del portal\",\"memberIds\":[$BETO_ID]}")")"

echo "== Tareas"
R=$(req POST /api/tasks "$ANA" "{\"title\":\"Maquetar pantalla de login\",\"description\":\"Frontend: formulario de login responsive con HTML y CSS\",\"priority\":\"ALTA\",\"dueDate\":\"$TOMORROW\",\"projectId\":$P1,\"assigneeId\":$BETO_ID}")
check "crear tarea asignada a Beto" 201 "$(status "$R")"; T1=$(body "$R" | json "['id']")
R=$(req POST /api/tasks "$ANA" "{\"title\":\"Configurar base de datos\",\"description\":\"Crear esquema PostgreSQL e índices\",\"priority\":\"URGENTE\",\"dueDate\":\"$PAST\",\"projectId\":$P1}")
T2=$(body "$R" | json "['id']"); check "tarea atrasada marcada overdue" True "$(body "$R" | json "['overdue']")"
R=$(req POST /api/tasks "$BETO" "{\"title\":\"Estilos del dashboard\",\"description\":\"Frontend: gráficos y tarjetas del panel con CSS3\",\"priority\":\"MEDIA\",\"projectId\":$P1}")
check "miembro crea tarea" 201 "$(status "$R")"; T3=$(body "$R" | json "['id']")
check "asignar a no miembro -> 400" 400 "$(status "$(req POST /api/tasks "$ANA" "{\"title\":\"x\",\"projectId\":$P1,\"assigneeId\":999999}")")"
check "título vacío -> 400" 400 "$(status "$(req POST /api/tasks "$ANA" "{\"title\":\"\",\"projectId\":$P1}")")"
check "Carla no puede crear en proyecto ajeno -> 403" 403 "$(status "$(req POST /api/tasks "$CARLA" "{\"title\":\"x\",\"projectId\":$P1}")")"
check "filtro por prioridad URGENTE" 1 "$(body "$(req GET "/api/tasks?projectId=$P1&priority=URGENTE" "$ANA")" | json ".__len__()")"
check "filtro atrasadas" 1 "$(body "$(req GET "/api/tasks?projectId=$P1&overdue=true" "$ANA")" | json ".__len__()")"
check "filtro texto 'frontend'" 2 "$(body "$(req GET "/api/tasks?projectId=$P1&q=frontend" "$ANA")" | json ".__len__()")"
check "cambiar estado" EN_PROGRESO "$(body "$(req PATCH /api/tasks/$T3/status "$BETO" '{"status":"EN_PROGRESO"}')" | json "['status']")"
check "estado inválido -> 400" 400 "$(status "$(req PATCH /api/tasks/$T3/status "$BETO" '{"status":"XYZ"}')")"
R=$(req PUT /api/tasks/$T3 "$BETO" "{\"title\":\"Estilos del dashboard\",\"description\":\"Frontend: gráficos y tarjetas del panel con CSS3\",\"priority\":\"MEDIA\",\"status\":\"COMPLETADA\",\"projectId\":$P1,\"assigneeId\":$BETO_ID}")
check "editar tarea (completar)" COMPLETADA "$(body "$R" | json "['status']")"
check "Carla no ve la tarea -> 403" 403 "$(status "$(req GET /api/tasks/$T1 "$CARLA")")"

echo "== Comentarios"
R=$(req POST /api/tasks/$T1/comments "$BETO" '{"content":"Empiezo hoy con el login"}')
check "añadir comentario" 201 "$(status "$R")"; C1=$(body "$R" | json "['id']")
check "listar comentarios" 1 "$(body "$(req GET /api/tasks/$T1/comments "$ANA")" | json ".__len__()")"
check "Carla no borra comentario ajeno -> 403" 403 "$(status "$(req DELETE /api/comments/$C1 "$CARLA")")"
check "autor borra su comentario" 204 "$(status "$(req DELETE /api/comments/$C1 "$BETO")")"

echo "== Dashboard"
R=$(req GET /api/dashboard "$ANA"); check "dashboard" 200 "$(status "$R")"
echo "     $(body "$R" | $PY -c "import sys,json;d=json.load(sys.stdin);print({k:d[k] for k in ['totalTasks','pending','inProgress','completed','overdue','completionRate']})")"

echo "== Integración n8n (API protegida con X-API-Key)"
check "sin API key -> 401" 401 "$(curl -s -o /dev/null -w "%{http_code}" $BASE/api/n8n/tasks/overdue)"
check "API key errónea -> 401" 401 "$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: mala" $BASE/api/n8n/tasks/overdue)"
check "usuario JWT no accede a /api/n8n -> 401" 401 "$(status "$(req GET /api/n8n/tasks/overdue "$ANA")")"
D=$(curl -s -H "X-API-Key: $KEY" $BASE/api/n8n/tasks/due-tomorrow)
check "due-tomorrow incluye la tarea $T1" True "$(echo "$D" | $PY -c "import sys,json;print(any(i['taskId']==$T1 for i in json.load(sys.stdin)['items']))")"
O=$(curl -s -H "X-API-Key: $KEY" $BASE/api/n8n/tasks/overdue)
check "overdue incluye la tarea $T2" True "$(echo "$O" | $PY -c "import sys,json;print(any(i['taskId']==$T2 for i in json.load(sys.stdin)['items']))")"
NB='{"userId":'$ANA_ID',"taskId":'$T2',"type":"OVERDUE","title":"prueba","message":"m","dedupKey":"SMOKE:'$RUN'"}'
check "crear notificación -> 201" 201 "$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "$NB" $BASE/api/n8n/notifications)"
check "misma dedupKey -> 200 (duplicada, no se crea)" 200 "$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "$NB" $BASE/api/n8n/notifications)"

echo "== Notificación de asignación (TaskFlow -> webhook n8n -> TaskFlow)"
sleep 4
R=$(req GET /api/notifications "$BETO")
check "Beto recibió TASK_ASSIGNED de la tarea $T1" True "$(body "$R" | $PY -c "import sys,json;print(any(n['type']=='TASK_ASSIGNED' and n['taskId']==$T1 for n in json.load(sys.stdin)))")"
echo "     origen: $(body "$R" | $PY -c "import sys,json;print([n['source'] for n in json.load(sys.stdin) if n['type']=='TASK_ASSIGNED'])")"
check "contador no leídas > 0" True "$(body "$(req GET /api/notifications/unread-count "$BETO")" | json "['count']>0")"
check "marcar todas leídas" 200 "$(status "$(req PATCH /api/notifications/read-all "$BETO")")"

echo "== Administración"
check "usuario normal no accede a /api/admin -> 403" 403 "$(status "$(req GET /api/admin/users "$BETO")")"

echo "== IA"
R=$(req GET /api/ai/status "$ANA"); echo "     $(body "$R")"

echo "== Limpieza"
check "Beto (no creador ni propietario) no borra T2 -> 403" 403 "$(status "$(req DELETE /api/tasks/$T2 "$BETO")")"
check "eliminar tarea" 204 "$(status "$(req DELETE /api/tasks/$T3 "$ANA")")"
check "tarea eliminada -> 404" 404 "$(status "$(req GET /api/tasks/$T3 "$ANA")")"

echo
echo "Resultado: $PASS OK, $FAIL FALLOS"
echo "IDS: P1=$P1 T1=$T1 T2=$T2 ANA=$ANA BETO=$BETO"
[ "$FAIL" -eq 0 ]
