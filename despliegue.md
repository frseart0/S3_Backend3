# Despliegue de Banco XYZ en AWS

El sistema ya esta empaquetado en un `Dockerfile` de dos etapas (Maven y JRE 21) y en `docker-compose.yml`. En AWS se reutiliza ese mismo artefacto: las imagenes van a ECR y el compose corre en una instancia EC2. Postgres pasa a RDS. Kafka puede quedarse en el compose o moverse a MSK cambiando una variable.

No hay que aprovisionar la cuenta desde este repositorio. Los pasos los ejecuta quien tenga credenciales de AWS.

## 1. Que se despliega

| Pieza local | En AWS |
| --- | --- |
| `postgres` del compose | Amazon RDS for PostgreSQL 16, base `banco_xyz` |
| `kafka` del compose | El contenedor Kafka en la misma EC2, o Amazon MSK |
| Resto de servicios | Contenedores en EC2, imagenes en ECR |
| Gateway `:8443` | Security group abierto a 443, con el certificado de desarrollo o uno de ACM delante de un ALB |
| Batch | Tarea puntual en la EC2 (`docker run` o `java -jar`), no un servicio siempre encendido |

Los servicios se descubren por Eureka. El gateway enruta `/api/web`, `/api/mobile` y `/api/atm` con `lb://` al BFF correspondiente.

## 2. RDS

1. Crear una instancia RDS PostgreSQL 16 en la misma VPC que la EC2.
2. Base de datos `banco_xyz`, usuario `banco_xyz`.
3. Security group: permitir 5432 solo desde el security group de la EC2.
4. Anotar el endpoint, por ejemplo `banco-xyz.xxxx.us-east-1.rds.amazonaws.com`.

El esquema lo crean los servicios al arrancar (`schema-negocio.sql` y `schema-api.sql`). No hace falta cargarlo a mano.

## 3. Repositorio de imagenes

```bash
aws ecr create-repository --repository-name banco-xyz --region us-east-1
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
```

Construir y subir cada modulo. El `Dockerfile` de la raiz recibe el modulo por build-arg:

```bash
ACCOUNT=<account>.dkr.ecr.us-east-1.amazonaws.com
for MODULE in banco-xyz-config-server banco-xyz-discovery banco-xyz-auth-server \
  banco-xyz-clientes banco-xyz-cuentas banco-xyz-pagos banco-xyz-core-api \
  banco-xyz-bff-web banco-xyz-bff-mobile banco-xyz-bff-atm \
  banco-xyz-notificaciones banco-xyz-gateway banco-xyz-batch
do
  docker build -f Dockerfile --build-arg MODULE=$MODULE -t $ACCOUNT/banco-xyz:$MODULE .
  docker push $ACCOUNT/banco-xyz:$MODULE
done
```

## 4. EC2

1. Amazon Linux 2023 o Ubuntu, tipo `t3.large` como minimo: son doce contenedores.
2. Instalar Docker y el plugin Compose.
3. Security group:
   - 22 desde la IP de administracion
   - 443 (o 8443) desde los clientes
   - 8761 solo si se quiere ver Eureka; mejor dejarlo cerrado y usar un tunel SSH
4. Copiar a la instancia `docker-compose.yml`, la carpeta `config-repo/` y, si el batch se corre ahi, el jar de `banco-xyz-batch`.

En el compose de la nube se quita el servicio `postgres` y se apuntan las URLs. En `config-repo` de cada servicio de datos:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://banco-xyz.xxxx.us-east-1.rds.amazonaws.com:5432/banco_xyz
    username: banco_xyz
    password: ${DB_PASSWORD}
```

La clave no va en el archivo. Se inyecta al compose:

```yaml
environment:
  DB_PASSWORD: ${DB_PASSWORD}
  CONFIG_SERVER: http://config-server:8888
