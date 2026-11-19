package himas;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HeatServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/api/assess", new AssessHandler());
        server.setExecutor(null);
        System.out.println("HIMAS Java Backend running at http://localhost:" + port);
        server.start();
    }

    static class AssessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String query = params.getOrDefault("q", "Batangas City");
                
                // Default indices since UI elements were removed
                int activity = 0; 
                int place = 0;    
                int crowd = 0;    

                HeatAssessmentService.Result result = HeatAssessmentService.assess(query, activity, place, crowd);
                String jsonResponse = toJson(result);

                exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                os.close();

            } catch (Exception e) {
                String errJson = "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                exchange.sendResponseHeaders(400, errJson.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(errJson.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }

        private Map<String, String> queryToMap(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null) return result;
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
                } else {
                    result.put(entry[0], "");
                }
            }
            return result;
        }

        private String toJson(HeatAssessmentService.Result r) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"place\":").append(jsonStr(r.place)).append(",");
            sb.append("\"time\":").append(jsonStr(r.time)).append(",");
            sb.append("\"temp\":").append(r.temp).append(",");
            sb.append("\"humidity\":").append(r.humidity).append(",");
            sb.append("\"apparent\":").append(r.apparent).append(",");
            sb.append("\"heatIndex\":").append(r.heatIndex).append(",");
            sb.append("\"pagasaCategory\":").append(jsonStr(r.pagasaCategory)).append(",");
            sb.append("\"level\":").append(r.level).append(",");
            sb.append("\"levelName\":").append(jsonStr(r.levelName)).append(",");
            sb.append("\"headline\":").append(jsonStr(r.headline)).append(",");
            sb.append("\"hydration\":").append(jsonStr(r.hydration));
            sb.append("}");
            return sb.toString();
        }

        private String jsonStr(String val) {
            if (val == null) return "\"\"";
            return "\"" + val.replace("\"", "\\\"") + "\"";
        }
    }
}