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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods related to IDEA JDKs.
 */
public class Jdks {
  @Nullable
  public static Sdk chooseOrCreateJavaSdk() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isApplicableJdk(sdk)) {
        return sdk;
      }
    }
    String jdkHomePath = getJdkHomePath();
    if (jdkHomePath != null) {
      return createJdk(jdkHomePath);
    }
    return null;
  }

  public static boolean isApplicableJdk(@NotNull Sdk jdk) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    // TODO this code is from IDEA. Do we really need JDK 1.5 and 1.7?
    return version == JavaSdkVersion.JDK_1_5 || version == JavaSdkVersion.JDK_1_6 || version == JavaSdkVersion.JDK_1_7;
  }

  @Nullable
  private static String getJdkHomePath() {
    Collection<String> jdkHomePaths = JavaSdk.getInstance().suggestHomePaths();
    if (jdkHomePaths.isEmpty()) {
      return null;
    }
    // Search for JDKs in both the suggest folder and all its sub folders.
    List<String> roots = Lists.newArrayList();
    for (String jdkHomePath : jdkHomePaths) {
      if (!Strings.isNullOrEmpty(jdkHomePath)) {
        roots.add(jdkHomePath);
        roots.addAll(getChildrenPaths(jdkHomePath));
      }
    }
    return getBestJdk(roots);
  }

  @NotNull
  private static List<String> getChildrenPaths(@NotNull String dirPath) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      return Collections.emptyList();
    }
    List<String> childrenPaths = Lists.newArrayList();
    File[] children = ObjectUtils.notNull(dir.listFiles(), ArrayUtil.EMPTY_FILE_ARRAY);
    for (File child : children) {
      boolean directory = child.isDirectory();
      if (directory) {
        childrenPaths.add(child.getAbsolutePath());
      }
    }
    return childrenPaths;
  }

  @Nullable
  private static String getBestJdk(@NotNull List<String> jdkRoots) {
    String bestJdk = null;
    for (String jdkRoot : jdkRoots) {
      if (JavaSdk.getInstance().isValidSdkHome(jdkRoot)) {
        if (bestJdk == null) {
          bestJdk = jdkRoot;
        } else {
          bestJdk = selectJdk(bestJdk, jdkRoot);
        }
      }
    }
    return bestJdk;
  }

  /**
   * Prioritize JDK 1.6, otherwise pick the one with the highest version.
   */
  @NotNull
  private static String selectJdk(@NotNull String jdk1, @NotNull String jdk2) {
    JavaSdkVersion v1 = getVersion(jdk1);
    if (JavaSdkVersion.JDK_1_6.equals(v1)) {
      return jdk1;
    }
    JavaSdkVersion v2 = getVersion(jdk2);
    if (JavaSdkVersion.JDK_1_6.equals(v2)) {
      return jdk2;
    }
    return v1.ordinal() > v2.ordinal() ? jdk1 : jdk2;
  }

  @NotNull
  private static JavaSdkVersion getVersion(@NotNull String jdk) {
    String version = JavaSdk.getInstance().getVersionString(jdk);
    if (version == null) {
      return JavaSdkVersion.JDK_1_0;
    }
    JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(version);
    return sdkVersion == null ? JavaSdkVersion.JDK_1_0 : sdkVersion;
  }

  @Nullable
  public static Sdk createJdk(@NotNull String jdkHomePath) {
    Sdk jdk = SdkConfigurationUtil.createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    if (jdk == null) {
      String msg = String.format("Unable to create JDK from path '%1$s'", jdkHomePath);
      Logger.getInstance(Jdks.class).error(msg);
    }
    return jdk;
  }
}
