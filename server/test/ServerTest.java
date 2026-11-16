import java.math.BigDecimal;

public class ServerTest {
    private static int checks = 0;

    public static void main(String[] args) {
        checkPoint("x=-1&y=2&r=2", true);
        checkPoint("x=-1&y=2.00000000000000000001&r=2", false);
        checkPoint("x=-1&y=1&r=1.5", false);
        checkPoint("x=-1&y=0&r=2", true);
        checkPoint("x=-1&y=-0.00000000000000000001&r=2", false);
        checkPoint("x=-1&y=-0.75&r=2.5", true);
        checkPoint("x=-1&y=-0.75000000000000000001&r=2.5", false);
        checkPoint("x=0&y=-2&r=2", true);
        checkPoint("x=0&y=-2.00000000000000000001&r=2", false);
        checkPoint("x=1&y=-0.5&r=2.5", true);
        checkPoint("x=1&y=-0.50000000000000000001&r=2.5", false);
        checkPoint("x=1&y=0&r=2", true);
        checkPoint("x=1&y=-0.01&r=2", false);
        checkPoint("x=1&y=1&r=3", false);
        checkPoint("x=5&y=0&r=3", false);
        checkPoint("x=0&y=2.99999999999999999999&r=3", true);

        for (String r : new String[]{"1", "1.5", "2", "2.5", "3"}) {
            checkPoint("x=0&y=0&r=" + r, true);
            checkPoint("x=-3&y=0&r=" + r, false);
        }

        PointRequest point = PointRequest.parse("r=2%2C5&y=%20-0%2C5%20&%78=1");
        if (point.x() != 1 || point.y().compareTo(new BigDecimal("-0.5")) != 0
                || point.r().compareTo(new BigDecimal("2.5")) != 0) {
            throw new AssertionError("URL decoding failed");
        }
        checks++;

        for (String query : new String[]{
                "", "x=0&y=0", "x=0&y=0&r=", "x=0&y=0&r=1&r=2",
                "x=0&y=0&r=1&z=2", "x=0&y=0&r=1&", "x&y=0&r=1",
                "x=0&y=%&r=1", "x=0&y=%GG&r=1", "x=0&y=3&r=1",
                "x=0&y=-3&r=1", "x=0&y=3.000&r=1", "x=0&y=-3.0001&r=1",
                "x=0&y=NaN&r=1", "x=0&y=Infinity&r=1", "x=0&y=1e0&r=1",
                "x=-4&y=0&r=1", "x=6&y=0&r=1", "x=1.5&y=0&r=1",
                "x=99999999999999999999&y=0&r=1", "x=0&y=0&r=0",
                "x=0&y=0&r=4", "x=0&y=0&r=1.1", "x=0&y=0&r=NaN",
                "x=0&y=0&r=2.50000000000000000001", "x=0&y=0&r=1&%78=1"
        }) {
            checkInvalid(query);
        }

        System.out.println("Passed: " + checks);
    }

    private static void checkPoint(String query, boolean expected) {
        PointRequest point = PointRequest.parse(query);
        boolean actual = AreaChecker.check(point.x(), point.y(), point.r());
        if (actual != expected) {
            throw new AssertionError("Wrong result: " + query);
        }
        checks++;
    }

    private static void checkInvalid(String query) {
        try {
            PointRequest.parse(query);
        } catch (IllegalArgumentException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid parameters: " + query);
    }
}
