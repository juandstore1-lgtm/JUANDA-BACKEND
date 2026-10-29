# Compra pública de boletos (sin registro)

## Flujo

1. El visitante elige boletos en `/rifas/{id}` y completa nombre, WhatsApp, correo (opcional) y el aviso de privacidad.
2. `POST /api/raffles/{id}/tickets/purchase` sin `Authorization`.
3. El backend aplica los límites de solicitudes, valida y normaliza los datos, y reserva **todos los boletos o ninguno** con la rifa bloqueada.
4. La respuesta es `{"raffleId", "ticketIds", "ticketNumbers", "status": "RESERVED", "reservedUntil"}`, sin datos del comprador.
5. El visitante envía el comprobante por WhatsApp (flujo existente).
6. Un `GLOBAL_ADMIN` aprueba la reserva en el panel (`RESERVED → SOLD`) o la cancela; si nadie la aprueba, vence y el boleto vuelve a estar libre.

`RESERVED` significa pendiente de pago y `SOLD` significa pago aprobado. Enviar el formulario nunca marca un boleto como vendido.

No se usa CAPTCHA ni ningún servicio externo: la compra pública no necesita claves de terceros.

## Estados y contador `available_tickets`

| Operación | Antes | Después | Contador |
|---|---|---|---|
| Compra | `AVAILABLE` | `RESERVED` (+ vencimiento) | −1 por boleto |
| Vencimiento (tarea o compra posterior) | `RESERVED` vencido | `AVAILABLE` | +1 por boleto |
| Aprobar | `RESERVED` | `SOLD` | sin cambio (ya se descontó) |
| Aprobar | `SOLD` | sin cambio | sin cambio |
| Aprobar | `AVAILABLE` | **error** "La reserva del boleto NN ya no está vigente" (antes: `SOLD` sin comprador) | — |
| Cancelar | `RESERVED` o `SOLD` | `AVAILABLE` | +1 (antes no se reponía) |
| Cancelar | `AVAILABLE` | sin cambio | sin cambio |
| Borrar rifa | — | se borran rifa y boletos | — |

- El contador nunca supera `total_tickets` ni se incrementa dos veces para el mismo boleto: con la rifa bloqueada se vuelve a comprobar el estado.
- Aprobar y cancelar aceptan `?buyerId=`. Si la reserva cambió de comprador (venció y la tomó otra persona), la acción se rechaza.
- Todas las operaciones (compra, aprobación, cancelación, vencimiento, edición y borrado de la rifa) siguen el mismo orden de bloqueos: **rifa → boletos → compradores**.

## Controles contra abuso

| Control | Dónde | Se aplica a |
|---|---|---|
| Límite de solicitudes por IP (también cuentan las rechazadas) | `PurchaseRateLimiter` | compras anónimas |
| Límite de solicitudes por teléfono (normalizado: `+57 300…` = `300…`) | `PurchaseRateLimiter` | compras anónimas |
| Máximo de boletos por solicitud | `PurchaseRequestValidator` | todas |
| Máximo de reservas activas por teléfono y rifa (en base de datos) | `TicketService` | todas |
| Validación estricta de nombre, teléfono, correo y boletos | `PurchaseRequestValidator` | todas |
| Reservas que vencen solas | `ReservationExpiryJob` y la propia compra | todas |
| `X-Forwarded-For` solo con proxies de confianza configurados | `ClientIpResolver` | compras anónimas |

- Un token inválido o caducado se trata como anónimo, así que no sirve para saltarse los límites.
- Solo un token válido de personal (`GLOBAL_ADMIN`/`STORE_ADMIN`) omite los límites por IP y teléfono. Aun así pasa la validación y los máximos de boletos.

## Configuración

Los valores están en un solo lugar (`RafflePurchaseProperties`). Se cambian con variables de entorno, siguiendo la convención de Spring Boot: punto → `_`, sin guiones, en mayúsculas.

