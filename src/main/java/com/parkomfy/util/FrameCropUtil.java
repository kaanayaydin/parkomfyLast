package com.parkomfy.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** JPEG kare üzerinde normalize (0-1) bbox ile araç kırpımı. */
public final class FrameCropUtil {

    private FrameCropUtil() {}

    /**
     * Slot poligonuna (perspektif dörtgen) maskeli kırpım: poligon dışı pikseller
     * siyahlanır, böylece OCR komşu slottaki aracın plakasını okumaz.
     * Köşeler centroid etrafında {@code expandScale} ile büyütülür ve plaka araç
     * gövdesinde (zemin dörtgeninin biraz üstünde) olduğu için {@code shiftUpFrac}
     * kadar yukarı kaydırılır (normalize 0-1).
     */
    public static byte[] cropPolygonMaskedJpeg(byte[] jpeg, double[] nxs, double[] nys,
                                               double expandScale, double shiftUpFrac) {
        if (jpeg == null || jpeg.length == 0 || nxs == null || nys == null
                || nxs.length < 3 || nxs.length != nys.length) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null) {
                return null;
            }
            int iw = img.getWidth();
            int ih = img.getHeight();
            int n = nxs.length;
            double cx = 0, cy = 0;
            for (int i = 0; i < n; i++) {
                cx += nxs[i];
                cy += nys[i];
            }
            cx /= n;
            cy /= n;

            int[] px = new int[n];
            int[] py = new int[n];
            int minX = iw, minY = ih, maxX = 0, maxY = 0;
            for (int i = 0; i < n; i++) {
                double ex = cx + (nxs[i] - cx) * expandScale;
                double ey = cy + (nys[i] - cy) * expandScale - shiftUpFrac;
                int xpix = clamp((int) Math.round(ex * iw), 0, iw - 1);
                int ypix = clamp((int) Math.round(ey * ih), 0, ih - 1);
                px[i] = xpix;
                py[i] = ypix;
                minX = Math.min(minX, xpix);
                maxX = Math.max(maxX, xpix);
                minY = Math.min(minY, ypix);
                maxY = Math.max(maxY, ypix);
            }
            int w = maxX - minX;
            int h = maxY - minY;
            if (w < 8 || h < 8) {
                return null;
            }

            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            Polygon poly = new Polygon();
            for (int i = 0; i < n; i++) {
                poly.addPoint(px[i] - minX, py[i] - minY);
            }
            g.setClip(poly);
            g.drawImage(img, -minX, -minY, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

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
