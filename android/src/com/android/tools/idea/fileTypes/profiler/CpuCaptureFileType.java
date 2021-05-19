/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.fileTypes.profiler;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a CPU trace file that can be imported into CPU profiler.
 */
public class CpuCaptureFileType extends AndroidProfilerCaptureFileType {

  public static final String EXTENSION = "trace";

  private static final CpuCaptureFileType INSTANCE = new CpuCaptureFileType();

  private CpuCaptureFileType() {
  }

  @NotNull
  @Override
  public String getName() {
    return "AndroidProfilerCpuCapture";
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return "Android Profiler CPU capture";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Profiler CPU capture file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXTENSION;
  }

  public static FileType getInstance() {
    return INSTANCE;
  }
}
