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

import com.android.tools.idea.IdeInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import java.io.File;
import java.util.function.Supplier;

import org.jetbrains.android.download.AndroidProfilerDownloader;
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
   * The home path of Android Studio.
   */
  @NotNull private final Supplier<String> myHomePathSupplier;

  private DeployableFile(@NotNull Builder builder) {
    myFileName = builder.myFileName;
    myReleaseDir = builder.myReleaseDir;
    myDevDir = builder.myDevDir;
    myOnDeviceAbiFileNameFormat = builder.myOnDeviceAbiFileNameFormat;
    myHomePathSupplier = builder.myHomePathSupplier;
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
    File dir = new File(myHomePathSupplier.get(), myReleaseDir);
    if (dir.exists()) {
      return dir;
    }

    // Development mode
    return new File(myHomePathSupplier.get(), myDevDir);
  }

  public static class Builder {
    @NotNull private final String myFileName;
    @NotNull private String myReleaseDir = Constants.PERFA_RELEASE_DIR;
    // TODO b/122597221 refactor general agent code to be outside of profiler-specific directory.
    @NotNull private String myDevDir = getDevDir(Constants.PERFA_DEV_DIR);
    @Nullable private String myOnDeviceAbiFileNameFormat;
    @NotNull private Supplier<String> myHomePathSupplier = () -> {
      if (IdeInfo.getInstance().isAndroidStudio()){
        return PathManager.getHomePath();
      } else {
        AndroidProfilerDownloader.getInstance().makeSureComponentIsInPlace();
        return AndroidProfilerDownloader.getInstance().getPluginDir().getAbsolutePath();
      }
    };

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
    public Builder setHomePathSupplier(Supplier<String> pathSupplier) {
      myHomePathSupplier = pathSupplier;
      return this;
    }

    @NotNull
    public DeployableFile build() {
      return new DeployableFile(this);
    }
  }

  public static String getDevDir(String relativePathFromWorkspaceRoot) {
    return "../../" + relativePathFromWorkspaceRoot;
  }
}
