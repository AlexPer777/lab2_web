import java.math.BigDecimal;

public class AreaChecker {
    public static boolean check(int x, BigDecimal y, BigDecimal r) {
        BigDecimal xValue = BigDecimal.valueOf(x);
        BigDecimal halfR = r.divide(BigDecimal.valueOf(2));

        boolean rectangle = x <= 0 && xValue.compareTo(halfR.negate()) >= 0
                && y.signum() >= 0 && y.compareTo(r) <= 0;

        BigDecimal distanceSquared = xValue.multiply(xValue).add(y.multiply(y));
        boolean circle = x <= 0 && y.signum() <= 0
                && distanceSquared.compareTo(halfR.multiply(halfR)) <= 0;

        BigDecimal triangleBoundary = xValue.multiply(BigDecimal.valueOf(2)).subtract(r);
        boolean triangle = x >= 0 && y.signum() <= 0
                && y.compareTo(triangleBoundary) >= 0;

        return rectangle || circle || triangle;
    }
}
