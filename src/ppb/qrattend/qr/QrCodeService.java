package ppb.qrattend.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class QrCodeService {

    private static final int DEFAULT_SIZE = 320;

    private QrCodeService() {
    }

    public static BufferedImage generateQrImage(String payload) throws WriterException {
        return generateQrImage(payload, DEFAULT_SIZE);
    }

    public static BufferedImage generateQrImage(String payload, int size) throws WriterException {
        String safePayload = payload == null ? "" : payload.trim();
        if (safePayload.isBlank()) {
            throw new IllegalArgumentException("QR payload is required.");
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BitMatrix matrix = new QRCodeWriter().encode(safePayload, BarcodeFormat.QR_CODE, size, size, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    public static byte[] generateQrPngBytes(String payload) throws WriterException, IOException {
        return generateQrPngBytes(payload, DEFAULT_SIZE);
    }

    public static byte[] generateQrPngBytes(String payload, int size) throws WriterException, IOException {
        BufferedImage image = generateQrImage(payload, size);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    public static String generateQrBase64Png(String payload) throws WriterException, IOException {
        return Base64.getEncoder().encodeToString(generateQrPngBytes(payload));
    }

    public static Path writeQrPng(String payload, Path path) throws WriterException, IOException {
        byte[] bytes = generateQrPngBytes(payload);
        Files.write(path, bytes);
        return path;
    }

    public static String decodeQrImage(BufferedImage image) throws NotFoundException {
        if (image == null) {
            throw new IllegalArgumentException("QR image is required.");
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }

    public static String decodeQrFile(Path path) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Selected file is not a readable image.");
        }
        return decodeQrImage(image);
    }
}
