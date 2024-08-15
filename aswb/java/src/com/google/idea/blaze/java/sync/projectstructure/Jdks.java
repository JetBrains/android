/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.sync.projectstructure;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.java.sync.sdk.BlazeJdkProvider;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/** Utility methods related to IDEA JDKs. */
public class Jdks {

  private static final Logger logger = Logger.getInstance(Jdks.class);

  private static final BoolExperiment keepCurrentJdkPreferentially =
      new BoolExperiment("blaze.keep.current.jdk", true);

  private static final BoolExperiment useExistingJdkPreferentially =
      new BoolExperiment("blaze.use.existing.jdk", false);

  @Nullable
  public static Sdk chooseOrCreateJavaSdk(@Nullable Sdk currentSdk, LanguageLevel langLevel) {
    ImmutableList<String> jdkHomePaths =
        BlazeJdkProvider.EP_NAME
            .extensions()
            .map(provider -> provider.provideJdkForLanguageLevel(langLevel))
            .filter(Objects::nonNull)
            .map(File::getPath)
            .collect(toImmutableList());

    if (keepCurrentJdkPreferentially.getValue()
        && currentSdk != null
        && currentSdk.getSdkType() == JavaSdk.getInstance()
        && ProjectJdkTable.getInstance().findJdk(currentSdk.getName()) == currentSdk) {
      if (jdkHomePaths.stream().anyMatch(homePath -> jdkPathMatches(currentSdk, homePath))) {
        return currentSdk;
      } else if (jdkHomePaths.isEmpty()) {
        LanguageLevel currentLangLevel = getJavaLanguageLevel(currentSdk);
        if (currentLangLevel != null
            && currentLangLevel.isAtLeast(langLevel)
            && isValid(currentSdk)) {
          return currentSdk;
        }
      }
    }

    if (jdkHomePaths.isEmpty() || useExistingJdkPreferentially.getValue()) {
      // fall back to looking for closest match before using suggesters
      Sdk existing = findClosestMatch(langLevel);
      if (existing != null) {
        return existing;
      }
    }

    return jdkHomePaths.stream()
        .findFirst()
        .or(() -> Optional.ofNullable(getJdkHomePath(langLevel)))
        .map(Jdks::getOrCreateSdk)
        .orElse(null);
  }

  private static Sdk getOrCreateSdk(String homePath) {
    return ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).stream()
        .filter(jdk -> jdkPathMatches(jdk, homePath))
        .findFirst()
        .orElseGet(() -> createJdk(homePath));
  }

  private static boolean jdkPathMatches(Sdk jdk, String homePath) {
    String jdkPath = jdk.getHomePath();
    if (jdkPath == null) {
      return false;
    }
    try {
      return Objects.equals(
          new File(homePath).getCanonicalPath(), new File(jdkPath).getCanonicalPath());
    } catch (IOException e) {
      // ignore these exceptions
      return false;
    }
  }

  @Nullable
  @VisibleForTesting
  static Sdk findClosestMatch(LanguageLevel langLevel) {
    return ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).stream()
        .filter(
            sdk -> {
              LanguageLevel level = getJavaLanguageLevel(sdk);
              return level != null && level.isAtLeast(langLevel);
            })
        .filter(Jdks::isValid)
        .min(Comparator.comparing(Jdks::getJavaLanguageLevel))
        .orElse(null);
  }

  private static boolean isValid(Sdk jdk) {
    // detect the case of JDKs with no-longer-valid roots
    return ApplicationManager.getApplication().isUnitTestMode()
        || jdk.getSdkModificator().getRoots(OrderRootType.CLASSES).length != 0;
  }

  /**
   * Returns null if the SDK is not a java JDK, or doesn't have a recognized java langauge level.
   */
  @Nullable
  private static LanguageLevel getJavaLanguageLevel(Sdk sdk) {
    if (!(sdk.getSdkType() instanceof JavaSdk)) {
      return null;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
    return version != null ? version.getMaxLanguageLevel() : null;
  }

  @Nullable
  private static String getJdkHomePath(LanguageLevel langLevel) {
    Collection<String> jdkHomePaths = new ArrayList<>(JavaSdk.getInstance().suggestHomePaths());
    if (jdkHomePaths.isEmpty()) {
      return null;
    }
    // prefer jdk path of getJavaHome(), since we have to allow access to it in tests
    // see AndroidProjectDataServiceTest#testImportData()
    final List<String> list = new ArrayList<>();
    String javaHome = SystemProperties.getJavaHome();

    if (javaHome != null && !javaHome.isEmpty()) {
      for (Iterator<String> it = jdkHomePaths.iterator(); it.hasNext(); ) {
        final String path = it.next();

        if (path != null && javaHome.startsWith(path)) {
          it.remove();
          list.add(path);
        }
      }
    }
    list.addAll(jdkHomePaths);
    return getBestJdkHomePath(list, langLevel);
  }

  @Nullable
  private static String getBestJdkHomePath(List<String> jdkHomePaths, LanguageLevel langLevel) {
    // Search for JDKs in both the suggest folder and all its sub folders.
    List<String> roots = Lists.newArrayList();
    for (String jdkHomePath : jdkHomePaths) {
      if (StringUtil.isNotEmpty(jdkHomePath)) {
        roots.add(jdkHomePath);
        roots.addAll(getChildrenPaths(jdkHomePath));
      }
    }
    return getBestJdk(roots, langLevel);
  }

  private static ImmutableList<String> getChildrenPaths(String dirPath) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      return ImmutableList.of();
    }
    List<String> childrenPaths = Lists.newArrayList();
    for (File child : notNullize(dir.listFiles())) {
      boolean directory = child.isDirectory();
      if (directory) {
        childrenPaths.add(child.getAbsolutePath());
      }
    }
    return ImmutableList.copyOf(childrenPaths);
  }

  @Nullable
  private static String getBestJdk(List<String> jdkRoots, LanguageLevel langLevel) {
    return jdkRoots.stream()
        .filter(root -> JavaSdk.getInstance().isValidSdkHome(root))
        .filter(root -> getVersion(root).getMaxLanguageLevel().isAtLeast(langLevel))
        .min(Comparator.comparing(o -> getVersion(o).getMaxLanguageLevel()))
        .orElse(null);
  }

  private static JavaSdkVersion getVersion(String jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot);
    if (version == null) {
      return JavaSdkVersion.JDK_1_0;
    }
    JavaSdkVersion sdkVersion = JavaSdkVersion.fromVersionString(version);
    return sdkVersion == null ? JavaSdkVersion.JDK_1_0 : sdkVersion;
  }

  @Nullable
  private static Sdk createJdk(String jdkHomePath) {
    Sdk jdk = SdkConfigurationUtil.createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    if (jdk == null) {
      logger.error(String.format("Unable to create JDK from path '%1$s'", jdkHomePath));
    }
    return jdk;
  }
}
