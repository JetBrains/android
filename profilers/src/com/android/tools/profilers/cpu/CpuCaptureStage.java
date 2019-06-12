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

import com.android.tools.profiler.proto.Transport;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * This class holds the models and capture data for the {@link com.android.tools.profilers.cpu.CpuCaptureStageView}.
 * This stage is set when a capture is selected from the {@link CpuProfilerStage}, or when a capture is imported.
 */
public class CpuCaptureStage extends Stage {

  /**
   * Helper function to save trace data to disk. The file is put in to the users temp directory with the format cpu_trace_[traceid].trace.
   * If the file exists FileUtil will append numbers to the end making it unique.
   */
  @NotNull
  public static File saveCapture(long traceId, ByteString data) {
    try {
      File trace = FileUtil.createTempFile(String.format(Locale.US, "cpu_trace_%d", traceId), ".trace", true);
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(data.toByteArray());
      }
      return trace;
    }
    catch (IOException io) {
      throw new IllegalStateException("Unable to save trace to disk");
    }
  }

  private final File myCaptureFile;

  public CpuCaptureStage(@NotNull StudioProfilers profilers, long traceId) {
    super(profilers);
    myCaptureFile = getAndSaveCapture(traceId);
  }

  public CpuCaptureStage(@NotNull StudioProfilers profilers, @NotNull File captureFile) {
    super(profilers);
    myCaptureFile = captureFile;
  }

  private File getAndSaveCapture(long traceId) {
    Transport.BytesRequest traceRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(getStudioProfilers().getSession().getStreamId())
      .setId(String.valueOf(traceId))
      .build();
    Transport.BytesResponse traceResponse = getStudioProfilers().getClient().getTransportClient().getBytes(traceRequest);
    if (!traceResponse.getContents().isEmpty()) {
      return saveCapture(traceId, traceResponse.getContents());
    }
    return null;
  }

  @Override
  public void enter() {

  }


  @Override
  public void exit() {

  }
}
