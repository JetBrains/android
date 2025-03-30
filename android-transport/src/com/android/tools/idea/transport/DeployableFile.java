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
import com.android.tools.idea.downloads.AndroidProfilerDownloader;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.annotations.VisibleForTesting;
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
  @NotNull private final Supplier<Boolean> myIsRunningFromSourcesSupplier;

  /**
   * The home path of Android Studio for release builds.
   */
  @NotNull private final Supplier<String> myHomePathSupplier;

  /**
   * The sources root directory for dev builds.
   */
  @NotNull private final Supplier<String> mySourcesRootSupplier;

  private DeployableFile(@NotNull Builder builder) {
    myFileName = builder.myFileName;
    myReleaseDir = builder.myReleaseDir;
    myDevDir = builder.myDevDir;
    myOnDeviceAbiFileNameFormat = builder.myOnDeviceAbiFileNameFormat;
    myIsRunningFromSourcesSupplier = builder.myIsRunningFromSourcesSupplier;
    myHomePathSupplier = builder.myHomePathSupplier;
    mySourcesRootSupplier = builder.mySourcesRootSupplier;
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
  private File getDir(@NotNull String parent, @NotNull String child) {
    File childFile = new File(child);
    if (childFile.isAbsolute()) {
      return childFile;
    }
    else {
      return new File(parent, child);
    }
  }

  @NotNull
  public File getDir() {
    if (myIsRunningFromSourcesSupplier.get()) {
      // Development mode
      return getDir(mySourcesRootSupplier.get(), myDevDir);
    }
    else {
      // Prod mode
      return getDir(myHomePathSupplier.get(), myReleaseDir);
    }
  }

  public static class Builder {
    @NotNull private final String myFileName;
    @NotNull private String myReleaseDir = Constants.PERFA_RELEASE_DIR;
    // TODO b/122597221 refactor general agent code to be outside of profiler-specific directory.
    @NotNull private String myDevDir = Constants.PERFA_DEV_DIR;
    @Nullable private String myOnDeviceAbiFileNameFormat;

    @NotNull private Supplier<Boolean> myIsRunningFromSourcesSupplier = () -> {
      return IdeInfo.getInstance().isAndroidStudio() && StudioPathManager.isRunningFromSources(); // should not use AOSP debug paths in IDEA
    };

    @NotNull private Supplier<String> myHomePathSupplier = () -> {
      AndroidProfilerDownloader.getInstance().makeSureComponentIsInPlace();
      return AndroidProfilerDownloader.getInstance().getPluginDir().getAbsolutePath();
    };
    @NotNull private Supplier<String> mySourcesRootSupplier = myHomePathSupplier;

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
      myIsRunningFromSourcesSupplier = () -> isRunningFromSources;
      return this;
    }

    @VisibleForTesting
    @NotNull
    public Builder setHomePath(String homePath) {
      myHomePathSupplier = () -> homePath;
      return this;
    }

    @VisibleForTesting
    @NotNull
    public Builder setSourcesRoot(String sourcesRoot) {
      mySourcesRootSupplier = () -> sourcesRoot;
      return this;
    }

    @NotNull
    public DeployableFile build() {
      return new DeployableFile(this);
    }
  }
}
