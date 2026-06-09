package com.parkomfy.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** JPEG kare üzerinde normalize (0-1) bbox ile araç kırpımı. */
public final class FrameCropUtil {

    private FrameCropUtil() {}

    public static byte[] cropNormalizedJpeg(byte[] jpeg, double nx, double ny, double nw, double nh) {
        if (jpeg == null || jpeg.length == 0 || nw <= 0 || nh <= 0) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null) {
                return null;
            }
            int iw = img.getWidth();
            int ih = img.getHeight();
            int x1 = clamp((int) (nx * iw), 0, iw - 1);
            int y1 = clamp((int) (ny * ih), 0, ih - 1);
            int x2 = clamp((int) ((nx + nw) * iw), x1 + 1, iw);
            int y2 = clamp((int) ((ny + nh) * ih), y1 + 1, ih);
            int w = x2 - x1;
            int h = y2 - y1;
            if (w < 8 || h < 8) {
                return null;
            }
            BufferedImage crop = img.getSubimage(x1, y1, w, h);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(crop, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
