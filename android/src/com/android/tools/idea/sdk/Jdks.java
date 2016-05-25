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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.getEmbeddedJdkPath;
import static com.android.tools.idea.sdk.IdeSdks.isUsingEmbeddedJdk;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createAndAddSDK;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static java.util.Collections.emptyList;

/**
 * Utility methods related to IDEA JDKs.
 */
public class Jdks {
  @NonNls public static final String DOWNLOAD_JDK_8_URL =
    "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html";

  private static final LanguageLevel DEFAULT_LANG_LEVEL = JDK_1_8;

  @Nullable
  public static Sdk chooseOrCreateJavaSdk() {
    return chooseOrCreateJavaSdk(null);
  }

  @Nullable
  public static Sdk chooseOrCreateJavaSdk(@Nullable LanguageLevel langLevel) {
    if (langLevel == null) {
      langLevel = DEFAULT_LANG_LEVEL;
    }
    if (isAndroidStudio() && !isUsingEmbeddedJdk()) {
      Sdk jdk = createJdk(getEmbeddedJdkPath().getPath());
      assert jdk != null && isApplicableJdk(jdk, langLevel);
      return jdk;
    }
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isApplicableJdk(sdk, langLevel)) {
        return sdk;
      }
    }
    String jdkHomePath = getJdkHomePath(langLevel);
    if (jdkHomePath != null) {
      return createJdk(jdkHomePath);
    }
    return null;
  }

  public static boolean isApplicableJdk(@NotNull Sdk jdk) {
    return isApplicableJdk(jdk, null);
  }

  public static boolean isApplicableJdk(@NotNull Sdk jdk, @Nullable LanguageLevel langLevel) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    if (langLevel == null) {
      langLevel = DEFAULT_LANG_LEVEL;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version != null) {
      //noinspection TestOnlyProblems
      return hasMatchingLangLevel(version, langLevel);
    }
    return false;
  }

  @Nullable
  public static String getJdkHomePath(@NotNull LanguageLevel langLevel) {
    Collection<String> jdkHomePaths = new ArrayList<>(JavaSdk.getInstance().suggestHomePaths());
    if (jdkHomePaths.isEmpty()) {
      return null;
    }
    // prefer jdk path of getJavaHome(), since we have to allow access to it in tests
    // see AndroidProjectDataServiceTest#testImportData()
    List<String> list = new ArrayList<>();
    String javaHome = SystemProperties.getJavaHome();

    if (javaHome != null && !javaHome.isEmpty()) {
      for (Iterator<String> it = jdkHomePaths.iterator(); it.hasNext(); ) {
        String path = it.next();

        if (path != null && javaHome.startsWith(path)) {
          it.remove();
          list.add(path);
        }
      }
    }
    list.addAll(jdkHomePaths);
    return getBestJdkHomePath(list, langLevel);

  }

  @VisibleForTesting
  @Nullable
  static String getBestJdkHomePath(@NotNull Collection<String> jdkHomePaths, @NotNull LanguageLevel langLevel) {
    // Search for JDKs in both the suggest folder and all its sub folders.
    List<String> roots = Lists.newArrayList();
    for (String jdkHomePath : jdkHomePaths) {
      if (isNotEmpty(jdkHomePath)) {
        roots.add(jdkHomePath);
        roots.addAll(getChildrenPaths(jdkHomePath));
      }
    }
    return getBestJdk(roots, langLevel);
  }

  @NotNull
  private static List<String> getChildrenPaths(@NotNull String dirPath) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      return emptyList();
    }
    List<String> childrenPaths = Lists.newArrayList();
    for (File child : notNullize(dir.listFiles())) {
      boolean directory = child.isDirectory();
      if (directory) {
        childrenPaths.add(child.getAbsolutePath());
      }
    }
    return childrenPaths;
  }

  @Nullable
  private static String getBestJdk(@NotNull List<String> jdkRoots, @NotNull LanguageLevel langLevel) {
    String bestJdk = null;
    for (String jdkRoot : jdkRoots) {
      if (JavaSdk.getInstance().isValidSdkHome(jdkRoot)) {
        if (bestJdk == null && hasMatchingLangLevel(jdkRoot, langLevel)) {
          bestJdk = jdkRoot;
        }
        else if (bestJdk != null) {
          bestJdk = selectJdk(bestJdk, jdkRoot, langLevel);
        }
      }
    }
    return bestJdk;
  }

  @Nullable
  private static String selectJdk(@NotNull String jdk1, @NotNull String jdk2, @NotNull LanguageLevel langLevel) {
    if (hasMatchingLangLevel(jdk1, langLevel)) {
      return jdk1;
    }
    if (hasMatchingLangLevel(jdk2, langLevel)) {
      return jdk2;
    }
    return null;
  }

  private static boolean hasMatchingLangLevel(@NotNull String jdkRoot, @NotNull LanguageLevel langLevel) {
    JavaSdkVersion version = getVersion(jdkRoot);
    return hasMatchingLangLevel(version, langLevel);
  }

  @VisibleForTesting
  static boolean hasMatchingLangLevel(@NotNull JavaSdkVersion jdkVersion, @NotNull LanguageLevel langLevel) {
    LanguageLevel max = jdkVersion.getMaxLanguageLevel();
    return max.isAtLeast(langLevel);
  }

  @Nullable
  public static JavaSdkVersion findVersion(@NotNull File jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot.getPath());
    if (isEmpty(version)) {
      return null;
    }
    return JavaSdk.getInstance().getVersion(version);
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
    Sdk jdk = createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    if (jdk == null) {
      String msg = String.format("Unable to create JDK from path '%1$s'", jdkHomePath);
      Logger.getInstance(Jdks.class).error(msg);
    }
    return jdk;
  }

  @Nullable
  public static Sdk createEmbeddedJdk() {
    if (isAndroidStudio()) {
      Sdk jdk = createJdk(getEmbeddedJdkPath().getPath());
      assert jdk != null;
      return jdk;
    }
    return null;
  }
}
