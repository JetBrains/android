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
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientRPC;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

public class GapisConnection implements Closeable {
  private final GapisProcess myParent;
  private final Socket myServerSocket;
  private final GapisFeatures myFeatures = new GapisFeatures();

  public GapisConnection(GapisProcess parent, Socket serverSocket) {
    myParent = parent;
    myServerSocket = serverSocket;
  }

  public boolean isConnected() {
    return myServerSocket != null && myServerSocket.isConnected();
  }

  public GapisFeatures getFeatures() {
    return myFeatures;
  }

  public ServiceClient createServiceClient(ListeningExecutorService executor) throws IOException {
    if (!isConnected()) {
      throw new IOException("Not connected");
    }
    return new ServiceClientRPC(executor,
                                myServerSocket.getInputStream(),
                                myServerSocket.getOutputStream(),
                                1024,
                                myParent.getVersion());
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
        myParent.onClose(this);
      }
    }
  }
}
