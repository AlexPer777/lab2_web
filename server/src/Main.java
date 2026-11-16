import com.fastcgi.FCGIInterface;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        var fastcgi = new FCGIInterface();

        while (fastcgi.FCGIaccept() >= 0) {
            long startTime = System.nanoTime();
            String method = FCGIInterface.request.params.getProperty("REQUEST_METHOD", "");

            if (!"GET".equals(method)) {
                sendResponse("405 Method Not Allowed", "Allow: GET\r\n",
                        "{\"error\":\"Only GET requests are supported\"}");
                continue;
            }

            try {
                String query = FCGIInterface.request.params.getProperty("QUERY_STRING", "");
                PointRequest point = PointRequest.parse(query);
                boolean hit = AreaChecker.check(point.x(), point.y(), point.r());
                String timestamp = Instant.now().toString();
                double executionTimeMs = (System.nanoTime() - startTime) / 1_000_000.0;
                String body = "{\"x\":" + point.x()
                        + ",\"y\":\"" + point.y().toPlainString() + "\""
                        + ",\"r\":" + point.r().toPlainString()
                        + ",\"hit\":" + hit
                        + ",\"timestamp\":\"" + timestamp + "\""
                        + ",\"executionTimeMs\":" + executionTimeMs + "}";
                sendResponse("200 OK", "", body);
            } catch (IllegalArgumentException e)
            { String safeMsg = e.getMessage() == null ? "Invalid request" : e.getMessage().replace("\"", "\\\"");
                sendResponse("400 Bad Request", "", "{\"error\":\"" + safeMsg + "\"}");
            } catch (Exception e) { sendResponse("500 Internal Server Error", "", "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private static void sendResponse(String status, String extraHeaders, String body) {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        String headers = "Status: " + status + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: " + content.length + "\r\n"
                + "Cache-Control: no-store, no-cache, must-revalidate\r\n"
                + extraHeaders
                + "\r\n";

        System.out.writeBytes(headers.getBytes(StandardCharsets.UTF_8));
        System.out.writeBytes(content);
        System.out.flush();
    }
}
