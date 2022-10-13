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
package com.android.tools.idea.device.explorer.files;

import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileEntry;
import com.android.tools.idea.file.explorer.toolwindow.ui.TreeUtil;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link DefaultMutableTreeNode} model for a {@link DeviceFileEntry}
 */
public class DeviceFileEntryNode extends DefaultMutableTreeNode {
  private @NotNull DeviceFileEntry myEntry;
  private boolean myIsSymbolicLinkToDirectory;
  private boolean myLoaded;
  private boolean myDownloading;
  private boolean myUploading;
  private int myTransferringTick;
  private long myCurrentTransferredBytes;
  private long myTotalTransferredBytes;

  @Nullable
  public static DeviceFileEntryNode fromNode(@Nullable Object value) {
    if (!(value instanceof DeviceFileEntryNode)) {
      return null;
    }

    return (DeviceFileEntryNode)value;
  }

  @Override
  @NotNull
  public String toString() {
    return myEntry.getName();
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

  public boolean isSymbolicLinkToDirectory() {
    return myIsSymbolicLinkToDirectory;
  }

  public void setSymbolicLinkToDirectory(boolean value) {
    myIsSymbolicLinkToDirectory = value;
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

  public void setEntry(@NotNull DeviceFileEntry entry) {
    myEntry = entry;
  }

  public boolean isDownloading() {
    return myDownloading;
  }

  public boolean isUploading() {
    return myUploading;
  }

  public boolean isTransferring() {
    return isDownloading() || isUploading();
  }

  private void clearTransferInfo() {
    myUploading = false;
    myDownloading = false;
    myTransferringTick = 0;
    myCurrentTransferredBytes = 0;
    myTotalTransferredBytes = 0;
  }

  public void setDownloading(boolean downloading) {
    clearTransferInfo();
    myDownloading = downloading;
  }

  public void setUploading(boolean uploading) {
    clearTransferInfo();
    myUploading = uploading;
  }

  public int getTransferringTick() {
    return myTransferringTick;
  }

  public void incTransferringTick() {
    myTransferringTick++;
  }

  public void setTransferProgress(long currentBytes, long totalBytes) {
    myCurrentTransferredBytes = currentBytes;
    myTotalTransferredBytes = totalBytes;
  }

  public long getCurrentTransferredBytes() {
    return myCurrentTransferredBytes;
  }

  public long getTotalTransferredBytes() {
    return myTotalTransferredBytes;
  }

  @NotNull
  public List<DeviceFileEntryNode> getChildEntryNodes() {
    return TreeUtil.getChildren(this)
      .map(DeviceFileEntryNode::fromNode)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Nullable
  public DeviceFileEntryNode findChildEntry(@NotNull String name) {
    return getChildEntryNodes().stream()
      .filter(x -> Objects.equals(x.getEntry().getName(), name))
      .findFirst()
      .orElse(null);
  }
}
