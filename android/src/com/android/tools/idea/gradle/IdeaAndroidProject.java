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

import com.android.builder.model.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.tools.idea.gradle.customizer.android.ContentRootModuleCustomizer.EXCLUDED_OUTPUT_FOLDER_NAMES;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class IdeaAndroidProject implements Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final VirtualFile myRootDir;
  @NotNull private final AndroidProject myDelegate;
  @NotNull private String mySelectedVariantName;
  @Nullable private Boolean myOverridesManifestPackage;
  @Nullable private AndroidVersion myMinSdkVersion;

  @NotNull private Map<String, BuildTypeContainer> myBuildTypesByName = Maps.newHashMap();
  @NotNull private Map<String, ProductFlavorContainer> myProductFlavorsByName = Maps.newHashMap();
  @NotNull private Map<String, Variant> myVariantsByName = Maps.newHashMap();

  @NotNull private Set<File> myExtraGeneratedSourceFolders = Sets.newHashSet();

  /**
   * Creates a new {@link IdeaAndroidProject}.
   *
   * @param moduleName          the name of the IDEA module, created from {@code delegate}.
   * @param rootDir             the root directory of the imported Android-Gradle project.
   * @param delegate            imported Android-Gradle project.
   * @param selectedVariantName name of the selected build variant.
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

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    setSelectedVariantName(selectedVariantName);
  }

  private void populateBuildTypesByName() {
    for (BuildTypeContainer container : myDelegate.getBuildTypes()) {
      String name = container.getBuildType().getName();
      myBuildTypesByName.put(name, container);
    }
  }

  private void populateProductFlavorsByName() {
    for (ProductFlavorContainer container : myDelegate.getProductFlavors()) {
      String name = container.getProductFlavor().getName();
      myProductFlavorsByName.put(name, container);
    }
  }

  private void populateVariantsByName() {
    for (Variant variant : myDelegate.getVariants()) {
      myVariantsByName.put(variant.getName(), variant);
    }
  }

  @Nullable
  public BuildTypeContainer findBuildType(@NotNull String name) {
    return myBuildTypesByName.get(name);
  }

  @Nullable
  public ProductFlavorContainer findProductFlavor(@NotNull String name) {
    return myProductFlavorsByName.get(name);
  }

  @Nullable
  public AndroidArtifact findInstrumentationTestArtifactInSelectedVariant() {
    Variant variant = getSelectedVariant();
    return findInstrumentationTestArtifact(variant);
  }

  @Nullable
  public static AndroidArtifact findInstrumentationTestArtifact(@NotNull Variant variant) {
    Collection<AndroidArtifact> extraAndroidArtifacts = variant.getExtraAndroidArtifacts();
    for (AndroidArtifact extraArtifact : extraAndroidArtifacts) {
      if (extraArtifact.getName().equals(AndroidProject.ARTIFACT_ANDROID_TEST)) {
        return extraArtifact;
      }
    }
    return null;
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
    Variant selected = myVariantsByName.get(mySelectedVariantName);
    assert selected != null;
    return selected;
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
    }
    else {
      List<String> sorted = Lists.newArrayList(variantNames);
      Collections.sort(sorted);
      // AndroidProject has always at least 2 variants (debug and release.)
      newVariantName = sorted.get(0);
    }
    mySelectedVariantName = newVariantName;

    // force lazy recompute
    myOverridesManifestPackage = null;
    myMinSdkVersion = null;
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myVariantsByName.keySet();
  }

  @NotNull
  public Collection<String> getBuildTypeNames() {
    return myBuildTypesByName.keySet();
  }

  @NotNull
  public Collection<String> getProductFlavorNames() {
    return myProductFlavorsByName.keySet();
  }

  @Nullable
  public LanguageLevel getJavaLanguageLevel() {
    JavaCompileOptions compileOptions = myDelegate.getJavaCompileOptions();
    String sourceCompatibility = compileOptions.getSourceCompatibility();
    return LanguageLevel.parse(sourceCompatibility);
  }

  /**
   * Returns the package name used for the current variant in the given project.
   */
  @NotNull
  public String computePackageName() {
    return getSelectedVariant().getMainArtifact().getApplicationId();
  }

  public boolean isLibrary() {
    return getDelegate().isLibrary();
  }

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   *
   * @return true if the manifest package is overridden
   */
  public boolean overridesManifestPackage() {
    if (myOverridesManifestPackage == null) {
      myOverridesManifestPackage = getDelegate().getDefaultConfig().getProductFlavor().getApplicationId() != null;

      Variant variant = getSelectedVariant();

      List<String> flavors = variant.getProductFlavors();
      for (String flavor : flavors) {
        ProductFlavorContainer productFlavor = findProductFlavor(flavor);
        assert productFlavor != null;
        if (productFlavor.getProductFlavor().getApplicationId() != null) {
          myOverridesManifestPackage = true;
          break;
        }
      }
      // The build type can specify a suffix, but it will be merged with the manifest
      // value if not specified in a flavor/default config, so only flavors count
    }

    return myOverridesManifestPackage.booleanValue();
  }

  private static final AndroidVersion NOT_SPECIFIED = new AndroidVersion(0, null);

  /**
   * Returns the {@code }minSdkVersion} specified by the user (in the default config or product flavors).
   * This is normally the merged value, but for example when using preview platforms, the Gradle plugin
   * will set minSdkVersion and targetSdkVersion to match the level of the compileSdkVersion; in this case
   * we want tools like lint's API check to continue to look for the intended minSdkVersion specified in
   * the build.gradle file
   *
   * @return the {@link AndroidVersion} to use for this Gradle project, or null if not specified
   */
  @Nullable
  public AndroidVersion getConfigMinSdkVersion() {
    if (myMinSdkVersion == null) {
      ApiVersion minSdkVersion = getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion != null && minSdkVersion.getCodename() != null) {
        ApiVersion defaultConfigVersion  = getDelegate().getDefaultConfig().getProductFlavor().getMinSdkVersion();
        if (defaultConfigVersion != null) {
          minSdkVersion = defaultConfigVersion;
        }

        List<String> flavors = getSelectedVariant().getProductFlavors();
        for (String flavor : flavors) {
          ProductFlavorContainer productFlavor = findProductFlavor(flavor);
          assert productFlavor != null;
          ApiVersion flavorVersion = productFlavor.getProductFlavor().getMinSdkVersion();
          if (flavorVersion != null) {
            minSdkVersion = flavorVersion;
            break;
          }
        }
      }

      if (minSdkVersion != null) {
        myMinSdkVersion = LintUtils.convertVersion(minSdkVersion, null);
      } else {
        myMinSdkVersion = NOT_SPECIFIED;
      }
    }

    return myMinSdkVersion != NOT_SPECIFIED ? myMinSdkVersion : null;
  }

  /**
   * Registers the path of a source folder that has been incorrectly generated outside of the default location (${buildDir}/generated.)
   *
   * @param folderPath the path of the generated source folder.
   */
  public void registerExtraGeneratedSourceFolder(@NotNull File folderPath) {
    myExtraGeneratedSourceFolders.add(folderPath);
  }

  /**
   * Indicates whether the given path should be manually excluded in the IDE, to minimize file indexing.
   * <p>
   * This method returns {@code false} if:
   * <ul>
   *   <li>the given path does not belong to a folder</li>
   *   <li>the path belongs to the "generated sources" root folder (${buildDir}/generated)</li>
   *   <li>the path belongs to the standard output folders (${buildDir}/intermediates and ${buildDir}/outputs)</li>
   *   <li>or if the path belongs to a generated source folder that has been placed at the wrong location (e.g. by a 3rd-party Gradle
   *   plug-in)</li>
   * </ul>
   * </p>
   *
   * @param path the given path
   * @return {@code true} if the path should be manually excluded in the IDE, {@code false otherwise}.
   */
  public boolean shouldManuallyExclude(@NotNull File path) {
    if (!path.isDirectory()) {
      return false;
    }
    String name = path.getName();
    if (EXCLUDED_OUTPUT_FOLDER_NAMES.contains(name)) {
      // already excluded.
      return false;
    }
    boolean hasGeneratedFolders = FD_GENERATED.equals(name) || containsExtraGeneratedSourceFolder(path);
    return !hasGeneratedFolders;
  }

  private boolean containsExtraGeneratedSourceFolder(@NotNull File folderPath) {
    if (!folderPath.isDirectory()) {
      return false;
    }
    for (File generatedSourceFolder : myExtraGeneratedSourceFolders) {
      if (FileUtil.isAncestor(folderPath, generatedSourceFolder, false)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the paths of generated sources placed at the wrong location (not in ${build}/generated.)
   */
  @NotNull
  public File[] getExtraGeneratedSourceFolders() {
    return myExtraGeneratedSourceFolders.toArray(new File[myExtraGeneratedSourceFolders.size()]);
  }
}
