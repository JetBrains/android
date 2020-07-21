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
package com.android.tools.idea.transport;

import com.android.tools.idea.util.StudioPathManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import java.io.File;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a file on the host (where Studio is running) that will be copied to the device.
 */
public final class DeployableFile {
  /**
   * Name of the file on the host.
   */
  @NotNull private final String myFileName;

  /**
   * The release path containing the file on the host.
   */
  @NotNull private final String myReleaseDir;

  /**
   * The development path containing the file on the host.
   */
  @NotNull private final String myDevDir;

  /**
   * Format of the filename on the device if the file depends on ABI, e.g "simpleperf_%s".
   * It is null, if the file doesn't dependent on ABI on the device.
   */
  @Nullable private final String myOnDeviceAbiFileNameFormat;

  /**
   * Whether the file is executable.
   */
  private final boolean myExecutable;

  /**
   * Whether Android Studio is running from sources or is a release build.
   */
  @NotNull private final boolean myIsRunningFromSources;

  /**
   * The home path of Android Studio for release builds.
   */
  @NotNull private final String myHomePath;

  /**
   * The sources root directory for dev builds.
   */
  @NotNull private final String mySourcesRoot;

  private DeployableFile(@NotNull Builder builder) {
    myFileName = builder.myFileName;
    myReleaseDir = builder.myReleaseDir;
    myDevDir = builder.myDevDir;
    myOnDeviceAbiFileNameFormat = builder.myOnDeviceAbiFileNameFormat;
    myIsRunningFromSources = builder.myIsRunningFromSources;
    myHomePath = builder.myHomePath;
    mySourcesRoot = builder.mySourcesRoot;
    myExecutable = builder.myExecutable;
  }

  @NotNull
  public String getFileName() {
    return myFileName;
  }

  public boolean isExecutable() {
    return myExecutable;
  }

  public boolean isAbiDependent() {
    return myOnDeviceAbiFileNameFormat != null;
  }

  @Nullable
  public String getOnDeviceAbiFileNameFormat() {
    return myOnDeviceAbiFileNameFormat;
  }

  @NotNull
  public File getDir() {
    if (myIsRunningFromSources) {
      // Development mode
      return new File(mySourcesRoot, myDevDir);
    } else {
      return new File(myHomePath, myReleaseDir);
    }
  }

  public static class Builder {
    @NotNull private final String myFileName;
    @NotNull private String myReleaseDir = Constants.PERFA_RELEASE_DIR;
    // TODO b/122597221 refactor general agent code to be outside of profiler-specific directory.
    @NotNull private String myDevDir = Constants.PERFA_DEV_DIR;
    @Nullable private String myOnDeviceAbiFileNameFormat;

    @NotNull private boolean myIsRunningFromSources = StudioPathManager.isRunningFromSources();
    @NotNull private String myHomePath = PathManager.getHomePath();
    @NotNull private String mySourcesRoot = StudioPathManager.getSourcesRoot();

    private boolean myExecutable = false;

    public Builder(@NotNull String fileName) {
      myFileName = fileName;
    }

    @NotNull
    public Builder setReleaseDir(@NotNull String releaseDir) {
      myReleaseDir = releaseDir;
      return this;
    }

    @NotNull
    public Builder setDevDir(@NotNull String devDir) {
      myDevDir = devDir;
      return this;
    }

    @NotNull
    public Builder setExecutable(boolean executable) {
      myExecutable = executable;
      return this;
    }

    @NotNull
    public Builder setOnDeviceAbiFileNameFormat(@NotNull String format) {
      myOnDeviceAbiFileNameFormat = format;
      return this;
    }

    @VisibleForTesting
    @NotNull
    public Builder setIsRunningFromSources(boolean isRunningFromSources) {
      myIsRunningFromSources = isRunningFromSources;
      return this;
    }

    @VisibleForTesting
    @NotNull
    public Builder setHomePath(String homePath) {
      myHomePath = homePath;
      return this;
    }

    @VisibleForTesting
    @NotNull
    public Builder setSourcesRoot(String sourcesRoot) {
      mySourcesRoot = sourcesRoot;
      return this;
    }

    @NotNull
    public DeployableFile build() {
      return new DeployableFile(this);
    }
  }
}
