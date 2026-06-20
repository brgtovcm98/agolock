package com.seu.seustock.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

  public byte[] generateQrCodeImage(String text, int width, int height)
      throws WriterException, IOException {
    Map<EncodeHintType, Object> hints = new HashMap<>();
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    hints.put(EncodeHintType.MARGIN, 1);

    BitMatrix bitMatrix =
        new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
    return baos.toByteArray();
  }
}
