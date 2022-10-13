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

import com.android.tools.idea.file.explorer.toolwindow.FileTransferWorkEstimator;

/**
 * File transfer work estimate as computed by {@link FileTransferWorkEstimator}.
 */
public class FileTransferWorkEstimate {
  private int myFileCount;
  private int myDirectoryCount;
  private long myWorkUnits;

  /** The number of files to transfer */
  public int getFileCount() {
    return myFileCount;
  }

  /** The number of directories to transfer */
  public int getDirectoryCount() {
    return myDirectoryCount;
  }

  /** The estimated amount of work units to transfer all files and directories */
  public long getWorkUnits() {
    return myWorkUnits;
  }

  public void addFileCount(@SuppressWarnings("SameParameterValue") int count) {
    myFileCount += count;
  }

  public void addDirectoryCount(@SuppressWarnings("SameParameterValue") int count) {
    myDirectoryCount += count;
  }

  public void addWorkUnits(long workUnits) {
    myWorkUnits += workUnits;
  }
}
