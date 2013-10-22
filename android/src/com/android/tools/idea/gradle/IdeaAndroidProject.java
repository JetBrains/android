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
package com.android.tools.idea.gradle;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.Variant;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class IdeaAndroidProject implements Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final VirtualFile myRootDir;
  @NotNull private final AndroidProject myDelegate;
  @NotNull private String mySelectedVariantName;

  /**
   * Creates a new {@link IdeaAndroidProject}.
   *
   * @param moduleName                the name of the IDEA module, created from {@code delegate}.
   * @param rootDir                   the root directory of the imported Android-Gradle project.
   * @param delegate                  imported Android-Gradle project.
   * @param selectedVariantName       name of the selected build variant.
   */
  public IdeaAndroidProject(@NotNull String moduleName,
                            @NotNull File rootDir,
                            @NotNull AndroidProject delegate,
                            @NotNull String selectedVariantName) {
    myModuleName = moduleName;
    VirtualFile found = VfsUtil.findFileByIoFile(rootDir, true);
    // the module's root directory can never be null.
    assert found != null;
    myRootDir = found;
    myDelegate = delegate;
    setSelectedVariantName(selectedVariantName);
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * @return the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing the
   * build.gradle file.
   */
  @NotNull
  public VirtualFile getRootDir() {
    return myRootDir;
  }

  /**
   * @return the imported Android-Gradle project.
   */
  @NotNull
  public AndroidProject getDelegate() {
    return myDelegate;
  }

  /**
   * @return the selected build variant.
   */
  @NotNull
  public Variant getSelectedVariant() {
    Variant selected = myDelegate.getVariants().get(mySelectedVariantName);
    return Preconditions.checkNotNull(selected);
  }

  /**
   * Updates the name of the selected build variant. If the given name does not belong to an existing variant, this method will pick up
   * the first variant, in alphabetical order.
   *
   * @param name the new name.
   */
  public void setSelectedVariantName(@NotNull String name) {
    Collection<String> variantNames = getVariantNames();
    String newVariantName;
    if (variantNames.contains(name)) {
      newVariantName = name;
    } else {
      List<String> sorted = Lists.newArrayList(variantNames);
      Collections.sort(sorted);
      newVariantName = sorted.get(0);
    }
    mySelectedVariantName = newVariantName;
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myDelegate.getVariants().keySet();
  }

  @Nullable
  public LanguageLevel getJavaLanguageLevel() {
    try {
      JavaCompileOptions compileOptions = myDelegate.getJavaCompileOptions();
      String sourceCompatibility = compileOptions.getSourceCompatibility();
      return LanguageLevel.parse(sourceCompatibility);
    }
    catch (UnsupportedMethodException e) {
      // This happens when using an old but supported v0.5.+ plug-in. This code will be removed once the minimum supported version is 0.6.0.
      return null;
    }
  }

  /**
   * Computes the package name to be used for the current variant in the given project.
   *
   * @param project the project
   * @param manifestPackage the manifest package, if known
   * @return the package for the current variant (and is only null if the user's project is configured incorrectly)
   */
  @Nullable
  public static String computePackageName(@NotNull IdeaAndroidProject project, @Nullable String manifestPackage) {
    return computePackageName(project, project.getSelectedVariant(), manifestPackage);
  }

  /**
   * Computes the package name to be used for the given project, if specified in the Gradle files.
   *
   * @param project the project
   * @param variant the variant to compute the package name for
   * @param manifestPackage the manifest package, if known
   * @return the package for the given variant (and is only null if the user's project is configured incorrectly)
   */
  @Nullable
  public static String computePackageName(@NotNull IdeaAndroidProject project,
                                          @NotNull Variant variant,
                                          @Nullable String manifestPackage) {
    String packageName = variant.getMergedFlavor().getPackageName();
    if (packageName == null) {
      packageName = manifestPackage;
    }
    if (packageName != null) {
      String buildTypeName = variant.getBuildType();
      BuildTypeContainer buildTypeContainer = project.getDelegate().getBuildTypes().get(buildTypeName);
      String packageNameSuffix = buildTypeContainer.getBuildType().getPackageNameSuffix();
      if (packageNameSuffix != null && !packageNameSuffix.isEmpty()) {
        packageName += packageNameSuffix;
      }
    }

    return packageName;
  }
}