| Propiedad | Variable de entorno | Por defecto | Uso |
|---|---|---|---|
| `app.raffle-purchase.public-enabled` | `APP_RAFFLEPURCHASE_PUBLICENABLED` | `false` | Compra sin sesión. Con `false` se exige sesión, como antes |
| `…reservation-duration` | `APP_RAFFLEPURCHASE_RESERVATIONDURATION` | `15m` | Duración de la reserva |
| `…max-tickets-per-request` | `APP_RAFFLEPURCHASE_MAXTICKETSPERREQUEST` | `5` | Boletos por solicitud |
| `…max-active-tickets-per-phone` | `APP_RAFFLEPURCHASE_MAXACTIVETICKETSPERPHONE` | `5` | Reservas activas por teléfono y rifa |
| `…rate-limit-window` | `APP_RAFFLEPURCHASE_RATELIMITWINDOW` | `15m` | Ventana de los límites |
| `…max-requests-per-ip` | `APP_RAFFLEPURCHASE_MAXREQUESTSPERIP` | `20` | Solicitudes por IP y ventana |
| `…max-requests-per-phone` | `APP_RAFFLEPURCHASE_MAXREQUESTSPERPHONE` | `5` | Solicitudes por teléfono y ventana |
| `…trusted-proxies` | `APP_RAFFLEPURCHASE_TRUSTEDPROXIES` | `0` | Proxies delante de la app (ver "IP real") |
| `…default-country-code` | `APP_RAFFLEPURCHASE_DEFAULTCOUNTRYCODE` | `57` | Prefijo que se quita a los móviles locales |
| `…expiry-job-enabled` | `APP_RAFFLEPURCHASE_EXPIRYJOBENABLED` | `true` | Tarea de vencimiento |
| `…expiry-check-interval` | `APP_RAFFLEPURCHASE_EXPIRYCHECKINTERVAL` | `1m` | Frecuencia de la tarea |

El frontend no necesita variables nuevas para la compra.

Con `public-enabled=false` (valor por defecto), la compra anónima responde 401, como antes. Al arrancar con la compra pública habilitada se registran los límites activos, y un aviso si `trusted-proxies=0`.

### IP real detrás de un proxy

- Con `trusted-proxies=0` se usa la IP de la conexión. Detrás del proxy de Railway, esa es la IP del proxy, así que **todos los visitantes compartirían el límite por IP**.
- Con `trusted-proxies=N` se toma la N-ésima entrada de `X-Forwarded-For` contando desde la derecha. Las entradas anteriores las puede inventar el cliente y nunca se usan.
- El valor correcto depende de cuántos proxies añaden la cabecera delante de la app (normalmente 1 en Railway, sin CDN delante). **Hay que verificarlo en staging** (paso 7 de la guía) antes de fijarlo.
- No hay que activar a la vez `server.forward-headers-strategy` y `trusted-proxies`.

## Riesgos de operar sin CAPTCHA

Sin CAPTCHA, nada distingue a una persona de un script que imite al navegador. Los límites reducen el daño, pero no lo impiden:

- **Acaparamiento de boletos.** Un atacante con muchas IPs (proxies, redes móviles) y teléfonos inventados puede reservar boletos en bloque.
  - Por cada IP: 20 solicitudes cada 15 min, como máximo 5 boletos por solicitud y 5 reservas activas por teléfono.
  - Los boletos acaparados quedan bloqueados hasta que vencen (15 min), y el ataque puede repetirse.
  - Mitigaciones operativas: reducir los límites, acortar la reserva y cancelar en bloque desde el panel.
  - Mitigaciones técnicas pendientes de decidir: un tope de reservas por rifa y ventana, o verificar el teléfono.
- **Teléfonos y nombres falsos.** La validación solo comprueba el formato; no hay verificación por SMS ni WhatsApp.
- **Límites en memoria.** Valen por instancia y se reinician al reiniciar la aplicación (un despliegue los vacía). Con varias instancias, el límite efectivo se multiplica.
- **IP compartida.** Con `trusted-proxies` mal configurado, el límite por IP o bloquea a todos los visitantes a la vez o deja de distinguir atacantes.
- **Enumeración.** El mensaje del máximo por teléfono revela que ese teléfono ya tiene reservas en la rifa (limitado por los límites de solicitudes).
- **Datos basura en `buyers`.** Las reservas vencidas de bots dejan registros de compradores (ver "Límites conocidos").

Conviene vigilar después de abrir la compra: reservas que vencen sin pago, respuestas 429 en los registros del proxy y teléfonos repetidos con muchos boletos.

## Límites conocidos

- Las reservas anteriores a este cambio (`reservation_expires_at` nulo) no vencen solas (ver la migración).
- Los datos de los compradores de reservas vencidas o canceladas se conservan en `buyers` (como ya ocurría al cancelar). La política de retención está pendiente de decisión.
- Si un visitante paga después del vencimiento y el boleto ya se liberó, el panel no lo deja aprobar. El administrador debe gestionarlo a mano.

## Guía de pruebas manuales en staging

