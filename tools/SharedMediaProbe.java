// Read-only JVM probe: exercise the shared mux with full-canvas native exports.
// Fixtures are user-selected local paths and are never added to Git or uploaded.
import app.stickerdock.core.AnimatedWebp;
import app.stickerdock.core.WebpMetadata;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;

public class SharedMediaProbe {
    static int read(byte[] b, int at, int count) {
        int n = 0;
        for (int i = 0; i < count; i++) n |= (b[at + i] & 255) << (i * 8);
        return n;
    }
    static void uint(ByteArrayOutputStream out, int value, int count) {
        for (int i = 0; i < count; i++) out.write(value >>> (i * 8));
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Choose native full-canvas WebP fixture files");
        for (String arg : args) {
            if (Files.size(Path.of(arg)) > 500_000) throw new IllegalArgumentException("Fixture exceeds sticker size limit");
            byte[] original = Files.readAllBytes(Path.of(arg));
            var info = WebpMetadata.INSTANCE.inspect(original);
            if (info == null || !info.getAnimated() || info.getFrames() < 2)
                throw new IllegalArgumentException("Invalid animation fixture");
            var frames = new ArrayList<byte[]>();
            int duration = -1;
            for (int at = 12; at + 8 <= original.length;) {
                int size = read(original, at + 4, 4);
                if (new String(original, at, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ANMF")) {
                    int start = at + 8;
                    if (read(original,start,3) != 0 || read(original,start+3,3) != 0 ||
                        read(original,start+6,3) != 511 || read(original,start+9,3) != 511 || original[start+15] != 2)
                        throw new IllegalArgumentException("Choose full-canvas replace-frame exports");
                    int ms = read(original,start+12,3);
                    if (duration != -1 && duration != ms) throw new IllegalArgumentException("Choose constant-duration exports");
                    duration = ms;
                    byte[] image = Arrays.copyOfRange(original,start+16,start+size);
                    var body = new ByteArrayOutputStream(); body.writeBytes("WEBP".getBytes()); body.writeBytes(image);
                    var riff = new ByteArrayOutputStream(); riff.writeBytes("RIFF".getBytes()); uint(riff,body.size(),4); riff.writeBytes(body.toByteArray());
                    frames.add(riff.toByteArray());
                }
                at += 8 + size + (size & 1);
            }
            byte[] rebuilt = AnimatedWebp.INSTANCE.encode(frames,duration,500_000);
            if (!Arrays.equals(original,rebuilt)) throw new AssertionError("Shared mux changed native export bytes");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rebuilt));
            System.out.println(Path.of(arg).getFileName()+"|"+frames.size()+"|"+rebuilt.length+"|"+hash);
        }
    }
}
