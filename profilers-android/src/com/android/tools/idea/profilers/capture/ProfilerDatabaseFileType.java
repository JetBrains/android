/*
 * Copyright (C) 2025 The Android Open Source Project
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a generic profiler database file.
 */
public class ProfilerDatabaseFileType extends AndroidProfilerCaptureFileType {
  public static final String EXTENSION = "asdb";
  private static final ProfilerDatabaseFileType INSTANCE = new ProfilerDatabaseFileType();

  @NotNull
  @Override
  public String getName() { return "ProfilerDatabase"; }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() { return "Profiler database"; }

  @NotNull
  @Override
  public String getDescription() { return "Profiler database file"; }

  @NotNull
  @Override
  public String getDefaultExtension() { return EXTENSION; }

  public static FileType getInstance() { return INSTANCE; }
}