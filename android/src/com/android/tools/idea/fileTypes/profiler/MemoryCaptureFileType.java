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
import org.jetbrains.annotations.NotNull;

/**
 * Represents a heap dump file that can be imported into memory profiler.
 */
public class MemoryCaptureFileType extends AndroidProfilerCaptureFileType {

  public static final String EXTENSION = "hprof";

  private static final MemoryCaptureFileType myInstance = new MemoryCaptureFileType();

  @NotNull
  @Override
  public String getName() {
    return "AndroidProfilerMemoryCapture";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Profiler Memory capture file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXTENSION;
  }

  public static FileType getInstance() {
    return myInstance;
  }
}
