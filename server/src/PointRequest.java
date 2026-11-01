import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record PointRequest(int x, BigDecimal y, BigDecimal r) {
    public static PointRequest parse(String query) {
        Map<String, String> parameters = new HashMap<>();

        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Parameters x, y and r are required");
        }

        for (String pair : query.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid query string");
            }

            String name = decode(parts[0]);
            String value = decode(parts[1]).trim();
            if (!Set.of("x", "y", "r").contains(name)) {
                throw new IllegalArgumentException("Unknown parameter");
            }
            if (parameters.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate parameter");
            }
        }

        if (parameters.size() != 3 || parameters.containsValue("")) {
            throw new IllegalArgumentException("Parameters x, y and r are required");
        }

        int x = parseX(parameters.get("x"));
        BigDecimal y = parseDecimal(parameters.get("y"));
        BigDecimal r = parseDecimal(parameters.get("r"));

        if (y.compareTo(BigDecimal.valueOf(-3)) <= 0 || y.compareTo(BigDecimal.valueOf(3)) >= 0) {
            throw new IllegalArgumentException("Y must be strictly between -3 and 3");
        }
        if (r.compareTo(BigDecimal.ONE) < 0 || r.compareTo(BigDecimal.valueOf(3)) > 0
                || r.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("R must be one of 1, 1.5, 2, 2.5, 3");
        }

        return new PointRequest(x, y, r);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL encoding");
        }
    }

    private static int parseX(String value) {
        try {
            if (!value.matches("-?\\d+")) {
                throw new NumberFormatException();
            }
            int x = Integer.parseInt(value);
            if (x < -3 || x > 5) {
                throw new NumberFormatException();
            }
            return x;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("X must be an integer from -3 to 5");
        }
    }

    private static BigDecimal parseDecimal(String value) {
        String normalized = value.replace(',', '.');
        if (!normalized.matches("-?\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("Y and R must be decimal numbers");
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Y and R must be decimal numbers");
        }
    }
}
