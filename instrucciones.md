# Instrucciones para ejecutar y probar Banco XYZ

Repositorio: https://github.com/frseart0/S3_Backend3

## Requisitos

- JDK 21
- Docker y Docker Compose
- Maven (el repo incluye `mvnw.cmd`)

En PowerShell, si `java -version` no es 21:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
```

## 1. Infraestructura

Desde la raiz del repositorio:

```powershell
docker compose up -d postgres kafka
```

Postgres queda en `localhost:5432` (base `banco_xyz`, usuario y clave `banco_xyz`). Kafka queda en `localhost:9092`.

## 2. Compilar

```powershell
.\mvnw.cmd clean package -DskipTests
```

## 3. Cargar los tres jobs batch

Cada comando termina solo. Si un job cae por un fallo critico, se reejecuta solo hasta dos veces (`batch.restart.max-intentos`).

```powershell
.\mvnw.cmd -pl banco-xyz-batch spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.name=reporteTransaccionesDiariasJob"
.\mvnw.cmd -pl banco-xyz-batch spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.name=calculoInteresesMensualesJob"
.\mvnw.cmd -pl banco-xyz-batch spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.name=estadosCuentaAnualesJob"
```

Comprobacion:

```sql
SELECT estado, COUNT(*) FROM transacciones GROUP BY estado;
SELECT COUNT(*) FROM cuenta_saldo;
SELECT COUNT(*) FROM estado_cuenta_anual;
```

## 4. Levantar el sistema

La forma corta, con Config Server, Eureka, OAuth2 y el gateway HTTPS:

```powershell
docker compose up -d --build
```

El batch no entra en el compose: hay que haberlo corrido antes para que existan saldos. `banco-xyz-clientes` siembra las identidades de demostracion al arrancar.

Puertos:

| Servicio | URL |
| --- | --- |
| Eureka | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Auth | http://localhost:9000 |
| Clientes | http://localhost:8085 |
| Cuentas | http://localhost:8086 |
| Pagos | http://localhost:8087 |
| Reportes (core-api) | http://localhost:8080 |
| BFF web / movil / cajero | http://localhost:8081 / 8082 / 8083 |
| Gateway | https://localhost:8443 |
| Notificaciones | http://localhost:8084 |

El certificado del gateway es de desarrollo. En curl usar `-k`. La contrasena del keystore es `banco-xyz-dev`.

### Arranque manual, sin Docker para las aplicaciones

Con Postgres y Kafka ya arriba, en terminales distintas y en este orden:

```powershell
.\mvnw.cmd -pl banco-xyz-config-server spring-boot:run
.\mvnw.cmd -pl banco-xyz-discovery spring-boot:run
.\mvnw.cmd -pl banco-xyz-clientes spring-boot:run
.\mvnw.cmd -pl banco-xyz-cuentas spring-boot:run
.\mvnw.cmd -pl banco-xyz-pagos spring-boot:run
.\mvnw.cmd -pl banco-xyz-core-api spring-boot:run
.\mvnw.cmd -pl banco-xyz-auth-server spring-boot:run
.\mvnw.cmd -pl banco-xyz-notificaciones spring-boot:run
.\mvnw.cmd -pl banco-xyz-bff-web spring-boot:run
.\mvnw.cmd -pl banco-xyz-bff-mobile spring-boot:run
.\mvnw.cmd -pl banco-xyz-bff-atm spring-boot:run
.\mvnw.cmd -pl banco-xyz-gateway spring-boot:run
```

Sin el config server, cada servicio usa su `application.yml` y los BFF firman el JWT ellos mismos (`bff.oauth2.modo=local`).

## 5. Identidades de prueba

Password comun: `Banco2026*`.

| Usuario | Cuenta | Canales | Tarjeta | PIN |
| --- | --- | --- | --- | --- |
| `jane.smith` | 106 | web, mobile, atm | `4051000000000106` | `1234` |
| `charlie.green` | 109 | web, mobile | — | — |
| `steve.rogers` | 117 | web, atm | `4051000000000117` | `4321` |

Cajeros: `ATM-001` / `llave-atm-001` y `ATM-002` / `llave-atm-002`.

Clave interna de los servicios: `clave-interna-banco-xyz-2026`, cabecera `X-Internal-Key`.

## 6. Probar cada canal por el gateway

### Web

```powershell
curl.exe -k -s -X POST https://localhost:8443/api/web/auth/login -H "Content-Type: application/json" -d "{\"usuario\":\"jane.smith\",\"password\":\"Banco2026*\"}"
```

Con el `accessToken` de la respuesta:

```powershell
curl.exe -k -s https://localhost:8443/api/web/dashboard -H "Authorization: Bearer TOKEN"
curl.exe -k -s https://localhost:8443/api/web/perfil-cliente -H "Authorization: Bearer TOKEN"
curl.exe -k -s -X POST https://localhost:8443/api/web/depositos -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" -d "{\"monto\":1500,\"claveIdempotencia\":\"dep-web-1\"}"
```

Un token de web contra `https://localhost:8443/api/atm/saldo` debe responder 401.

