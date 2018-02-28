/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * The list of files to install for a given application ID.
 * <ul>
 *   <li>For single APK applications, {@link #getFiles} contains a single {@link File}</li>
 *   <li>For split APK applications (e.g. app + dynamic features), {@link #getFiles} contains the corresponding list of APK files</li>
 *   <li>For Instant App applications, {@link #getFiles} contains a single {@link File} which points to a zip file containing
 *   the app and its optional features.</li>
 * </ul>
 */
public final class ApkInfo {
  /** The APK file(s). Contains at least one element. */
  @NotNull
  private final List<File> myFiles;
  /** The manifest package name for the APK (the app ID). */
  @NotNull
  private final String myApplicationId;

  public ApkInfo(@NotNull File file, @NotNull String applicationId) {
    myFiles = ImmutableList.of(file);
    myApplicationId = applicationId;
  }

  public ApkInfo(@NotNull List<File> files, @NotNull String applicationId) {
    Preconditions.checkArgument(!files.isEmpty());
    myFiles = files;
    myApplicationId = applicationId;
  }

  /**
   * Shortcut for {@link #getFiles() getFiles().get(0)}, used by callers that don't have to handle
   * dynamic apps.
   */
  @NotNull
  public File getFile() {
    Preconditions.checkArgument(myFiles.size() == 1);
    return myFiles.get(0);
  }

  /**
   * The list of files to deploy for the given {@link #getApplicationId()}.
   */
  @NotNull
  public List<File> getFiles() {
    return myFiles;
  }

  @NotNull
  public String getApplicationId() {
    return myApplicationId;
  }
}
