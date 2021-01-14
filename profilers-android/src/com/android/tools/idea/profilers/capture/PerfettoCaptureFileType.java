/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture;

import com.intellij.openapi.fileTypes.FileType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a perfetto trace file that can be imported into CPU profiler.
 */
public class PerfettoCaptureFileType extends AndroidProfilerCaptureFileType {

  public static final List<String> EXTENSIONS = Arrays.asList("pftrace", "perfetto-trace");

  private static final PerfettoCaptureFileType INSTANCE = new PerfettoCaptureFileType();

  @NotNull
  @Override
  public String getName() {
    return "PerfettoCapture";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Perfetto capture file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXTENSIONS.get(0);
  }

  public static FileType getInstance() {
    return INSTANCE;
  }
}