### Movil

```powershell
curl.exe -k -s -X POST https://localhost:8443/api/mobile/auth/login -H "Content-Type: application/json" -d "{\"usuario\":\"charlie.green\",\"password\":\"Banco2026*\"}"
curl.exe -k -s https://localhost:8443/api/mobile/inicio -H "Authorization: Bearer TOKEN"
curl.exe -k -s https://localhost:8443/api/mobile/perfil -H "Authorization: Bearer TOKEN"
```

`charlie.green` no tiene tarjeta: el login de cajero con su usuario no aplica. `steve.rogers` no esta habilitado en movil y el login movil debe rechazarlo.

### Cajero

```powershell
curl.exe -k -s -X POST https://localhost:8443/api/atm/auth/sesion -H "Content-Type: application/json" -d "{\"tarjeta\":\"4051000000000106\",\"pin\":\"1234\",\"dispositivoId\":\"ATM-001\",\"dispositivoClave\":\"llave-atm-001\"}"
```

Con el token y la cabecera `X-Device-Id: ATM-001`:

```powershell
curl.exe -k -s https://localhost:8443/api/atm/saldo -H "Authorization: Bearer TOKEN" -H "X-Device-Id: ATM-001"
curl.exe -k -s -X POST https://localhost:8443/api/atm/retiros -H "Authorization: Bearer TOKEN" -H "X-Device-Id: ATM-001" -H "Content-Type: application/json" -d "{\"monto\":2000,\"claveIdempotencia\":\"ret-atm-1\"}"
```

El monto tiene que ser multiplo de 1000 y no mayor a 100000. Repetir la misma `claveIdempotencia` devuelve el comprobante original y no descuenta dos veces.

Si pagos no responde, el cajero contesta 503 `CAJERO_NO_DISPONIBLE`. Web y movil contestan 503 `CANAL_NO_DISPONIBLE`.

## 7. Probar cuentas, pagos y clientes directo

Estas llamadas no pasan por el gateway: son el contrato interno.

```powershell
$h = @{ "X-Internal-Key" = "clave-interna-banco-xyz-2026"; "Content-Type" = "application/json" }

# Alta de cliente
Invoke-RestMethod -Method Post -Uri http://localhost:8085/internal/clientes -Headers $h -Body '{"usuario":"ana.ruiz","password":"Banco2026*","nombre":"Ana Ruiz","cuentaId":2001,"canales":"web,mobile"}'

# Apertura
Invoke-RestMethod -Method Post -Uri http://localhost:8086/internal/cuentas -Headers $h -Body '{"cuentaId":2001,"tipo":"ahorro","alias":"Cuenta Ana","saldoInicial":10000,"clienteUsuario":"ana.ruiz"}'

# Deposito, transferencia y pago
Invoke-RestMethod -Method Post -Uri http://localhost:8087/internal/cuentas/2001/depositos -Headers $h -Body '{"monto":500,"canal":"web","claveIdempotencia":"dep-ana-1"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8087/internal/cuentas/2001/transferencias -Headers $h -Body '{"cuentaDestinoId":106,"monto":250,"canal":"web","claveIdempotencia":"tr-ana-1"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8087/internal/cuentas/2001/pagos -Headers $h -Body '{"monto":100,"canal":"web","claveIdempotencia":"pago-ana-1","referencia":"servicio"}'

# Cierre: el pago siguiente debe responder 409 CUENTA_CERRADA
Invoke-RestMethod -Method Post -Uri http://localhost:8086/internal/cuentas/2001/cierre -Headers $h
```

## 8. Comprobar el evento

Con Kafka y `banco-xyz-notificaciones` en marcha, despues de un deposito:

```sql
SELECT tipo, clave_evento, cuenta_id, monto FROM notificacion_transaccion ORDER BY id DESC LIMIT 5;
SELECT clave_evento, cuenta_id, monto FROM notificacion_retiro ORDER BY id DESC LIMIT 5;
```

El retiro del cajero aparece en `notificacion_retiro`. Deposito, transferencia y pago aparecen en `notificacion_transaccion`.

## 9. OAuth2 directo

```powershell
curl.exe -s -X POST http://localhost:9000/oauth2/token -u web-client:banco-xyz-oauth-2026 -H "Content-Type: application/x-www-form-urlencoded" -d "grant_type=canal_password&canal=web&username=jane.smith&password=Banco2026*&scope=web"
```

El authorization server no guarda la contrasena: se la pregunta a `banco-xyz-clientes`.
