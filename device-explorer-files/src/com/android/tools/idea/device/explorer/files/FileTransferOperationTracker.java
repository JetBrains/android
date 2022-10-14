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

import com.android.tools.adtui.util.HumanReadableUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link LongRunningOperationTracker} class that tracks progress of a file transfer
 * and reports progress to a {@link DeviceFileExplorerView} progress panel.
 *
 * <p>When the file transfer is done, the {@link #getSummary()} method returns a
 * {@link FileTransferSummary} instance containing various counters and optionally
 * problems related to the file transfer activity.
 */
public class FileTransferOperationTracker extends LongRunningOperationTracker {
  private static final int MAX_PATH_DISPLAY_LENGTH = 50;
  @NotNull private final FileTransferSummary mySummary;
  private long myFinishedWorkUnits;
  private long myTotalWorkUnits;
  private int myCurrentFileCount;
  private int myTotalFileCount;
  @SuppressWarnings("unused") private int myCurrentDirectoryCount;
  @SuppressWarnings("unused") private int myTotalDirectoryCount;

  public FileTransferOperationTracker(@NotNull DeviceFileExplorerView view, boolean backgroundable) {
    super(view, backgroundable);
    mySummary = new FileTransferSummary();
  }

  @NotNull
  public FileTransferSummary getSummary() {
    return mySummary;
  }

  @Override
  public void stop() {
    super.stop();
    mySummary.setDurationMillis(getDurationMillis());
  }

  public void addProblem(@NotNull Throwable error) {
    if (ExceptionUtil.getRootCause(error) instanceof CancellationException) {
      return;
    }
    mySummary.getProblems().add(error);
    setWarningColor();
  }

  public void showProgress() {
    if (myTotalWorkUnits == 0) {
      return;
    }

    setProgress((double)myFinishedWorkUnits / (double)myTotalWorkUnits);
  }

  public void processDirectory() {
    myFinishedWorkUnits += FileTransferWorkEstimator.getDirectoryWorkUnits();
    myCurrentDirectoryCount++;
    showProgress();
  }

  public void processFile() {
    myFinishedWorkUnits += FileTransferWorkEstimator.getFileWorkUnits();
    myCurrentFileCount++;
    showProgress();
  }

  public void processFileBytes(long byteCount) {
    myFinishedWorkUnits += FileTransferWorkEstimator.getFileContentsWorkUnits(byteCount);
    showProgress();
  }

  public void setUploadFileText(@NotNull VirtualFile file, long currentBytes, long totalBytes) {
    String text;
    if (myTotalFileCount > 1) {
      text = String.format(Locale.US, "Uploading file %,d of %,d: \"%s\"", myCurrentFileCount, myTotalFileCount,
                           StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), MAX_PATH_DISPLAY_LENGTH));
    }
    else {
      text = String.format("Uploading file \"%s\"",
                           StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), MAX_PATH_DISPLAY_LENGTH));
    }

    if (totalBytes > 0) {
      text += String.format(" (%s / %s)",
                            HumanReadableUtil.getHumanizedSize(currentBytes),
                            HumanReadableUtil.getHumanizedSize(totalBytes));
    }
    setStatusText(text);
  }

  public void setDownloadFileText(@NotNull String entryFullPath, long currentBytes, long totalBytes) {
    String text;
    if (myTotalFileCount > 1) {
      text = String.format(Locale.US, "Downloading file %,d of %,d: \"%s\"", myCurrentFileCount, myTotalFileCount,
                           StringUtil.shortenPathWithEllipsis(entryFullPath.toString(), MAX_PATH_DISPLAY_LENGTH));
    }
    else {
      text = String.format("Downloading file \"%s\"",
                           StringUtil.shortenPathWithEllipsis(entryFullPath.toString(), MAX_PATH_DISPLAY_LENGTH));
    }
    if (totalBytes > 0) {
      text += String.format(" (%s / %s)",
                            HumanReadableUtil.getHumanizedSize(currentBytes),
                            HumanReadableUtil.getHumanizedSize(totalBytes));
    }
    setStatusText(text);
  }

  public void addWorkEstimate(FileTransferWorkEstimate estimate) {
    myTotalFileCount += estimate.getFileCount();
    myTotalDirectoryCount += estimate.getDirectoryCount();
    myTotalWorkUnits += estimate.getWorkUnits();
  }

  public void setCalculatingText(int fileCount, int directoryCount) {
    // Note: We may be called for multiple directories or files, so we need
    // to add what we already know to the parameter value.
    fileCount += myTotalFileCount;
    directoryCount += myTotalDirectoryCount;

    String text = "Calculating...";
    if (fileCount > 0 || directoryCount > 0) {
      text += String.format(Locale.US, " %,d %s, %,d %s", fileCount, StringUtil.pluralize("file", fileCount), directoryCount,
                            StringUtil.pluralize("directory", directoryCount));
    }
    setStatusText(text);
  }
}
