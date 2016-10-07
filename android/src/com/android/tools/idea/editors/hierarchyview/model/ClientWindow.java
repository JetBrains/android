/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.hierarchyview.model;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.HandleViewDebug;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a root window.
 */
public class ClientWindow {

  public final String title;
  public final Client client;

  public ClientWindow(@NotNull String title, @NotNull Client client) {
    this.title = title;
    this.client = client;
  }

  /**
   * Returns the name for the window suitable for displaying on the UI.
   * Returns the class name if available otherwise returns the component package name.
   */
  @NotNull
  public String getDisplayName() {
    String appName = client.getClientData().getClientDescription();
    List<String> parts = Lists.newArrayList(title.split("/"));
    parts.remove("");
    parts.remove(appName);

    if (parts.isEmpty()) {
      return appName;
    }
    return parts.get(parts.size() > 2 ? 1 : 0);
  }

  /**
   * Byte array representing the view hierachy dump of the window.
   */
  @Nullable
  public byte[] loadWindowData(long timeout, TimeUnit unit) {
    CaptureByteArrayHandler handler = new CaptureByteArrayHandler(HandleViewDebug.CHUNK_VURT);
    try {
      HandleViewDebug.dumpViewHierarchy(
        client, title,
        false /* skipChildren */,
        true  /* includeProperties */,
        handler);
    } catch (IOException e) {
      return null;
    }

    return handler.getData(timeout, unit);
  }

  /**
   * Byte array representing image preview of the provided node.
   */
  @Nullable
  public byte[] loadViewImage(@NotNull ViewNode node, long timeout, TimeUnit unit) {
    CaptureByteArrayHandler handler = new CaptureByteArrayHandler(HandleViewDebug.CHUNK_VUOP);
    try {
      HandleViewDebug.captureView(client, title, node.toString(), handler);
    } catch (IOException e) {
      return null;
    }

    return handler.getData(timeout, unit);
  }

  /**
   * Lists all the active window for the current client.
   */
  @Nullable
  public static List<ClientWindow> getAll(@NotNull Client client, long timeout, @NotNull TimeUnit unit) {
    ClientData cd = client.getClientData();
    if (cd.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY)) {
      try {
        return new ListViewRootsHandler().getWindows(client, timeout, unit);
      }
      catch (IOException ignored) {
      }
    }
    return null;
  }

  private static class ListViewRootsHandler extends HandleViewDebug.ViewDumpHandler {

    private final List<String> myViewRoots = Lists.newCopyOnWriteArrayList();

    public ListViewRootsHandler() {
      super(HandleViewDebug.CHUNK_VULW);
    }

    @Override
    protected void handleViewDebugResult(ByteBuffer data) {
      int nWindows = data.getInt();

      for (int i = 0; i < nWindows; i++) {
        int len = data.getInt();
        myViewRoots.add(getString(data, len));
      }
    }

    @NotNull
    public List<ClientWindow> getWindows(@NotNull Client c, long timeout, TimeUnit unit) throws IOException {
      HandleViewDebug.listViewRoots(c, this);
      waitForResult(timeout, unit);
      List<ClientWindow> windows = Lists.newArrayList();
      for (String root : myViewRoots) {
        windows.add(new ClientWindow(root, c));
      }
      return windows;
    }
  }

  private static class CaptureByteArrayHandler extends HandleViewDebug.ViewDumpHandler {
    public CaptureByteArrayHandler(int type) {
      super(type);
    }

    private AtomicReference<byte[]> mData = new AtomicReference<byte[]>();

    @Override
    protected void handleViewDebugResult(ByteBuffer data) {
      byte[] b = new byte[data.remaining()];
      data.get(b);
      mData.set(b);

    }

    public byte[] getData(long timeout, TimeUnit unit) {
      waitForResult(timeout, unit);
      return mData.get();
    }
  }
}