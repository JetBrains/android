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
package com.android.tools.idea.gradle.project.model;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.tools.idea.gradle.project.model.AndroidModelSourceProviderUtils.convertVersion;
import static com.android.tools.idea.gradle.util.GradleBuildOutputUtil.getGenericBuiltArtifact;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.lint.client.api.LintClient.getGradleDesugaring;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;

import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestOptions;
import com.android.builder.model.Variant;
import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.ide.common.gradle.model.GradleModelConverterUtil;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.android.projectmodel.DynamicResourceValue;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleClassJarProvider;
import com.android.tools.idea.gradle.util.GenericBuiltArtifactsWithTimestamp;
import com.android.tools.idea.gradle.util.LastBuildOrSyncService;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.lint.detector.api.Desugaring;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serialization.PropertyMapping;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class AndroidModuleModel implements AndroidModel, ModuleModel {
  // Placeholder application id if the project is never built before, there is no way to get application id.
  public static final String UNINITIALIZED_APPLICATION_ID = "uninitialized.application.id";
  private static final AndroidVersion NOT_SPECIFIED = new AndroidVersion(0, null);
  private final static String ourAndroidSyncVersion = "2020-05-21/2";

  @Nullable private transient Module myModule;

  @NotNull private ProjectSystemId myProjectSystemId;
  @NotNull private String myAndroidSyncVersion;
  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private IdeAndroidProject myAndroidProject;

  @NotNull private transient AndroidModelFeatures myFeatures;
  @Nullable private transient GradleVersion myModelVersion;
  @NotNull private String mySelectedVariantName;

  @Nullable private Boolean myOverridesManifestPackage;
  @Nullable private transient AndroidVersion myMinSdkVersion;

  @NotNull private final transient Map<String, BuildTypeContainer> myBuildTypesByName;
  @NotNull private final transient Map<String, ProductFlavorContainer> myProductFlavorsByName;
  @NotNull final transient Map<String, IdeVariant> myVariantsByName;

  @GuardedBy("myGenericBuiltArtifactsMap")
  @NotNull private final transient Map<String, GenericBuiltArtifactsWithTimestamp> myGenericBuiltArtifactsMap;

  @Nullable
  public static AndroidModuleModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static AndroidModuleModel get(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = AndroidModel.get(androidFacet);
    return androidModel instanceof AndroidModuleModel ? (AndroidModuleModel)androidModel : null;
  }

  public static AndroidModuleModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull AndroidProject androidProject,
                                          @NotNull String selectedVariantName,
                                          @NotNull IdeDependenciesFactory dependenciesFactory) {
    return create(moduleName, rootDirPath, androidProject, selectedVariantName, dependenciesFactory, null, emptyList(), emptyList());
  }

  public static AndroidModuleModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull IdeAndroidProject androidProject,
                                          @NotNull String variantName) {
    return new AndroidModuleModel(ourAndroidSyncVersion, moduleName, rootDirPath, androidProject, variantName);
  }

  /**
   * @param moduleName          the name of the IDEA module, created from {@code delegate}.
   * @param rootDirPath         the root directory of the imported Android-Gradle project.
   * @param androidProject      imported Android-Gradle project.
   * @param variantName         the name of selected variant.
   * @param dependenciesFactory the factory instance to create {@link IdeDependencies}.
   * @param variantsToAdd       list of variants to add that were requested but not present in the {@link AndroidProject}.
   * @param cachedVariants      list of IdeVariants to add that were cached from previous Gradle Sync.
   * @param syncIssues          Model containing all sync issues that were produced by Gradle.
   */
  public static AndroidModuleModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull AndroidProject androidProject,
                                          @NotNull String variantName,
                                          @NotNull IdeDependenciesFactory dependenciesFactory,
                                          @Nullable Collection<Variant> variantsToAdd,
                                          @NotNull Collection<IdeVariant> cachedVariants,
                                          @NotNull Collection<SyncIssue> syncIssues) {
    IdeAndroidProject ideAndroidProject = IdeAndroidProjectImpl.create(androidProject, dependenciesFactory, variantsToAdd, syncIssues);
    ideAndroidProject.addVariants(cachedVariants);
    return new AndroidModuleModel(ourAndroidSyncVersion, moduleName, rootDirPath, ideAndroidProject, variantName);
  }

  @PropertyMapping({"myAndroidSyncVersion", "myModuleName", "myRootDirPath", "myAndroidProject", "mySelectedVariantName"})
  @VisibleForTesting
  AndroidModuleModel(@NotNull String androidSyncVersion,
                     @NotNull String moduleName,
                     @NotNull File rootDirPath,
                     @NotNull IdeAndroidProject androidProject,
                     @NotNull String variantName) {
    myAndroidProject = androidProject;
    if (!androidSyncVersion.equals(ourAndroidSyncVersion)) {
      throw new IllegalArgumentException(
        String.format("Attempting to deserialize a model of incompatible version (%s)", androidSyncVersion));
    }
    myAndroidSyncVersion = ourAndroidSyncVersion;
    myProjectSystemId = GRADLE_SYSTEM_ID;
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    parseAndSetModelVersion();
    myFeatures = new AndroidModelFeatures(myModelVersion);
    myBuildTypesByName = myAndroidProject.getBuildTypes().stream().collect(toMap(it -> it.getBuildType().getName(), it -> it));
    myProductFlavorsByName = myAndroidProject.getProductFlavors().stream().collect(toMap(it -> it.getProductFlavor().getName(), it -> it));
    myVariantsByName = myAndroidProject.getVariants().stream().map(it -> (IdeVariant)it).collect(toMap(it -> it.getName(), it -> it));

    mySelectedVariantName = findVariantToSelect(variantName);
    myGenericBuiltArtifactsMap = new HashMap<>();
  }

  /**
   * Sets the IDE module this model is for, this should always be set on creation or re-attachement of the module to the project.
   * @param module
   */
   public void setModule(@NotNull Module module) {
    myModule = module;
   }

  /**
   * @return Instance of {@link IdeDependencies} from main artifact.
   */
  @NotNull
  public IdeDependencies getSelectedMainCompileLevel2Dependencies() {
    IdeAndroidArtifact mainArtifact = getMainArtifact();
    return mainArtifact.getLevel2Dependencies();
  }

  /**
   * @return Instance of {@link IdeDependencies} from test artifact, or {@code null} if current module has no test artifact.
   */
  @Nullable
  public IdeDependencies getSelectedAndroidTestCompileDependencies() {
    IdeAndroidArtifact androidTestArtifact = getSelectedVariant().getAndroidTestArtifact();
    if (androidTestArtifact == null) {
      // Only variants in the debug build type have an androidTest artifact.
      return null;
    }
    return androidTestArtifact.getLevel2Dependencies();
  }

  @NotNull
  public AndroidModelFeatures getFeatures() {
    return myFeatures;
  }

  @Nullable
  public GradleVersion getModelVersion() {
    return myModelVersion;
  }

  @NotNull
  public IdeAndroidArtifact getMainArtifact() {
    return getSelectedVariant().getMainArtifact();
  }

  @NotNull
  public SourceProvider getDefaultSourceProvider() {
    return getAndroidProject().getDefaultConfig().getSourceProvider();
  }

  @NotNull
  public List<SourceProvider> getActiveSourceProviders() {
    return AndroidModelSourceProviderUtils.collectMainSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<SourceProvider> getUnitTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectUnitTestSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<SourceProvider> getAndroidTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAndroidTestSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<SourceProvider> getTestSourceProviders(@NotNull String artifactName) {
    switch (artifactName) {
      case ARTIFACT_ANDROID_TEST:
        return AndroidModelSourceProviderUtils.collectAndroidTestSourceProviders(this, getSelectedVariant());
      case ARTIFACT_UNIT_TEST:
        return AndroidModelSourceProviderUtils.collectUnitTestSourceProviders(this, getSelectedVariant());
    }
    return ImmutableList.of();
  }

  /**
   * @return true if the variant model with given name has been requested before.
   */
  public boolean variantExists(@NotNull String variantName) {
    return myVariantsByName.containsKey(variantName);
  }

  @NotNull
  public List<SourceProvider> getAllSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllSourceProviders(this);
  }

  @NotNull
  public List<SourceProvider> getAllUnitTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllUnitTestSourceProviders(this);
  }

  @NotNull
  public List<SourceProvider> getAllAndroidTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllAndroidTestSourceProviders(this);
  }

  @Override
  @NotNull
  public String getApplicationId() {
    if (myFeatures.isBuildOutputFileSupported()) {
      return getApplicationIdUsingCache(mySelectedVariantName);
    }
    return getSelectedVariant().getMainArtifact().getApplicationId();
  }

  @Override
  @NotNull
  public Set<String> getAllApplicationIds() {
    Set<String> ids = new HashSet<>();
    for (Variant variant : myAndroidProject.getVariants()) {
      String applicationId = getApplicationIdUsingCache(variant.getName());
      if (applicationId != UNINITIALIZED_APPLICATION_ID) {
        ids.add(applicationId);
      }
    }
    return ids;
  }

  @Override
  public Boolean isDebuggable() {
    BuildTypeContainer buildTypeContainer = findBuildType(getSelectedVariant().getBuildType());
    if (buildTypeContainer != null) {
      return buildTypeContainer.getBuildType().isDebuggable();
    }
    return null;
  }


  /**
   * Returns the {@code minSdkVersion} specified by the user (in the default config or product flavors).
   * This is normally the merged value, but for example when using preview platforms, the Gradle plugin
   * will set minSdkVersion and targetSdkVersion to match the level of the compileSdkVersion; in this case
   * we want tools like lint's API check to continue to look for the intended minSdkVersion specified in
   * the build.gradle file
   *
   * @return the {@link AndroidVersion} to use for this Gradle project, or {@code null} if not specified.
   */
  @Override
  @Nullable
  public AndroidVersion getMinSdkVersion() {
    if (myMinSdkVersion == null) {
      ApiVersion minSdkVersion = getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion != null && minSdkVersion.getCodename() != null) {
        ApiVersion defaultConfigVersion = getAndroidProject().getDefaultConfig().getProductFlavor().getMinSdkVersion();
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
      myMinSdkVersion = minSdkVersion != null ? convertVersion(minSdkVersion, null) : NOT_SPECIFIED;
    }

    return myMinSdkVersion != NOT_SPECIFIED ? myMinSdkVersion : null;
  }

  @Override
  @Nullable
  public AndroidVersion getRuntimeMinSdkVersion() {
    ApiVersion minSdkVersion = getSelectedVariant().getMergedFlavor().getMinSdkVersion();
    return minSdkVersion != null ? convertVersion(minSdkVersion, null) : null;
  }

  @Override
  @Nullable
  public AndroidVersion getTargetSdkVersion() {
    ApiVersion targetSdkVersion = getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
    return targetSdkVersion != null ? convertVersion(targetSdkVersion, null) : null;
  }

  /**
   * @return the version code associated with the merged flavor of the selected variant, or {@code null} if none have been set.
   */
  @Override
  @Nullable
  public Integer getVersionCode() {
    Variant variant = getSelectedVariant();
    ProductFlavor flavor = variant.getMergedFlavor();
    return flavor.getVersionCode();
  }

  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return myProjectSystemId;
  }

  @Nullable
  public BuildTypeContainer findBuildType(@NotNull String name) {
    return myBuildTypesByName.get(name);
  }

  @NotNull
  public Set<String> getBuildTypes() {
    return myBuildTypesByName.keySet();
  }

  @NotNull
  public Set<String> getProductFlavors() {
    return myProductFlavorsByName.keySet();
  }

  @Nullable
  public ProductFlavorContainer findProductFlavor(@NotNull String name) {
    return myProductFlavorsByName.get(name);
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * @return the path of the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing
   * the build.gradle file.
   */
  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  @Override
  public boolean isGenerated(@NotNull VirtualFile file) {
    VirtualFile buildFolder = findFileByIoFile(myAndroidProject.getBuildFolder(), false);
    if (buildFolder != null && isAncestor(buildFolder, file, false)) {
      return true;
    }
    return false;
  }

  /**
   * @return the imported Android-Gradle project.
   */
  @NotNull
  public IdeAndroidProject getAndroidProject() {
    return myAndroidProject;
  }

  /**
   * @return the selected build variant.
   */
  @NotNull
  public IdeVariant getSelectedVariant() {
    IdeVariant selected = myVariantsByName.get(mySelectedVariantName);
    assert selected != null;
    return selected;
  }

  /**
   * Returns the selected variant name
   */
  public String getSelectedVariantName() {
    return mySelectedVariantName;
  }

  /**
   * @return a list of synced build variants.
   */
  @NotNull
  public ImmutableList<IdeVariant> getVariants() {
    return ImmutableList.copyOf(myVariantsByName.values());
  }

  @Nullable
  public IdeVariant findVariantByName(@NotNull String variantName) {
    return myVariantsByName.get(variantName);
  }

  /**
   * Updates the name of the selected build variant. If the given name does not belong to an existing variant, this method will pick up
   * the first variant, in alphabetical order.
   *
   * @param name the new name.
   */
  public void setSelectedVariantName(@NotNull String name) {
    mySelectedVariantName = findVariantToSelect(name);

    // force lazy recompute
    myOverridesManifestPackage = null;
    myMinSdkVersion = null;
  }

  @VisibleForTesting
  @NotNull
  String findVariantToSelect(@NotNull String variantName) {
    String newVariantName;
    if (myVariantsByName.containsKey(variantName)) {
      newVariantName = variantName;
    }
    else {
      List<String> sorted = new ArrayList<>(myVariantsByName.keySet());
      Collections.sort(sorted);
      assert !myVariantsByName.isEmpty() : "There is no variant model in AndroidModuleModel!";
      newVariantName = sorted.get(0);
    }
    return newVariantName;
  }

  @NotNull
  public Collection<String> getBuildTypeNames() {
    return myBuildTypesByName.keySet();
  }

  @NotNull
  public Collection<String> getProductFlavorNames() {
    return myProductFlavorsByName.keySet();
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myAndroidProject.getVariantNames();
  }

  @Nullable
  public LanguageLevel getJavaLanguageLevel() {
    JavaCompileOptions compileOptions = myAndroidProject.getJavaCompileOptions();
    String sourceCompatibility = compileOptions.getSourceCompatibility();
    return LanguageLevel.parse(sourceCompatibility);
  }

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   *
   * @return true if the manifest package is overridden
   */
  @Override
  public boolean overridesManifestPackage() {
    if (myOverridesManifestPackage == null) {
      myOverridesManifestPackage = getAndroidProject().getDefaultConfig().getProductFlavor().getApplicationId() != null;

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

  @Nullable
  public Collection<SyncIssue> getSyncIssues() {
    if (getFeatures().isIssueReportingSupported()) {
      return myAndroidProject.getSyncIssues();
    }
    return null;
  }

  /**
   * Returns the {@link IdeAndroidArtifact} that should be used for instrumented testing.
   *
   * <p>For test-only modules this is the main artifact.
   */
  @Nullable
  public IdeAndroidArtifact getArtifactForAndroidTest() {
    return getAndroidProject().getProjectType() == PROJECT_TYPE_TEST ?
           getSelectedVariant().getMainArtifact() :
           getSelectedVariant().getAndroidTestArtifact();
  }

  @Nullable
  public TestOptions.Execution getTestExecutionStrategy() {
    IdeAndroidArtifact artifact = getArtifactForAndroidTest();
    if (artifact != null) {
      TestOptions testOptions = artifact.getTestOptions();
      if (testOptions != null) {
        return testOptions.getExecution();
      }
    }

    return null;
  }

  private void parseAndSetModelVersion() {
    // Old plugin versions do not return model version.
    myModelVersion = GradleVersion.tryParse(myAndroidProject.getModelVersion());
  }

  /**
   * Returns the source providers for the available flavors, which will never be {@code null} for a project backed by an
   * {@link AndroidProject}, and always {@code null} for a legacy Android project.
   *
   * @return the flavor source providers or {@code null} in legacy projects.
   * @deprecated no reason to use just a subset of source providers outside of Gradle project system.
   */
  @Deprecated
  @NotNull
  public List<SourceProvider> getFlavorSourceProviders() {
    Variant selectedVariant = getSelectedVariant();
    List<String> productFlavors = selectedVariant.getProductFlavors();
    List<SourceProvider> providers = new ArrayList<>();
    for (String flavor : productFlavors) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.add(productFlavor.getSourceProvider());
    }
    return providers;
  }

  public void syncSelectedVariantAndTestArtifact(@NotNull AndroidFacet facet) {
    IdeVariant variant = getSelectedVariant();
    AndroidFacetProperties state = facet.getProperties();
    state.SELECTED_BUILD_VARIANT = variant.getName();

    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();

    // When multi test artifacts are enabled, test tasks are computed dynamically.
    updateGradleTaskNames(state, mainArtifact);
  }

  private static void updateGradleTaskNames(@NotNull AndroidFacetProperties state, @NotNull IdeAndroidArtifact mainArtifact) {
    state.ASSEMBLE_TASK_NAME = mainArtifact.getAssembleTaskName();
    state.COMPILE_JAVA_TASK_NAME = mainArtifact.getCompileTaskName();
    state.AFTER_SYNC_TASK_NAMES = new HashSet<>(mainArtifact.getIdeSetupTaskNames());

    state.ASSEMBLE_TEST_TASK_NAME = "";
    state.COMPILE_JAVA_TEST_TASK_NAME = "";
  }

  @Override
  @NotNull
  public ClassJarProvider getClassJarProvider() {
    return new AndroidGradleClassJarProvider();
  }

  @Override
  public boolean isClassFileOutOfDate(@NotNull Module module, @NotNull String fqcn, @NotNull VirtualFile classFile) {
    return ClassFileUtil.isClassSourceFileNewerThanClassClassFile(module, fqcn, classFile);
  }

  @NotNull
  @Override
  public AaptOptions.Namespacing getNamespacing() {
    return myAndroidProject.getAaptOptions().getNamespacing();
  }

  @NotNull
  @Override
  public Set<Desugaring> getDesugaring() {
    GradleVersion version = getModelVersion();
    if (version == null) {
      return Desugaring.NONE;
    }

    return getGradleDesugaring(version, getJavaLanguageLevel(), myAndroidProject.getJavaCompileOptions().isCoreLibraryDesugaringEnabled());
  }

  @Override
  @NotNull
  public Map<String, DynamicResourceValue> getResValues() {
    return GradleModelConverterUtil.classFieldsToDynamicResourceValues(getSelectedVariant().getMainArtifact().getResValues());
  }

  @NotNull
  private String getApplicationIdUsingCache(@NotNull String variantName) {
    synchronized (myGenericBuiltArtifactsMap) {
      GenericBuiltArtifactsWithTimestamp artifactsWithTimestamp = myGenericBuiltArtifactsMap.get(variantName);
      long lastSyncOrBuild = Long.MAX_VALUE; // If we don't have a module default to MAX which will always trigger a re-compute.
      if (myModule != null) {
        lastSyncOrBuild = myModule.getProject().getService(LastBuildOrSyncService.class).getLastBuildOrSyncTimeStamp();
      } else {
        Logger.getInstance(AndroidModuleModel.class).warn("No module set on model named: " + myModuleName);
      }
      if (artifactsWithTimestamp == null || lastSyncOrBuild >= artifactsWithTimestamp.getTimeStamp()) {
        // Cache is invalid
        artifactsWithTimestamp =
          new GenericBuiltArtifactsWithTimestamp(getGenericBuiltArtifact(this, variantName), System.currentTimeMillis());
        myGenericBuiltArtifactsMap.put(variantName, artifactsWithTimestamp);
      }

      GenericBuiltArtifacts artifacts = artifactsWithTimestamp.getGenericBuiltArtifacts();
      if (artifacts == null) {
        return UNINITIALIZED_APPLICATION_ID;
      }

      return artifacts.getApplicationId();
    }
  }
}
