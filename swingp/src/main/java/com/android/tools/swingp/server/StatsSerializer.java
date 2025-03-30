/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.swingp.server;

import com.android.tools.swingp.RenderStatsManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Stats poller and serializer.
 * This class effectively moves blocking queue out of the stats generators such that the generators can run as fast as possible.
 * There is also a busy-wait polling mechanism to allow for streaming reads from a web service.
 */
public class StatsSerializer {
  private static final String POLLING_THREAD_NAME = "Stats Monitor Collector";

  private static final long FPS = 60;
  private static final long POLLING_INTERVAL_NS = TimeUnit.SECONDS.toNanos(1) / FPS;

  private static final int SWING_MONITOR_SERVER_PORT = 61642;
  private static final int MAX_BATCH_SIZE = 10;

  @NotNull private final LinkedBlockingQueue<byte[]> mySerializedStats;
  @NotNull private final PollingSerializer myPollingSerializer;
  @NotNull private final HttpServer myHttpServer;

  public StatsSerializer() {
    mySerializedStats = new LinkedBlockingQueue<>();
    myPollingSerializer = new PollingSerializer(mySerializedStats);

    myHttpServer = ServerBootstrap
      .bootstrap()
      .setListenerPort(SWING_MONITOR_SERVER_PORT)
      .setSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
      .registerHandler("*", new HttpRequestHandler() {
        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
          response.setStatusCode(HttpStatus.SC_OK);
          response.setHeader("Access-Control-Allow-Origin", "*");
          response.setEntity(new ByteArrayEntity(getSerializedStats()));
        }
      })
      .create();
  }

  /**
   * @return true if the serializer started successfully, false otherwise.
   */
  public boolean start() {
    Thread pollingThread = new Thread(myPollingSerializer, POLLING_THREAD_NAME);
    pollingThread.start();

    try {
      myHttpServer.start();
    }
    catch (IOException e) {
      return false;
    }

    RenderStatsManager.setIsEnabled(true);
    return true;
  }

  public void stop() throws InterruptedException {
    RenderStatsManager.setIsEnabled(false);
    myPollingSerializer.stop();
    myHttpServer.shutdown(33, TimeUnit.MILLISECONDS);
  }

  /**
   * Encoding is a simple (int "size" field, followed by byte[size] bytes)+.
   * e.g.
   * --- start ---
   * int size0
   * byte[size0] payload0
   * int size1
   * byte[size1] payload1
   * ...
   * int sizeN
   * byte[sizeN] payloadN
   * --- end ---
   * Where N is at most MAX_BATCH_SIZE.
   *
   * @return the serialized byte array ready to be sent over the wire.
   */
  @NotNull
  private byte[] getSerializedStats() {
    try {
      int bufferSize;
      int statsCount;
      List<byte[]> stats = new ArrayList<>(MAX_BATCH_SIZE);
      // We always want to wait for the blocking queue to have at least a single item before
      // we return from the long poll. If it's empty, call "take" to block until some data is available.
      if (mySerializedStats.isEmpty()) {
        byte[] stat = mySerializedStats.take();
        stats.add(stat);
        statsCount = 1;
        bufferSize = 4 + stat.length;
      }
      else {
        mySerializedStats.drainTo(stats, MAX_BATCH_SIZE);
        statsCount = stats.size();
        bufferSize = stats.size() * 4;
        for (byte[] stat : stats) {
          bufferSize += stat.length;
        }
      }

      ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i < statsCount; i++) {
        buffer.putInt(stats.get(i).length);
        buffer.put(stats.get(i));
      }
      return buffer.array();
    }
    catch (InterruptedException e) {
      return new byte[0];
    }
  }

  private static class PollingSerializer implements Runnable {
    @NotNull private final CountDownLatch myStopLatch = new CountDownLatch(1);
    @NotNull private final CountDownLatch myStoppedLatch = new CountDownLatch(1);
    @NotNull private final LinkedBlockingQueue<byte[]> myResultQueue;

    private PollingSerializer(@NotNull LinkedBlockingQueue<byte[]> resultQueue) {
      myResultQueue = resultQueue;
    }

    @Override
    public void run() {
      try {
        while (myStopLatch.getCount() > 0) {
          long startTime = System.nanoTime();

          try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            JsonElement element = RenderStatsManager.getJson();
            if (element != JsonNull.INSTANCE) {
              try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(stream))) {
                JsonWriter jsonWriter = new JsonWriter(bufferedWriter);
                jsonWriter.setLenient(true);
                Streams.write(element, jsonWriter);
                jsonWriter.flush();
              }
              stream.flush();
              byte[] bytes = stream.toByteArray();
              if (bytes.length > 0) {
                myResultQueue.add(bytes);
              }
            }
          }
          catch (IOException ignored) {
          }

          long elapsedTimeNs = System.nanoTime() - startTime;
          if (elapsedTimeNs < POLLING_INTERVAL_NS) {
            try {
              Thread.sleep(TimeUnit.NANOSECONDS.toMillis(POLLING_INTERVAL_NS - elapsedTimeNs));
            }
            catch (InterruptedException ignored) {
            }
          }
        }
      }
      finally {
        myStoppedLatch.countDown();
      }
    }

    public void stop() throws InterruptedException {
      myStopLatch.countDown();
      myStoppedLatch.await();
    }
  }
}
