/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks download sizes within some context. This is intended to allow multiple separate downloads
 * to be reported in aggregate to the user.
 */
public class DownloadTrackingScope implements Context.Scope<Context<?>> {

  private final AtomicInteger totalFiles = new AtomicInteger(0);
  private final AtomicLong totalBytes = new AtomicLong(0L);

  public void addDownloads(int fileCount, long bytes) {
    totalFiles.addAndGet(fileCount);
    totalBytes.addAndGet(bytes);
  }

  public int getFileCount() {
    return totalFiles.get();
  }

  public long getTotalBytes() {
    return totalBytes.get();
  }

  @Override
  public void onScopeBegin(Context<?> context) {}

  @Override
  public void onScopeEnd(Context<?> context) {}
}
