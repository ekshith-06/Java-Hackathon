import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;

/** Serves the web page (folder "web") and the JSON API used by it. */
public class WebServer {
    private final Directory directory;
    private final Path webDir = Paths.get("web").toAbsolutePath().normalize();
    private HttpServer server;

    public WebServer(Directory directory) { this.directory = directory; }

    public int start(int preferredPort) throws IOException {
        int port = -1;
        for (int p = preferredPort; p < preferredPort + 20; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", p), 0);
                port = p;
                break;
            } catch (BindException e) { /* port busy, try next */ }
        }
        if (server == null) throw new IOException("No free port found");
        server.createContext("/", this::route);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        return port;
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.startsWith("/api/")) api(ex, method, path);
            else staticFile(ex, path);
        } catch (IllegalArgumentException e) {
            send(ex, 400, "application/json", "{\"error\":" + Json.str(e.getMessage()) + "}");
        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, "application/json", "{\"error\":" + Json.str("Database error: " + e.getMessage()) + "}");
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "application/json", "{\"error\":\"Server error\"}");
        }
    }

    private void api(HttpExchange ex, String method, String path) throws IOException, SQLException {
        String[] parts = path.split("/");   // "", "api", "donors", id, action
        if (parts.length < 3) { notFound(ex); return; }

        if (parts[2].equals("stats") && method.equals("GET")) {
            Map<String, Integer> counts = directory.availableCounts();
            StringBuilder sb = new StringBuilder("{\"total\":" + directory.total() + ",\"availableByGroup\":{");
            int i = 0, availTotal = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append(Json.str(e.getKey())).append(':').append(e.getValue());
                availTotal += e.getValue();
            }
            sb.append("},\"availableTotal\":").append(availTotal).append('}');
            send(ex, 200, "application/json", sb.toString());
            return;
        }
        if (!parts[2].equals("donors")) { notFound(ex); return; }

        if (parts.length == 3) {
            if (method.equals("GET")) {
                Map<String, String> q = parseForm(ex.getRequestURI().getRawQuery());
                List<Donor> list = directory.search(q.get("q"), q.get("bloodGroup"), q.get("city"),
                        "true".equals(q.get("availableOnly")));
                send(ex, 200, "application/json", toJson(list));
            } else if (method.equals("POST")) {
                Donor d = fromForm(parseForm(body(ex)), 0);
                send(ex, 201, "application/json", directory.add(d).toJson());
            } else notFound(ex);
            return;
        }

        int id;
        try { id = Integer.parseInt(parts[3]); } catch (NumberFormatException e) { notFound(ex); return; }

        if (parts.length == 4 && method.equals("PUT")) {
            Donor d = fromForm(parseForm(body(ex)), id);
            if (!directory.update(d)) { notFound(ex); return; }
            send(ex, 200, "application/json", d.toJson());
        } else if (parts.length == 4 && method.equals("DELETE")) {
            if (!directory.delete(id)) { notFound(ex); return; }
            send(ex, 200, "application/json", "{\"ok\":true}");
        } else if (parts.length == 5 && parts[4].equals("availability") && method.equals("POST")) {
            boolean avail = "true".equals(parseForm(body(ex)).get("available"));
            if (!directory.setAvailability(id, avail)) { notFound(ex); return; }
            send(ex, 200, "application/json", "{\"ok\":true}");
        } else notFound(ex);
    }

    private Donor fromForm(Map<String, String> f, int id) {
        return new Donor(id, f.get("donorName"), f.get("bloodGroup"), f.get("city"),
                "true".equals(f.get("available")), f.get("phoneNumber"));
    }

    private String toJson(List<Donor> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i).toJson());
        }
        return sb.append(']').toString();
    }

    private void staticFile(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path file = webDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webDir) || !Files.isRegularFile(file)) { notFound(ex); return; }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "application/javascript; charset=utf-8"
                : name.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(file));
    }

    private static String body(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    private void notFound(HttpExchange ex) throws IOException {
        send(ex, 404, "application/json", "{\"error\":\"Not found\"}");
    }

    private void send(HttpExchange ex, int code, String type, String body) throws IOException {
        send(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
