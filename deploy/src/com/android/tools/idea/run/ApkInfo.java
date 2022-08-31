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

import com.android.sdklib.AndroidVersion;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
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
  private final List<ApkFileUnit> myFiles;
  /** The manifest package name for the APK (the app ID). */
  @NotNull
  private final String myApplicationId;
  /** A set of required "pm install" options to install this APK. */
  @NotNull
  private final Set<AppInstallOption> myRequiredInstallOptions;

  /**
   * An available "pm options".
   */
  public enum AppInstallOption {
    // Request to be installed with "all privileges" (-g).
    GRANT_ALL_PERMISSIONS(AndroidVersion.VersionCodes.M),
    // Request to be installed as queryable (--force-queryable).
    FORCE_QUERYABLE(AndroidVersion.VersionCodes.R);

    public final int minSupportedApiLevel;

    AppInstallOption(int minSupportedApiLevel) {
      this.minSupportedApiLevel = minSupportedApiLevel;
    }
  }

  public ApkInfo(@NotNull File file, @NotNull String applicationId) {
    this(file, applicationId, ImmutableSet.of());
  }

  public ApkInfo(@NotNull File file, @NotNull String applicationId, @NotNull Set<AppInstallOption> requiredInstallOptions) {
    myFiles = ImmutableList.of(new ApkFileUnit("", file));
    myApplicationId = applicationId;
    myRequiredInstallOptions = requiredInstallOptions;
  }

  public ApkInfo(@NotNull List<ApkFileUnit> files, @NotNull String applicationId) {
    Preconditions.checkArgument(!files.isEmpty());
    myFiles = files;
    myApplicationId = applicationId;
    myRequiredInstallOptions = ImmutableSet.of();
  }


  /**
   * The list of files to deploy for the given {@link #getApplicationId()}.
   */
  @NotNull
  public List<ApkFileUnit> getFiles() {
    return myFiles;
  }

  @NotNull
  public String getApplicationId() {
    return myApplicationId;
  }

  @NotNull
  public Set<AppInstallOption> getRequiredInstallOptions() {
    return myRequiredInstallOptions;
  }
}
