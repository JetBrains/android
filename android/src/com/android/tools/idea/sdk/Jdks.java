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

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.NonInjectable;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods related to IDEA JDKs.
 */
public class Jdks {
  @NotNull private static final Logger LOG = Logger.getInstance(Jdks.class);

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
  public JavaSdkVersion getVersion(@NotNull String jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot);
    return isEmpty(version) ? null : JavaSdkVersion.fromVersionString(version);
  }

  public @Nullable Sdk createAndAddJdk(@NotNull String jdkHomePath) {
    VirtualFile sdkHome = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(jdkHomePath));
    if (sdkHome == null) {
      LOG.error(String.format("Unable to create JDK from path '%1$s'", jdkHomePath));
      return null;
    }
    Sdk newSdk = SdkConfigurationUtil.setupSdk(
      ProjectJdkTable.getInstance().getAllJdks(), sdkHome, JavaSdk.getInstance(), true, null, null);
    if (newSdk != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> SdkConfigurationUtil.addSdk(newSdk));
    }
    return newSdk;
  }

  @Nullable
  public Sdk createEmbeddedJdk() {
    if (myIdeInfo.isAndroidStudio()) {
      Path path = EmbeddedDistributionPaths.getInstance().tryToGetEmbeddedJdkPath();
      if (path == null) {
        return null;
      }
      Sdk jdk = createAndAddJdk(path.toString());
      assert jdk != null;
      return jdk;
    }
    return null;
  }
}
