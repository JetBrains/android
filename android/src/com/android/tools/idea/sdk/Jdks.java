/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createAndAddSDK;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.serviceContainer.NonInjectable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods related to IDEA JDKs.
 */
public class Jdks {
  @NotNull private static final Logger LOG = Logger.getInstance(Jdks.class);

  @NonNls public static final String DOWNLOAD_JDK_8_URL =
    "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html";

  @NotNull private final IdeInfo myIdeInfo;

  @NotNull
  public static Jdks getInstance() {
    return ApplicationManager.getApplication().getService(Jdks.class);
  }

  public Jdks() {
    this(IdeInfo.getInstance());
  }

  @NonInjectable
  @VisibleForTesting
  public Jdks(@NotNull IdeInfo ideInfo) {
    myIdeInfo = ideInfo;
  }

  @Nullable
  public JavaSdkVersion findVersion(@NotNull Path jdkRoot) {
    return getVersion(jdkRoot.toString());
  }

  @Nullable
  private static JavaSdkVersion getVersion(String jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot);
    return isEmpty(version) ? null : JavaSdkVersion.fromVersionString(version);
  }

  @Nullable
  public Sdk createJdk(@NotNull String jdkHomePath) {
    Sdk jdk = ExternalSystemApiUtil.executeOnEdt(() -> {
      return createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    });
    if (jdk == null) {
      String msg = String.format("Unable to create JDK from path '%1$s'", jdkHomePath);
      LOG.error(msg);
    }
    return jdk;
  }

  @Nullable
  public Sdk createEmbeddedJdk() {
    if (myIdeInfo.isAndroidStudio() || myIdeInfo.isGameTools()) {
      Path path = EmbeddedDistributionPaths.getInstance().tryToGetEmbeddedJdkPath();
      if (path == null) {
        return null;
      }
      Sdk jdk = createJdk(path.toString());
      assert jdk != null;
      return jdk;
    }
    return null;
  }

  public static boolean isJdkRunnableOnPlatform(@NotNull Sdk jdk) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }

    if (!SystemInfo.isWindows || !SystemInfo.is32Bit) {
      // We only care about bitness compatibility on Windows. Elsewhere we just assume things are fine, because
      // nowadays virtually all Mac and Linux installations are 64 bits. No need to spend cycles on running 'java -version'
      return true;
    }

    JavaSdk javaSdk = (JavaSdk)jdk.getSdkType();
    String javaExecutablePath = javaSdk.getVMExecutablePath(jdk);
    return runAndCheckJVM(javaExecutablePath);
  }

  public static boolean isJdkRunnableOnPlatform(@NotNull String jdkHome) {
    return runAndCheckJVM(FileUtil.join(jdkHome, "bin", "java"));
  }

  private static boolean runAndCheckJVM(@NotNull String javaExecutablePath) {
    LOG.info("Checking java binary: " + javaExecutablePath);
    GeneralCommandLine commandLine = new GeneralCommandLine(javaExecutablePath);
    commandLine.addParameter("-version");
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      int exitCode = process.runProcess().getExitCode();
      return (exitCode == 0);
    }
    catch (ExecutionException e) {
      LOG.info("Could not invoke 'java -version'", e);
      return false;
    }
  }
}
