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
    // JDK 7 not really needed, added it to be more consistent with IDEA's way of getting JDK.
    return chooseOrCreateJavaSdk(JavaSdkVersion.JDK_1_6, JavaSdkVersion.JDK_1_7);
  }

  @Nullable
  public static Sdk chooseOrCreateJavaSdk(@NotNull JavaSdkVersion... jdkVersions) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isApplicableJdk(sdk, jdkVersions)) {
        return sdk;
      }
    }
    String jdkHomePath = getJdkHomePath(jdkVersions);
    if (jdkHomePath != null) {
      return createJdk(jdkHomePath);
    }
    return null;
  }

  public static boolean isApplicableJdk(@NotNull Sdk jdk) {
    // TODO this code is from IDEA. Do we really need JDK 1.5 and 1.7?
    return isApplicableJdk(jdk, JavaSdkVersion.JDK_1_5, JavaSdkVersion.JDK_1_6, JavaSdkVersion.JDK_1_7);
  }

  public static boolean isApplicableJdk(@NotNull Sdk jdk, @NotNull JavaSdkVersion... jdkVersions) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version != null) {
      for (JavaSdkVersion v : jdkVersions) {
        if (!version.isAtLeast(v)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static String getJdkHomePath(@NotNull JavaSdkVersion[] jdkVersions) {
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
    return getBestJdk(roots, jdkVersions);
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
  private static String getBestJdk(@NotNull List<String> jdkRoots, @NotNull JavaSdkVersion[] jdkVersions) {
    String bestJdk = null;
    for (String jdkRoot : jdkRoots) {
      if (JavaSdk.getInstance().isValidSdkHome(jdkRoot)) {
        if (bestJdk == null && hasMatchingVersion(jdkRoot, jdkVersions)) {
          bestJdk = jdkRoot;
        }
        else if (bestJdk != null) {
          bestJdk = selectJdk(bestJdk, jdkRoot, jdkVersions);
        }
      }
    }
    return bestJdk;
  }

  @Nullable
  private static String selectJdk(@NotNull String jdk1, @NotNull String jdk2, @NotNull JavaSdkVersion[] jdkVersions) {
    if (hasMatchingVersion(jdk1, jdkVersions)) {
      return jdk1;
    }
    if (hasMatchingVersion(jdk2, jdkVersions)) {
      return jdk2;
    }
    return null;
  }

  private static boolean hasMatchingVersion(@NotNull String jdkRoot, @NotNull JavaSdkVersion[] jdkVersions) {
    JavaSdkVersion version = getVersion(jdkRoot);
    for (JavaSdkVersion v : jdkVersions) {
      if (v.equals(version)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static JavaSdkVersion getVersion(@NotNull String jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot);
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
