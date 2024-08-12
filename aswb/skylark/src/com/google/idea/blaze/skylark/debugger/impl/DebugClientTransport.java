/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.DebugEvent;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.DebugRequest;
import com.intellij.openapi.diagnostic.Logger;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

class DebugClientTransport implements Closeable {

  private static final Logger logger = Logger.getInstance(DebugClientTransport.class);

  /**
   * Leave enough time to (in the worst case) extract a blaze installation, then start up a local
   * blaze server.
   */
  private static final int CONNECTION_TIMEOUT_MILLIS = 30000;

  private static final int RETRY_DELAY_MILLIS = 200;
  private static final int RESPONSE_TIMEOUT_MILLIS = 30000;
  private static final int RESPONSE_RECHECK_TIME_MILLIS = 1000;

  private static final String LOCAL_HOST = "localhost";

  private final int port;
  private final SkylarkDebugProcess debugProcess;

  private final AtomicLong sequence = new AtomicLong(1);
  private final Map<Long, DebugEvent> responseQueue = new HashMap<>();

  @Nullable private Socket clientSocket;
  @Nullable private OutputStream requestStream;
  @Nullable private ListenableFuture<?> readTask;
  private volatile boolean isStopCalled = false;

  DebugClientTransport(SkylarkDebugProcess debugProcess, int port) {
    this.port = port;
    this.debugProcess = debugProcess;
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  boolean isConnected() {
    return clientSocket != null && !clientSocket.isClosed() && clientSocket.isConnected();
  }

  private boolean ignoreErrors() {
    return !isConnected() || !debugProcess.isProcessAlive();
  }

  @Override
  public void close() {
    isStopCalled = true;
    if (readTask != null) {
      readTask.cancel(true);
    }
    if (clientSocket == null) {
      return;
    }
    try {
      clientSocket.close();
    } catch (IOException e) {
      logger.info("Exception closing skylark debugger socket", e);
    }
  }

  /**
   * Connect to the debug server, retrying if necessary. Returns true if the connection was
   * successful.
   */
  boolean waitForConnection() {
    logger.info("Connecting to debug server");
    long startTime = System.currentTimeMillis();
    IOException connectionException = null;
    while (!isStopCalled && System.currentTimeMillis() - startTime < CONNECTION_TIMEOUT_MILLIS) {
      try {
        clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(LOCAL_HOST, port), CONNECTION_TIMEOUT_MILLIS);
        requestStream = clientSocket.getOutputStream();
        readTask = processEvents(clientSocket.getInputStream());
        logger.info("Connection established");
        return true;

      } catch (IOException e) {
        connectionException = e;
      }
      try {
        Thread.sleep(RETRY_DELAY_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    if (connectionException != null) {
      logger.warn("Couldn't connect to Skylark debugger", connectionException);
    }
    return false;
  }

  /**
   * Sends a {@link DebugRequest} to the server, and blocks waiting for a response. The sequence
   * number will be populated prior to sending the request.
   *
   * @return the {@link DebugEvent} response from the server, or null if no response was received
   *     within the timeout.
   */
  @Nullable
  DebugEvent sendRequest(DebugRequest.Builder builder) {
    long seq = sequence.getAndIncrement();
    DebugRequest request = builder.setSequenceNumber(seq).build();
    try {
      synchronized (requestStream) {
        request.writeDelimitedTo(requestStream);
        requestStream.flush();
      }
      return waitForResponse(seq);

    } catch (IOException e) {
      if (!ignoreErrors()) {
        logger.error("Error sending request to Skylark debugger", e);
      }
      return null;
    }
  }

  private ListenableFuture<?> processEvents(InputStream eventStream) {
    ListeningExecutorService executor =
        MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);
    return executor.submit(
        () -> {
          try {
            listenForEvents(eventStream);
          } catch (IOException e) {
            if (!ignoreErrors()) {
              logger.error("Malformed event proto", e);
            }
            close();
          }
        });
  }

  private void listenForEvents(InputStream eventStream) throws IOException {
    while (true) {
      DebugEvent event = DebugEvent.parseDelimitedFrom(eventStream);
      if (event.getSequenceNumber() == 0) {
        // sequence number is 0 iff it's not a response to a DebugRequest: handle it immediately
        debugProcess.handleEvent(event);
      } else {
        placeResponse(event.getSequenceNumber(), event);
      }
    }
  }

  private void placeResponse(long sequence, DebugEvent response) {
    synchronized (responseQueue) {
      responseQueue.put(sequence, response);
      responseQueue.notifyAll();
    }
  }

  /**
   * Wait for a response from the debug server. Returns null if no response was received, or this
   * thread was interrupted.
   */
  @Nullable
  private DebugEvent waitForResponse(long sequence) {
    DebugEvent response = null;
    long startTime = System.currentTimeMillis();
    synchronized (responseQueue) {
      while (response == null && shouldWaitForResponse(startTime)) {
        try {
          responseQueue.wait(RESPONSE_RECHECK_TIME_MILLIS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
        response = responseQueue.remove(sequence);
      }
    }
    return response;
  }

  private boolean shouldWaitForResponse(long startTime) {
    return clientSocket.isConnected()
        && !readTask.isDone()
        && System.currentTimeMillis() - startTime < RESPONSE_TIMEOUT_MILLIS;
  }
}
