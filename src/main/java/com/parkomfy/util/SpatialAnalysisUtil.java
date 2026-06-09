package com.parkomfy.util;

/**
 * Rectangle intersection and IoU (Intersection over Union) for CV geometry checks.
 * IoU = Area(A ∩ B) / Area(A ∪ B).
 */
public final class SpatialAnalysisUtil {

    private SpatialAnalysisUtil() {}

    /** IoU for two axis-aligned rectangles (x, y, width, height). Returns 0 if no overlap. */
    public static double computeIoU(double x1, double y1, double w1, double h1,
                                    double x2, double y2, double w2, double h2) {
        double intersectionArea = computeIntersectionArea(x1, y1, w1, h1, x2, y2, w2, h2);
        double area1 = w1 * h1;
        double area2 = w2 * h2;
        double unionArea = area1 + area2 - intersectionArea;
        if (unionArea <= 0) {
            return 0.0;
        }
        return intersectionArea / unionArea;
    }

    /** Intersection area of two axis-aligned rectangles. */
    public static double computeIntersectionArea(double x1, double y1, double w1, double h1,
                                                 double x2, double y2, double w2, double h2) {
        double left = Math.max(x1, x2);
        double right = Math.min(x1 + w1, x2 + w2);
        double top = Math.max(y1, y2);
        double bottom = Math.min(y1 + h1, y2 + h2);
        if (left >= right || top >= bottom) {
            return 0.0;
        }
        return (right - left) * (bottom - top);
    }

    /** Rectangle area. */
    public static double area(double x, double y, double width, double height) {
        return width * height;
    }

    /** Union area: area1 + area2 - intersection. */
    public static double computeUnionArea(double x1, double y1, double w1, double h1,
                                         double x2, double y2, double w2, double h2) {
        double area1 = area(x1, y1, w1, h1);
        double area2 = area(x2, y2, w2, h2);
        double intersection = computeIntersectionArea(x1, y1, w1, h1, x2, y2, w2, h2);
        return area1 + area2 - intersection;
    }
}
