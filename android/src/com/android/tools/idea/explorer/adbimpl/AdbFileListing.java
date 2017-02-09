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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class AdbFileListing {
  @NotNull private final IDevice myDevice;
  @NotNull private final Executor myExecutor;

  public AdbFileListing(@NotNull IDevice device, @NotNull Executor taskExecutor) {
    myDevice = device;
    myExecutor = taskExecutor;
  }

  @NotNull
  private FileListingService getFileListingService() {
    return myDevice.getFileListingService();
  }

  @NotNull
  public ListenableFuture<AdbFileListingEntry> getRoot() {
    FileListingService.FileEntry root = getFileListingService().getRoot();
    assert root != null;
    return Futures.immediateFuture(new AdbFileListingEntry(root));
  }

  @NotNull
  public ListenableFuture<List<AdbFileListingEntry>> getChildren(@NotNull AdbFileListingEntry parentEntry) {
    SettableFuture<List<AdbFileListingEntry>> futureResult = SettableFuture.create();
    myExecutor.execute(() -> {
      try {
        FileListingService.FileEntry[] children =
          getFileListingService().getChildrenSync(parentEntry.getAdbEntry());
        List<AdbFileListingEntry> result = Arrays.stream(children).map(AdbFileListingEntry::new).collect(Collectors.toList());
        futureResult.set(result);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });
    return futureResult;
  }
}