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
package com.android.tools.idea.explorer;

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A {@link DefaultMutableTreeNode} model for a {@link DeviceFileEntry}
 */
public class DeviceFileEntryNode extends DefaultMutableTreeNode {
  private @NotNull DeviceFileEntry myEntry;
  private boolean myLoaded;
  private boolean myDownloading;
  private int myDownloadingTick;
  private long myDownloadedBytes;
  private long myTotalDownloadBytes;
  private boolean myIsSymbolicLinkToDirectory;

  @Nullable
  public static DeviceFileEntryNode fromNode(@Nullable Object value) {
    if (!(value instanceof DeviceFileEntryNode)) {
      return null;
    }

    return (DeviceFileEntryNode)value;
  }

  public DeviceFileEntryNode(@NotNull DeviceFileEntry entry) {
    myEntry = entry;
  }

  public boolean isLoaded() {
    return myLoaded;
  }

  public void setLoaded(boolean loaded) {
    myLoaded = loaded;
  }

  @Override
  @NotNull
  public String toString() {
    return myEntry.getName();
  }

  @Override
  public boolean isLeaf() {
    return !isExpandable();
  }

  public boolean isExpandable() {
    return myEntry.isDirectory() || myIsSymbolicLinkToDirectory;
  }

  @NotNull
  public DeviceFileEntry getEntry() {
    return myEntry;
  }

  public boolean isDownloading() {
    return myDownloading;
  }

  public void setDownloading(boolean downloading) {
    myDownloading = downloading;
    myDownloadingTick = 0;
    myDownloadedBytes = 0;
    myTotalDownloadBytes = 0;
  }

  public boolean isSymbolicLinkToDirectory() {
    return myIsSymbolicLinkToDirectory;
  }

  public void setSymbolicLinkToDirectory(boolean value) {
    myIsSymbolicLinkToDirectory = value;
  }

  public int getDownloadingTick() {
    return myDownloadingTick;
  }

  public void incDownloadingTick() {
    myDownloadingTick++;
  }

  public void setDownloadProgress(long downloadedBytes, long totalDownloadBytes) {
    myDownloadedBytes = downloadedBytes;
    myTotalDownloadBytes = totalDownloadBytes;
  }

  public long getDownloadedBytes() {
    return myDownloadedBytes;
  }

  public long getTotalDownloadBytes() {
    return myTotalDownloadBytes;
  }
}
