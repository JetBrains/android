/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.gapi;

import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientGRPC;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientRPC;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelProvider;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public abstract class GapisConnection implements Closeable {
  public static final GapisConnection NOT_CONNECTED = new GapisConnection(null) {
    @Override
    public boolean isConnected() {
      return false;
    }

    @Override
    public void setAuth(String authToken) throws IOException {
    }

    @Override
    public ServiceClient createServiceClient(ListeningExecutorService executor) throws IOException {
      throw new IOException("Not connected");
    }

    @Override
    public void close() {
    }
  };

  protected final GapisProcess myParent;
  private final GapisFeatures myFeatures = new GapisFeatures();

  public GapisConnection(GapisProcess parent) {
    myParent = parent;
  }

  @Override
  public void close() {
    myParent.onClose(this);
  }

  public GapisFeatures getFeatures() {
    return myFeatures;
  }

  public abstract boolean isConnected();

  /**
   * Records (but may send immediately) the auth-token to the GAPIS instance requiring authentication.
   * Must be called before any calls to {@link #createServiceClient}.
   */
  public abstract void setAuth(String authToken) throws IOException;

  public abstract ServiceClient createServiceClient(ListeningExecutorService executor) throws IOException;

  public static class RpcGapisConnection extends GapisConnection {
    @NotNull private static final byte AUTH_HEADER[] = new byte[]{'A', 'U', 'T', 'H'};

    private final Socket myServerSocket;

    public RpcGapisConnection(GapisProcess parent, Socket serverSocket) {
      super(parent);
      myServerSocket = serverSocket;
    }

    @Override
    public boolean isConnected() {
      return myServerSocket != null && myServerSocket.isConnected();
    }


    @Override
    public ServiceClient createServiceClient(ListeningExecutorService executor) throws IOException {
      if (!isConnected()) {
        throw new IOException("Not connected");
      }
      return new ServiceClientRPC(executor,
                                  myServerSocket.getInputStream(),
                                  myServerSocket.getOutputStream(),
                                  1024,
                                  myParent.getVersion().major);
    }

    @Override
    public void setAuth(String authToken) throws IOException {
      OutputStream s = myServerSocket.getOutputStream();
      s.write(AUTH_HEADER);
      s.write(authToken.getBytes("UTF-8"));
      s.write(new byte[]{ 0 });
    }

    @Override
    public void close() {
      synchronized (this) {
        if (isConnected()) {
          try {
            myServerSocket.close();
          }
          catch (IOException e) {
          }
        }
        super.close();
      }
    }
  }

  public static class GRpcGapisConnection extends GapisConnection {
    private final ManagedChannel myChannel;
    private String myAuthToken = "";

    public GRpcGapisConnection(GapisProcess parent, String host, int port) {
      super(parent);
      // Us OkHTTP as netty deadlocks a lot with the go server.
      // TODO: figure out what exactly is causing netty to deadlock.
      myChannel = new OkHttpChannelProvider().builderForAddress(host, port)
        .usePlaintext(true)
        .build();
    }

    @Override
    public boolean isConnected() {
      return !myChannel.isShutdown();
    }

    @Override
    public void setAuth(String authToken) throws IOException {
      myAuthToken = authToken;
    }

    @Override
    public ServiceClient createServiceClient(ListeningExecutorService executor) throws IOException {
      return new ServiceClientGRPC(myChannel, myAuthToken);
    }

    @Override
    public void close() {
      myChannel.shutdown();
      super.close();
    }
  }
}
