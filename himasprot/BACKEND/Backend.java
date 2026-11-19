import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class Backend {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/process-name", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

                // Parse query parameters manually
                String query = exchange.getRequestURI().getQuery();
                String name = "Guest";
                String age = "N/A";
                String weight = "N/A";
                String riskFactor = "N/A";

                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1) {
                            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                            if (pair[0].equals("name")) name = value;
                            else if (pair[0].equals("age")) age = value;
                            else if (pair[0].equals("weight")) weight = value;
                            else if (pair[0].equals("riskFactor")) riskFactor = value;
                        }
                    }
                }

                // Print all collected information to the VS Code terminal
                System.out.println("--- New Assessment Received ---");
                System.out.println("Name: " + name);
                System.out.println("Age: " + age);
                System.out.println("Weight: " + weight);
                System.out.println("Risk Factor: " + riskFactor);

                // Send success response back (staying on page)
                String response = "Success";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Java Backend Server is running on http://localhost:8080");
    }
}