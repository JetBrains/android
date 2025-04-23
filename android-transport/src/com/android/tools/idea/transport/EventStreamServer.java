/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.ServerBuilder;
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.intellij.openapi.Disposable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class for support non-device rpc servers that can push events and bytes via the transport service pipeline. The server acts
 * as a separate stream that the datastore layer talks to. Consumers should be able to query events from these servers by referencing
 * their stream ids.
 * <p>
 * Usage example:
 * <pre>
 *   EventStreamServer server = new EventStreamServer("serverName");
 *   server.start();
 *
 *   server.getEventDeque().offer(event);
 *   server.getByteCacheMap().put(byteId, bytes);
 *   Common.Stream stream = TransportService.getInstance().registerStreamServer(server);
 *
 *   // event and bytes can now be queried using the result stream's id.
 *
 *   // Cleanup
 *   TransportService.getInstance().unregisterStreamServer(stream.getStreamId())
 * </pre>
 * <p>
 * Note - While the caller can call TransportService.getInstance().unregisterStreamServer() to manually stop the server, it can also be
 * auto-stopped along with the TransportService when the latter is disposed.
 */
public class EventStreamServer implements Disposable {

  @NotNull private final String myServerName;
  @NotNull private final BlockingDeque<Common.Event> myEventQueue = new LinkedBlockingDeque<Common.Event>();
  @NotNull private final Map<String, ByteString> myByteCache = new HashMap<>();

  @NotNull private final Object myServerLock = new Object();
  @GuardedBy("myServerLock") private Server myServer;
  @GuardedBy("myServerLock") private StreamTransportService myTransportService;

  public EventStreamServer(@NotNull String serverName) {
    myServerName = serverName;
  }

  @NotNull
  public String getServerName() {
    return myServerName;
  }

  @NotNull
  public BlockingDeque<Common.Event> getEventDeque() {
    return myEventQueue;
  }

  @NotNull
  public Map<String, ByteString> getByteCacheMap() {
    return myByteCache;
  }

  public void start() throws IOException {
    synchronized (myServerLock) {
      if (myServer != null) {
        throw new IllegalStateException(String.format("Server %s has already started", myServerName));
      }

      ServerBuilder builder = InProcessServerBuilder.forName(myServerName).directExecutor();
      myTransportService = new StreamTransportService();
      builder.addService((myTransportService));
      myServer = builder.build();
      myServer.start();
    }
  }

  public void stop() {
    dispose();
  }

  @Override
  public void dispose() {
    synchronized (myServerLock) {
      if (myServer == null || myServer.isShutdown()) {
        return;
      }

      myTransportService.shutDownAndWait();
      myServer.shutdownNow();
    }
  }

  private class StreamTransportService extends TransportServiceGrpc.TransportServiceImplBase {

    private Thread myEventStreamingThread;
    @Nullable private CountDownLatch myEventStreamingLatch;

    private void shutDownAndWait() {
      if (myEventStreamingThread != null && myEventStreamingLatch != null) {
        try {
          myEventStreamingThread.interrupt();
          myEventStreamingLatch.await();
        }
        catch (InterruptedException ignored) {
        }
      }
    }

    @Override
    public void getEvents(Transport.GetEventsRequest request, StreamObserver<Common.Event> responseObserver) {
      myEventStreamingLatch = new CountDownLatch(1);
      myEventStreamingThread = new Thread(() -> {
        // The loop keeps running if the queue is not emptied, to make sure we pipe through all the existing
        // events that are already in the queue.
        while (!Thread.currentThread().isInterrupted() || !myEventQueue.isEmpty()) {
          try {
            Common.Event event = myEventQueue.take();
            responseObserver.onNext(event);
          }
          catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        }

        responseObserver.onCompleted();
        myEventStreamingLatch.countDown();
      });
      myEventStreamingThread.start();
    }

    @Override
    public void getBytes(Transport.BytesRequest request, StreamObserver<Transport.BytesResponse> responseObserver) {
      if (myByteCache.containsKey(request.getId())) {
        responseObserver.onNext(Transport.BytesResponse.newBuilder().setContents(myByteCache.get(request.getId())).build());
        myByteCache.remove(request.getId());
      }
      else {
        responseObserver.onNext(Transport.BytesResponse.getDefaultInstance());
      }
      responseObserver.onCompleted();
    }
  }
}
