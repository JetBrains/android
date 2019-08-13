/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profilers.IdeProfilerServices;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This implements the {@link StatusPanelModel} it is responsible for updating the parsing time, as well as showing notifications when
 * parsing a capture has failed.
 */
public class CpuCaptureHandler implements Updatable, StatusPanelModel {
  @NotNull private final CpuCaptureParser myCaptureParser;
  @NotNull private Range myParseRange = new Range();
  @NotNull private final IdeProfilerServices myServices;
  @NotNull private final String myConfiguration;
  @NotNull private final File myCaptureFile;
  private boolean myIsParsing = false;

  public CpuCaptureHandler(@NotNull IdeProfilerServices services,
                           @NotNull File captureFile,
                           @NotNull String configuration,
                           @Nullable String captureProcessNameHint) {
    myCaptureParser = new CpuCaptureParser(services);
    myCaptureParser.setProcessNameHint(captureProcessNameHint);
    myCaptureFile = captureFile;
    myServices = services;
    myConfiguration = configuration;
  }

  /**
   * {@link StatusPanelModel} interface override.
   * Returns the range that represents the amount of time parsing has taken so far.
   */
  @NotNull
  @Override
  public Range getRange() {
    return myParseRange;
  }

  /**
   * {@link StatusPanelModel} interface override.
   * Returns the name of the configuration. This is often the {@link ProfilingConfiguration} name.
   */
  @NotNull
  @Override
  public String getConfigurationText() {
    return myConfiguration;
  }

  /**
   * {@link StatusPanelModel} interface override.
   * Aborts the capture parser.
   */
  @Override
  public void abort() {
    myCaptureParser.abortParsing();
  }

  /**
   * {@link Updatable} interface override.
   * When parsing increases the max time of the parse range so parse range length is the total time to parse.
   */
  @Override
  public void update(long elapsedNs) {
    if (myIsParsing) {
      myParseRange.setMax(myParseRange.getMax() + elapsedNs);
    }
  }

  /**
   * Starts parsing a capture on failure to parse a notification will be triggered indicating the failure type.
   *
   * @param captureCompleted The callback will be called on both success and failure to parse. When a failure is encountered the callback
   *                         will be passed a null {@link CpuCapture}.
   */
  public void parse(Consumer<CpuCapture> captureCompleted) {
    myIsParsing = true;
    myParseRange.set(0, 0);
    CompletableFuture<CpuCapture> capture = myCaptureParser.parse(myCaptureFile);
    if (capture == null) {
      // User aborted the capture, or the model received an invalid file (e.g. from tests) canceled. Log and return early.
      myServices.showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
      captureCompleted.accept(null);
      return;
    }

    // Parsing is in progress. Handle it asynchronously and set the capture afterwards using the main executor.
    capture.handleAsync((parsedCapture, exception) -> {
      if (parsedCapture != null) {
        myServices.getFeatureTracker().trackImportTrace(parsedCapture.getType(), true);
      }
      else if (capture.isCancelled()) {
        myServices.showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_ABORTED);
      }
      else {
        myServices.showNotification(CpuProfilerNotifications.IMPORT_TRACE_PARSING_FAILURE);
      }
      myIsParsing = false;
      captureCompleted.accept(parsedCapture);
      return parsedCapture;
    }, myServices.getMainExecutor());
  }
}