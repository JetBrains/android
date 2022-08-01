/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.asdriver.tests;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A file server originally created to serve files for updating Android Studio.
 */
public class FileServer implements AutoCloseable {

  private HttpServer server;

  /**
   * The files that this server is tracking. The keys represent the path that an HTTP request would
   * fetch, e.g. "/updates.xml".
   */
  private final Map<String, Path> fileMap;

  private final Map<String, List<URI>> requestHistory;

  public FileServer() {
    fileMap = new HashMap<>();
    requestHistory = new HashMap<>();
  }

  public void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(null), 0), 0);
    server.createContext("/", new HandleHttpRequest(this));

    // Start the FileServer on a separate thread from the rest of the framework
    // so that it can respond while debugging the framework itself.
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();

    System.out.println("FileServer listening on port " + server.getAddress().getPort());
  }

  public List<URI> getRequestHistoryForPath(String httpPath) {
    return requestHistory.get(httpPath);
  }

  public String getOrigin() {
    InetSocketAddress address = server.getAddress();
    return String.format("http://%s:%d", address.getHostName(), address.getPort());
  }

  @Override
  public void close() {
    server.stop(3);
  }

  /**
   * Registers a file to be served by this server.
   */
  public void registerFile(String httpPath, Path file) {
    System.out.printf("Registered \"%s\" to point to %s%n", httpPath, file.getFileName());
    fileMap.put(httpPath, file);
  }

  static class HandleHttpRequest implements HttpHandler {
    private final FileServer fileServer;

    public HandleHttpRequest(FileServer fileServer) {
      this.fileServer = fileServer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      System.out.println("FileServer request received for " + path);
      addHistory(path, exchange.getRequestURI());
      Path path1 = fileServer.fileMap.get(path);
      if (path1 == null) {
        String response = "404 - file not found from test server";
        respondWithString(exchange, response, 404);
      }
      else {
        respondWithFile(exchange, path1);
      }
    }

    private void addHistory(String path, URI requestUri) {
      if (!fileServer.requestHistory.containsKey(path)) {
        fileServer.requestHistory.put(path, new ArrayList<>());
      }
      fileServer.requestHistory.get(path).add(requestUri);
    }

    /**
     * Produces a simple, string-based response to a request.
     */
    private void respondWithString(HttpExchange exchange, String response, int httpCode) throws IOException {
      Charset charset = StandardCharsets.UTF_8;
      byte[] bytes = response.getBytes(charset);
      exchange.sendResponseHeaders(httpCode, bytes.length);

      OutputStream os = exchange.getResponseBody();
      os.write(bytes);
      os.close();
    }

    /**
     * Serves a file.
     */
    private void respondWithFile(HttpExchange exchange, Path path) throws IOException {
      String fileName = path.getFileName().toString().toLowerCase();
      if (fileName.endsWith(".xml")) {
        Charset charset = StandardCharsets.UTF_8;
        String content = Files.readString(path, charset);
        byte[] bytes = content.getBytes(charset);

        exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=" + charset.name());
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
      }
      else if (fileName.endsWith(".json")) {
        byte[] bytes = Files.readAllBytes(path);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
      }
      else if (fileName.endsWith(".jar")) {
        byte[] bytes = Files.readAllBytes(path);

        exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
      }
      else {
        throw new IllegalArgumentException("Unrecognized file type: " + fileName);
      }
    }
  }
}
