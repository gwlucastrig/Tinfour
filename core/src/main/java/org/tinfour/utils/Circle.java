package org.tinfour.utils;

/**
 * A simple representation of a circle, defined by a center point (x,y)
 * and a radius. This class is primarily used for geometric calculations
 * such as checking point containment or representing diametral circles of segments.
 */
public class Circle {
    /** The x-coordinate of the center of the circle. */
    public final double x;
    /** The y-coordinate of the center of the circle. */
    public final double y;
    /** The radius of the circle. */
    public final double radius;
    /** The square of the radius (radius * radius), stored for efficient distance comparisons. */
    public final double radiusSq;

    /**
     * Constructs a new Circle with the specified center coordinates and radius.
     *
     * @param x the x-coordinate of the center of the circle.
     * @param y the y-coordinate of the center of the circle.
     * @param radius the radius of the circle.
     */
    public Circle(double x, double y, double radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    /**
     * Checks if a point (px, py) is strictly inside this circle.
     * "Strictly inside" means the distance from the point to the circle's center
     * is less than the circle's radius, accounting for a specified tolerance.
     * Points on the boundary are not considered strictly inside.
     * <p>
     * The check performed is effectively:
     * {@code (px - this.x)^2 + (py - this.y)^2 < this.radiusSq - tolerance}.
     * A positive tolerance makes the condition for being "strictly inside" more stringent.
     *
     * @param px the x-coordinate of the point to check.
     * @param py the y-coordinate of the point to check.
     * @param tolerance a small positive value used to ensure the point is not too close
     *                  to the boundary. The squared distance from the point to the
     *                  circle's center must be less than (radius² - tolerance).
     * @return {@code true} if the point is strictly inside the circle;
     *         {@code false} otherwise, including if the point is on the boundary.
     */
    public boolean isStrictlyInside(double px, double py, double tolerance) {
        double dx = px - this.x;
        double dy = py - this.y;
        // Compare squared distances to avoid sqrt.
        // To be strictly inside, distSq must be less than radiusSq.
        // (px - cx)^2 + (py - cy)^2 < r^2
        // To handle floating point inaccuracies, we check if distSq is smaller than radiusSq by at least 'tolerance'.
        // This means (dx * dx + dy * dy) should be less than (this.radiusSq - tolerance).
        // If tolerance is positive, this makes the condition for being "inside" more stringent.
        return dx * dx + dy * dy < this.radiusSq - tolerance;
    }
}
