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
package com.android.tools.idea.device.explorer.files.fs;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;

public interface FileTransferProgress {
  /**
   * Reports progress of a long file transfer operation, including the
   * number of bytes already transferred. If the total number of bytes
   * is unknown, the value of <code>totalBytes</code> is
   * <code>-1</code>.
   */
  @UiThread
  void progress(long currentBytes, long totalBytes);

  /**
   * Queries if the transfer should be cancelled.
   *
   * Warning: Unlike other callbacks, this method is invoked on the worker
   * thread running the transfer operation to prevent potential deadlocks.
   * The implementor is responsible for ensuring the operation
   * is thread safe. Typically, this can be achieved by setting a boolean
   * field in a thread and returning the field value as the implementation
   * of this method.
   */
  @WorkerThread
  boolean isCancelled();
}
