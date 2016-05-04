/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

public class AaptInvoker {
  /**
   * Exit code when aapt exits successfully.
   */
  public static final int SUCCESS = 0;

  private static AaptInvoker ourInstance;
  private final Path myAapt;

  private AaptInvoker(@NotNull Path aapt) {
    myAapt = aapt;
  }

  @NotNull
  public ProcessOutput getXmlTree(@NotNull File apk, @NotNull String xmlResourcePath) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine(myAapt.toString(), "dump", "xmltree", apk.getAbsolutePath(), xmlResourcePath);
    return ExecUtil.execAndGetOutput(commandLine);
  }

  @NotNull
  public ProcessOutput dumpResources(@NotNull File apk) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine(myAapt.toString(), "dump", "resources", apk.getAbsolutePath());
    return ExecUtil.execAndGetOutput(commandLine);
  }

  @Nullable
  public static synchronized AaptInvoker getInstance() {
    if (ourInstance != null) {
      return ourInstance;
    }

    Path aapt = getPathToAapt();
    if (aapt == null) {
      return null;
    }

    ourInstance = new AaptInvoker(aapt);
    return ourInstance;
  }

  /**
   * @return the path to aapt from the latest version of build tools that is installed, null if there are no build tools
   */
  @Nullable
  private static Path getPathToAapt() {
    AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    BuildToolInfo latestBuildTool = sdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(AaptInvoker.class), true);
    if (latestBuildTool == null) {
      return null;
    }

    return latestBuildTool.getLocation().toPath().resolve(SdkConstants.FN_AAPT);
  }
}