Requisitos previos:
- migración aplicada;
- `PUBLICENABLED=true` y `TRUSTEDPROXIES` según el paso 7;
- para los pasos de vencimiento, `RESERVATIONDURATION=2m`.

1. **Compra anónima**
   - Pasos: en una ventana privada, abrir una rifa `ACTIVE`, elegir 2 boletos, completar los datos y aceptar el aviso.
   - Resultado esperado:
     - pantalla "¡Boletas Reservadas!" con los números y la hora de vencimiento, y el aviso de que el pago está pendiente;
     - en la red, `POST …/purchase` sin `Authorization`, sin llamadas a servicios externos y con respuesta 200.
   - Criterio de aprobación: los boletos aparecen como "Reservado (pago pendiente)".
2. **Doble envío**
   - Pasos: hacer doble clic en "Confirmar Reserva".
   - Resultado esperado: una sola petición; el botón muestra "Reservando…".
   - Criterio de aprobación: un solo comprador nuevo en el panel.
3. **Boleto ocupado**
   - Pasos: en dos navegadores, elegir el mismo boleto y reservar en ambos.
   - Resultado esperado: solo uno lo consigue; el otro ve "Ya no están disponibles: #NN" y puede elegir otros.
   - Criterio de aprobación: el boleto tiene un solo comprador.
4. **Validación**
   - Pasos: nombre "A", teléfono "123", correo "x@y", 6 boletos.
   - Resultado esperado: errores junto a los campos; para 6 boletos, "Puedes reservar como máximo 5…".
   - Criterio de aprobación: nada reservado.
5. **Vencimiento**
   - Pasos: reservar y esperar más de 2 minutos sin aprobar.
   - Resultado esperado: el boleto vuelve a estar libre; `available_tickets` sube exactamente lo reservado.
   - Criterio de aprobación: el contador coincide con el recuento de boletos `AVAILABLE`.
6. **Aprobación**
   - Pasos: reservar y aprobar desde el panel antes del vencimiento; esperar a que pase la hora de vencimiento.
   - Resultado esperado: el boleto queda "Vendido · pago aprobado" y no se libera.
   - Criterio de aprobación: el estado no cambia.
7. **IP real**
   - Pasos: con `MAXREQUESTSPERIP=3`, hacer 4 compras fallidas desde la red A (wifi) y luego 1 desde la red B (datos móviles).
   - Resultado esperado: la red A recibe 429 y la B no.
   - Criterio de aprobación: si B también recibe 429, `TRUSTEDPROXIES` no es correcto. Restaurar el valor al terminar.
8. **Cabecera falsificada**
   - Pasos: con curl, 21 solicitudes desde la misma máquina, cada una con un `X-Forwarded-For` distinto en la parte izquierda.
   - Resultado esperado: 429 a partir del límite.
   - Criterio de aprobación: cambiar la cabecera no reinicia el límite.
9. **Límite por teléfono**
   - Pasos: 6 reservas de 1 boleto con el mismo teléfono escrito de distintas formas.
   - Resultado esperado: a partir del límite, 429 (solicitudes) o "Se alcanzó el máximo de boletos reservados…".
   - Criterio de aprobación: no más de 5 boletos `RESERVED` para ese teléfono.
10. **Panel con datos viejos**
    - Pasos: abrir el panel con una reserva, dejarla vencer, reservarla con otro teléfono y luego aprobar desde el panel viejo.
    - Resultado esperado: error "La reserva del boleto NN cambió…" y la lista se recarga.
    - Criterio de aprobación: la reserva del segundo comprador sigue intacta.
11. **Cancelación**
    - Pasos: cancelar una reserva desde el panel.
    - Resultado esperado: el boleto queda libre y `available_tickets` sube 1.
    - Criterio de aprobación: cancelar de nuevo no cambia nada.
12. **Admin sigue protegido**
    - Pasos: sin token, llamar a `PUT …/approve`, `PUT …/cancel`, `GET …/tickets/admin` y `POST /api/raffles`.
    - Resultado esperado: 401 en todas.
    - Criterio de aprobación: sin cambios en la base de datos.
13. **Sin datos personales**
    - Pasos: `GET /api/raffles/{id}/tickets` y la respuesta de la compra.
    - Resultado esperado: ni nombre, ni teléfono, ni correo.
    - Criterio de aprobación: búsqueda de texto negativa.
14. **Sesión de admin abierta**
    - Pasos: con sesión de admin en el mismo navegador, reservar desde la página pública.
    - Resultado esperado: la compra no envía el token y la sesión de admin sigue activa.
    - Criterio de aprobación: el panel sigue accesible.
