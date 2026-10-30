import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Reduce las imágenes base64 ya guardadas en categories.image (máx. 800 px, JPEG 0.8, igual que el panel).
 *
 * Uso (fuera de Maven, sin dependencias nuevas; usa el driver de PostgreSQL del repositorio local de Maven):
 *   java -cp <postgresql.jar> scripts/RecompressCategoryImages.java            -> solo informa (no modifica nada)
 *   java -cp <postgresql.jar> scripts/RecompressCategoryImages.java --apply    -> respalda y actualiza
 *
 * Conexión: DATABASE_URL (jdbc:postgresql://... o postgresql://usuario:clave@host:puerto/bd),
 * DATABASE_USERNAME y DATABASE_PASSWORD. Nunca se imprimen.
 *
 * Solo modifica una fila si: la imagen ocupa más de MIN_CHARS, es un data URL base64 que Java puede leer, no tiene
 * transparencia (JPEG la perdería) y el resultado es al menos un 20 % menor. Antes de actualizar copia las filas
 * afectadas (id, image) a una tabla categories_image_backup_<fecha> y actualiza en una sola transacción, solo si la
 * imagen no cambió mientras tanto.
 */
public class RecompressCategoryImages {

    private static final int MAX_SIZE = 800;
    private static final float QUALITY = 0.8f;
    private static final int MIN_CHARS = 300_000;

    record Candidate(long id, String name, int oldChars, String oldMd5, String newImage) {}

    public static void main(String[] args) throws Exception {
        boolean apply = List.of(args).contains("--apply");
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            List<Candidate> candidates = new ArrayList<>();

            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT id, name, length(image), md5(image), image FROM categories WHERE length(image) > ? ORDER BY id")) {
                ps.setInt(1, MIN_CHARS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong(1);
                        String name = rs.getString(2);
                        int chars = rs.getInt(3);
                        String result = recompress(rs.getString(5));
                        if (result.startsWith("data:")) {
                            if (result.length() <= chars * 0.8) {
                                candidates.add(new Candidate(id, name, chars, rs.getString(4), result));
                                System.out.printf("id=%d  %-30s %,12d -> %,10d caracteres%n", id, name, chars, result.length());
                            } else {
                                System.out.printf("id=%d  %-30s %,12d  sin cambio: no se reduce lo suficiente%n", id, name, chars);
                            }
                        } else {
                            System.out.printf("id=%d  %-30s %,12d  sin cambio: %s%n", id, name, chars, result);
                        }
                    }
                }
            }
            long before = candidates.stream().mapToLong(Candidate::oldChars).sum();
            long after = candidates.stream().mapToLong(c -> c.newImage().length()).sum();
            System.out.printf("%nCategorías a reducir: %d  (%,d -> %,d caracteres)%n", candidates.size(), before, after);

            if (!apply || candidates.isEmpty()) {
                System.out.println(apply ? "Nada que actualizar." : "Modo de prueba: no se modificó nada. Ejecuta con --apply para aplicar.");
                db.rollback();
                return;
            }

            String backup = "categories_image_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            try (PreparedStatement ps = db.prepareStatement("CREATE TABLE " + backup + " AS SELECT id, image FROM categories WHERE id = ANY (?)")) {
                ps.setArray(1, db.createArrayOf("bigint", candidates.stream().map(Candidate::id).toArray()));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = db.prepareStatement("UPDATE categories SET image = ? WHERE id = ? AND md5(image) = ?")) {
                for (Candidate c : candidates) {
                    ps.setString(1, c.newImage());
                    ps.setLong(2, c.id());
                    ps.setString(3, c.oldMd5());
                    if (ps.executeUpdate() != 1) {
                        db.rollback();
                        System.out.println("La categoría " + c.id() + " cambió durante el proceso. No se modificó nada; vuelve a ejecutarlo.");
                        return;
                    }
                }
            }
            db.commit();
            System.out.println("Actualizadas " + candidates.size() + " categorías. Respaldo en la tabla " + backup + ".");
            System.out.println("Restaurar: UPDATE categories c SET image = b.image FROM " + backup + " b WHERE b.id = c.id;");
        }
    }

    /** Devuelve el nuevo data URL, o el motivo por el que no se puede reducir. */
    static String recompress(String image) throws Exception {
        int comma = image.indexOf(',');
        if (!image.startsWith("data:image/") || comma < 0 || !image.substring(0, comma).endsWith(";base64")) {
            return "no es una imagen base64 (data URL)";
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(image.substring(comma + 1))));
        } catch (Exception e) {
            source = null;
        }
        if (source == null) return "formato no soportado (" + image.substring(5, image.indexOf(';')) + ")";
        if (source.getColorModel().hasAlpha() && hasTransparentPixels(source)) return "tiene transparencia";

        double scale = Math.min(1.0, (double) MAX_SIZE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(target, null, null), param);
        } finally {
            writer.dispose();
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    static boolean hasTransparentPixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }

    static Connection connect() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DATABASE_USERNAME");
        String password = System.getenv("DATABASE_PASSWORD");
        if (url == null || url.isBlank()) throw new IllegalStateException("Falta la variable DATABASE_URL");
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            URI uri = URI.create(url);
            String info = uri.getRawUserInfo();
            if (info != null) {
                String[] parts = info.split(":", 2);
                if (user == null) user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (password == null && parts.length > 1) password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
            url = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getRawPath()
                    + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        }
        return DriverManager.getConnection(url, user, password);
    }
}
