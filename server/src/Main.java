import com.fastcgi.FCGIInterface;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        var fastcgi = new FCGIInterface();
        // FastCGI replaces System.err with a stream belonging to the current request.
        PrintStream log = System.err;

        while (acceptRequest(fastcgi, log)) {
            long startTime = System.nanoTime();

            try {
                String method = FCGIInterface.request.params.getProperty("REQUEST_METHOD", "");
                if (!"GET".equals(method)) {
                    sendResponse("405 Method Not Allowed", "Allow: GET\r\n",
                            "{\"error\":\"Only GET requests are supported\"}");
                    continue;
                }

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
            } catch (IllegalArgumentException e) {
                String message = e.getMessage() == null ? "Invalid request" : e.getMessage();
                sendResponse("400 Bad Request", "", "{\"error\":" + jsonString(message) + "}");
            } catch (Exception e) {
                e.printStackTrace(log);
                sendResponse("500 Internal Server Error", "", "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private static boolean acceptRequest(FCGIInterface fastcgi, PrintStream log) {
        while (true) {
            try {
                if (fastcgi.FCGIaccept() >= 0) {
                    return true;
                }
            } catch (RuntimeException e) {
                // The supplied library can throw on EOF before finishing a request.
                log.println("Failed to receive FastCGI request: " + e);
            }

            // A failed client must not stop the listening server. Reset the library's
            // request state so the next accept does not reuse a broken input stream.
            if (FCGIInterface.request != null && FCGIInterface.request.socket != null) {
                try {
                    FCGIInterface.request.socket.close();
                } catch (IOException e) {
                    log.println("Failed to close FastCGI connection: " + e);
                }
            }
            FCGIInterface.request = null;
            if (FCGIInterface.srvSocket == null || FCGIInterface.srvSocket.isClosed()) {
                log.println("FastCGI listener is unavailable; server stopped");
                return false;
            }
        }
    }

    static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.append('"').toString();
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
