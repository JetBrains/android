/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.mocks;

import com.android.tools.idea.util.FutureUtils;
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil;
import com.android.tools.idea.explorer.adbimpl.AdbShellCommandException;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService.OPERATION_TIMEOUT_MILLIS;

@SuppressWarnings({"SameParameterValue"})
public class MockDeviceFileEntry implements DeviceFileEntry {
  private final MockDeviceFileSystem myFileSystem;
  private final MockDeviceFileEntry myParent;
  private final List<MockDeviceFileEntry> myEntries = new ArrayList<>();
  private final String myName;
  private final boolean myIsDirectory;
  private final boolean myIsLink;
  private final String myLinkTarget;
  private long mySize;
  private boolean myIsSymbolicLinkToDirectory;
  private Throwable myGetEntriesError;
  private Throwable myDeleteError;
  private int myGetEntriesTimeoutMillis = OPERATION_TIMEOUT_MILLIS;

  @NotNull
  public static MockDeviceFileEntry createRoot(@NotNull MockDeviceFileSystem fileSystem) {
    return new MockDeviceFileEntry(fileSystem, null, "", true, false, null);
  }

  @NotNull
  public MockDeviceFileEntry addFile(@NotNull String name) throws AdbShellCommandException {
    assert myIsDirectory;
    throwIfEntryExists(name);
    return new MockDeviceFileEntry(myFileSystem, this, name, false, false, null);
  }

  @NotNull
  public MockDeviceFileEntry addFileLink(@NotNull String name, @NotNull String linkTarget) throws AdbShellCommandException {
    assert myIsDirectory;
    throwIfEntryExists(name);
    return new MockDeviceFileEntry(myFileSystem, this, name, false, true, linkTarget);
  }

  @NotNull
  public MockDeviceFileEntry addDirectory(@NotNull String name) throws AdbShellCommandException {
    assert myIsDirectory;
    throwIfEntryExists(name);
    return new MockDeviceFileEntry(myFileSystem, this, name, true, false, null);
  }

  private void removeEntry(MockDeviceFileEntry childEntry) {
    myEntries.remove(childEntry);
  }


  public MockDeviceFileEntry(@NotNull MockDeviceFileSystem fileSystem,
                             @Nullable MockDeviceFileEntry parent,
                             @NotNull String name,
                             boolean isDirectory,
                             boolean isLink,
                             @Nullable String linkTarget) {
    myFileSystem = fileSystem;
    myParent = parent;
    if (myParent != null) {
      myParent.myEntries.add(this);
    }
    myName = name;
    myIsDirectory = isDirectory;
    myIsLink = isLink;
    myLinkTarget = linkTarget;
  }

  private void throwIfEntryExists(@NotNull String name) throws AdbShellCommandException {
    if (myEntries.stream().anyMatch(x -> Objects.equals(x.getName(), name))) {
      throw new AdbShellCommandException("File already exists");
    }
  }

  @NotNull
  public List<MockDeviceFileEntry> getMockEntries() {
    return myEntries;
  }

  @NotNull
  @Override
  public DeviceFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Nullable
  @Override
  public DeviceFileEntry getParent() {
    return myParent;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFullPath() {
    if (myParent == null) {
      return myName;
    }
    return AdbPathUtil.resolve(myParent.getFullPath(), myName);
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    if (myGetEntriesError != null) {
      return FutureUtils.delayedError(myGetEntriesError, myGetEntriesTimeoutMillis);
    }
    return FutureUtils.delayedValue(myEntries.stream().collect(Collectors.toList()), myGetEntriesTimeoutMillis);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> delete() {
    if (myDeleteError != null) {
      return FutureUtils.delayedError(myDeleteError, OPERATION_TIMEOUT_MILLIS);
    }
    return FutureUtils.delayedOperation(() -> {
      myParent.removeEntry(this);
      return null;
    }, OPERATION_TIMEOUT_MILLIS);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull String fileName) {
    return FutureUtils.delayedOperation(() -> {
      addFile(fileName);
      return null;
    }, OPERATION_TIMEOUT_MILLIS);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull String directoryName) {
    return FutureUtils.delayedOperation(() -> {
      addDirectory(directoryName);
      return null;
    }, OPERATION_TIMEOUT_MILLIS);
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> isSymbolicLinkToDirectory() {
    return FutureUtils.delayedValue(myIsSymbolicLinkToDirectory, OPERATION_TIMEOUT_MILLIS);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull Path localPath, @NotNull FileTransferProgress progress) {
    return myFileSystem.downloadFile(this, localPath, progress);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath, @NotNull String fileName, @NotNull FileTransferProgress progress) {
    return myFileSystem.uploadFile(localPath, this, fileName, progress);
  }

  @NotNull
  @Override
  public Permissions getPermissions() {
    return () -> "rwxrwxrwx";
  }

  @NotNull
  @Override
  public DateTime getLastModifiedDate() {
    return () -> "";
  }

  @Override
  public long getSize() {
    return mySize;
  }

  public void setSize(long size) {
    mySize = size;
  }

  @Override
  public boolean isDirectory() {
    return myIsDirectory;
  }

  @Override
  public boolean isFile() {
    return !myIsDirectory;
  }

  @Override
  public boolean isSymbolicLink() {
    return myIsLink;
  }

  @Nullable
  @Override
  public String getSymbolicLinkTarget() {
    return myLinkTarget;
  }

  public void setGetEntriesError(@Nullable Throwable t) {
    myGetEntriesError = t;
  }

  public void setGetEntriesTimeoutMillis(int timeoutMillis) {
    myGetEntriesTimeoutMillis = timeoutMillis;
  }

  @SuppressWarnings("unused")
  public void setSymbolicLinkToDirectory(boolean symbolicLinkToDirectory) {
    myIsSymbolicLinkToDirectory = symbolicLinkToDirectory;
  }

  public void setDeleteError(@Nullable Throwable t) {
    myDeleteError = t;
  }
}

