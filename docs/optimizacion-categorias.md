# Optimización de `GET /api/categories`

## Causa

`categories.image` (TEXT) guarda la imagen como base64. El panel de categorías la subía sin comprimir, así que cada
categoría podía pesar varios MB y cada listado los enviaba todos, aunque el catálogo público solo usa `id` y `name`.

## Cambios

| Dónde | Cambio |
|---|---|
| Backend | `GET /api/categories?includeImage=false` devuelve el mismo listado (mismos filtros y orden) con `image: null` y no lee la columna `image`. Sin el parámetro, o con `true`, la respuesta es la de siempre. |
| Backend | `server.compression` activo para `application/json` mayores de 2 KB. |
| Frontend | El catálogo público (`StoreCatalog`) y el selector de categorías de productos piden `includeImage=false`. |
| Frontend | El panel de categorías comprime la imagen antes de guardarla (máx. 800 px, JPEG 0.8). |

Desplegar primero el backend: un frontend nuevo contra un backend viejo sigue funcionando (el parámetro se ignora),
pero no gana velocidad.

## Medir (solo lectura)

```bash
curl -s -o /dev/null -H "Accept-Encoding: gzip" \
  -w "bytes=%{size_download} ttfb=%{time_starttransfer}s total=%{time_total}s\n" \
  "https://<backend>/api/categories?storeId=1"
# Repetir con &includeImage=false
```

```sql
SELECT id, name, length(image) AS image_chars FROM categories ORDER BY image_chars DESC NULLS LAST;
```

## Reducir las imágenes ya guardadas (pendiente, lo ejecuta el administrador)

Las imágenes existentes no se modifican solas. `scripts/RecompressCategoryImages.java` las reduce (máx. 800 px,
JPEG 0.8) sin dependencias nuevas. Solo toca imágenes base64 de más de 300 000 caracteres, sin transparencia y que
bajen al menos un 20 %. Con `--apply` copia antes las filas afectadas a `categories_image_backup_<fecha>` y actualiza
todo en una transacción. Ejecutarlo dos veces no cambia nada más.

PowerShell, desde la raíz del backend (las credenciales se escriben en tu terminal; el script no las imprime):

```powershell
$env:DATABASE_URL = "<URL pública de Postgres de Railway>"   # postgresql://usuario:clave@host:puerto/bd o jdbc:postgresql://...
# Si la URL no lleva usuario y clave: $env:DATABASE_USERNAME = "..."; $env:DATABASE_PASSWORD = "..."
$jar = "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar"

java -Dstdout.encoding=UTF-8 -cp $jar scripts/RecompressCategoryImages.java           # 1. solo informa
java -Dstdout.encoding=UTF-8 -cp $jar scripts/RecompressCategoryImages.java --apply   # 2. respalda y aplica

Remove-Item Env:DATABASE_URL, Env:DATABASE_USERNAME, Env:DATABASE_PASSWORD -ErrorAction SilentlyContinue
```

La salida indica la tabla de respaldo y la sentencia `UPDATE` para restaurar. Cuando el resultado se vea bien en la
tienda, la tabla de respaldo se puede borrar. Las imágenes que el script no reduce (PNG con transparencia, URLs
externas) se pueden volver a subir desde el panel, que ya las comprime.