```

Si Kafka sigue en el compose, no hay que cambiar `KAFKA_BOOTSTRAP`. Si se usa MSK, en `config-repo/application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: b-1.banco.xxxx.kafka.us-east-1.amazonaws.com:9092
```

y se elimina el servicio `kafka` del compose. Los `depends_on` de `pagos` y `notificaciones` que esperan a Kafka hay que quitarlos en ese caso.

## 5. Certificado del borde

El gateway termina TLS con `banco-xyz-gateway/src/main/resources/keystore.p12`. La contrasena de desarrollo es `banco-xyz-dev` y se pasa como `GATEWAY_KEYSTORE_PASSWORD`.

Para un nombre publico:

1. Generar un certificado para ese nombre (o pedir uno en ACM).
2. Si el certificado esta en ACM, poner un Application Load Balancer en el puerto 443 delante de la EC2 y dejar el gateway en HTTP dentro de la VPC. En ese caso `server.ssl.enabled` pasa a `false` y el puerto publicado del gateway es 8088, solo abierto al security group del balanceador.
3. Si el certificado vive en la EC2, reemplazar el PKCS12 del gateway y abrir 8443.

El trafico entre contenedores sigue en la red de Docker, sin TLS. El canal que ve el cliente es el del gateway o el del balanceador.

## 6. Variables que cada servicio espera

| Variable | Quien | Valor en la nube |
| --- | --- | --- |
| `CONFIG_SERVER` | todos los clientes del config | `http://config-server:8888` |
| `DB_PASSWORD` | clientes, cuentas, pagos, core-api, notificaciones, batch | la clave de RDS |
| `CORE_INTERNAL_KEY` | servicios internos y BFF | una clave distinta de la de desarrollo |
| `OAUTH_HMAC_SECRET` | auth y BFF | al menos 32 bytes, el mismo en todos |
| `OAUTH_CLIENT_SECRET` | auth y BFF | secreto de `web-client`, `mobile-client` y `atm-client` |
| `GATEWAY_KEYSTORE_PASSWORD` | gateway | contrasena del PKCS12 |
| `EUREKA_URL` | ya la fija `config-repo/application.yml` | `http://discovery:8761/eureka/` |

En Docker el config-repo deja `bff.oauth2.modo=authorization-server` y las URLs `http://banco-xyz-clientes`, `http://banco-xyz-cuentas`, `http://banco-xyz-pagos` y `http://banco-xyz-core-api`. Eureka resuelve esos nombres. No hay que poner IPs.

## 7. Orden de arranque

```bash
export DB_PASSWORD='...'
export CORE_INTERNAL_KEY='...'
export OAUTH_HMAC_SECRET='...'
export OAUTH_CLIENT_SECRET='...'
docker compose up -d
```

1. Kafka (si sigue en el compose).
2. Config Server y Eureka.
3. Clientes, cuentas, pagos y core-api.
4. Auth server, que valida las credenciales contra clientes.
5. Los tres BFF y notificaciones.
6. Gateway.

## 8. Batch en la instancia

El batch no queda corriendo. Despues de que RDS tenga el esquema (basta con haber arrancado cuentas una vez):

```bash
docker run --rm --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://banco-xyz.xxxx.us-east-1.rds.amazonaws.com:5432/banco_xyz \
  -e SPRING_DATASOURCE_USERNAME=banco_xyz \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  <account>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz:banco-xyz-batch \
  --spring.batch.job.name=reporteTransaccionesDiariasJob
```

Repetir con `calculoInteresesMensualesJob` y `estadosCuentaAnualesJob`. Esa imagen hay que construirla igual que las otras; el `Dockerfile` sirve porque el modulo tiene `spring-boot-maven-plugin`. Si un job termina en `FAILED`, el proceso lo reejecuta hasta dos veces antes de salir con error.

El saldo vigente se siembra desde `cuentas_interes` la primera vez que arranca el servicio de cuentas. Conviene correr el job de intereses antes de ese primer arranque, o reiniciar cuentas despues del job.

## 9. Comprobacion

```bash
curl -k https://<ip-o-dns>:8443/actuator/health
```

Ese health es el del gateway. Eureka, en un tunel SSH al 8761, debe listar clientes, cuentas, pagos, core-api, los tres BFF, auth, notificaciones y el gateway.

Un login de prueba:

```bash
curl -k -s -X POST https://<ip-o-dns>:8443/api/web/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"jane.smith","password":"Banco2026*"}'
```

## 10. Escalar

Eureka ya elige una instancia al azar y el gateway usa el balanceador de Spring Cloud. Para sumar capacidad se levanta otro contenedor del mismo BFF o del mismo servicio de dominio, con otro `server.port` solo si comparten red de host. En la red de Compose, dos replicas del mismo servicio bastan: Eureka registra las dos y `lb://` reparte.

Kafka en este compose tiene una particion. Para repartir consumidores hay que subir `KAFKA_NUM_PARTITIONS` antes de crear los topicos, o recrear `banco.retiros.realizados` y `banco.transacciones.completadas` con mas particiones.
