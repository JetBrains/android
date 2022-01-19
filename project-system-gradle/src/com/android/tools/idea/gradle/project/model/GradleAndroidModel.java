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

import static com.android.tools.idea.gradle.project.model.AndroidModelSourceProviderUtils.convertVersion;
import static com.android.tools.idea.gradle.project.model.AndroidModuleModelUtilKt.classFieldsToDynamicResourceValues;
import static com.android.tools.idea.gradle.util.BuildOutputUtil.getOutputListingFile;
import static com.android.tools.idea.gradle.util.BuildOutputUtil.loadBuildOutputListingFile;
import static com.android.tools.idea.gradle.util.BuildOutputUtil.variantOutputInformation;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.lint.client.api.LintClient.getGradleDesugaring;
import static java.util.stream.Collectors.toMap;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.ide.common.repository.GradleVersion;
import com.android.projectmodel.DynamicResourceValue;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.gradle.AndroidGradleClassJarProvider;
import com.android.tools.idea.gradle.model.IdeAaptOptions;
import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeApiVersion;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation;
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeJavaCompileOptions;
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer;
import com.android.tools.idea.gradle.model.IdeSourceProvider;
import com.android.tools.idea.gradle.model.IdeTestOptions;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys;
import com.android.tools.idea.gradle.util.GenericBuiltArtifactsWithTimestamp;
import com.android.tools.idea.gradle.util.LastBuildOrSyncService;
import com.android.tools.idea.gradle.util.OutputType;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.model.Namespacing;
import com.android.tools.idea.model.TestExecutionOption;
import com.android.tools.idea.model.TestOptions;
import com.android.tools.lint.detector.api.Desugaring;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serialization.PropertyMapping;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class GradleAndroidModel implements AndroidModuleModel {
  private static final AndroidVersion NOT_SPECIFIED = new AndroidVersion(0, null);
  private final static String ourAndroidSyncVersion = "2021-03-17/1";

  @Nullable public transient Object lintModuleModelCache;
  @Nullable private transient Module myModule;

  @NotNull private ProjectSystemId myProjectSystemId;
  @NotNull private String myAndroidSyncVersion;
  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private final IdeAndroidProject myAndroidProject;
  @NotNull private final Map<String, IdeVariant> myCachedVariantsByName;

  @NotNull private transient AndroidModelFeatures myFeatures;
  @NotNull private transient GradleVersion myAgpVersion;
  @NotNull private String mySelectedVariantName;

  @Nullable private Boolean myOverridesManifestPackage;
  @Nullable private transient AndroidVersion myMinSdkVersion;

  @NotNull private final transient Map<String, IdeBuildTypeContainer> myBuildTypesByName;
  @NotNull private final transient Map<String, IdeProductFlavorContainer> myProductFlavorsByName;

  @GuardedBy("myGenericBuiltArtifactsMap")
  @NotNull private final transient Map<String, GenericBuiltArtifactsWithTimestamp> myGenericBuiltArtifactsMap;

  @Nullable
  public static GradleAndroidModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static GradleAndroidModel get(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = AndroidModel.get(androidFacet);
    return androidModel instanceof GradleAndroidModel ? (GradleAndroidModel)androidModel : null;
  }

  @Nullable
  public static GradleAndroidModel findFromModuleDataNode(@NotNull DataNode<?> dataNode) {
    if (dataNode.getKey().equals(ProjectKeys.MODULE)) {
      DataNode<GradleAndroidModel> androidModelNode = ExternalSystemApiUtil.find(dataNode, AndroidProjectKeys.ANDROID_MODEL);
      if (androidModelNode != null) {
        return androidModelNode.getData();
      }
    } else if (dataNode.getKey().equals(GradleSourceSetData.KEY)) {
      // Source set nodes have the module attached to the parent node
      DataNode<?> parent = dataNode.getParent();
      if (parent != null) {
        return findFromModuleDataNode(parent);
      }
    }
    return null;
  }

  public static GradleAndroidModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull IdeAndroidProject androidProject,
                                          @NotNull Collection<IdeVariant> cachedVariants,
                                          @NotNull String variantName) {
    return new GradleAndroidModel(ourAndroidSyncVersion,
                                  moduleName,
                                  rootDirPath,
                                  androidProject,
                                  cachedVariants.stream().collect(toMap(it -> it.getName(), it -> it)),
                                  variantName);
  }

  @PropertyMapping({"myAndroidSyncVersion", "myModuleName", "myRootDirPath", "myAndroidProject", "myCachedVariantsByName",
    "mySelectedVariantName"})
  @VisibleForTesting
  GradleAndroidModel(@NotNull String androidSyncVersion,
                     @NotNull String moduleName,
                     @NotNull File rootDirPath,
                     @NotNull IdeAndroidProject androidProject,
                     @NotNull Map<String, IdeVariant> cachedVariantsByName,
                     @NotNull String variantName) {
    if (!androidSyncVersion.equals(ourAndroidSyncVersion)) {
      throw new IllegalArgumentException(
        String.format("Attempting to deserialize a model of incompatible version (%s)", androidSyncVersion));
    }
    myAndroidSyncVersion = ourAndroidSyncVersion;
    myProjectSystemId = GRADLE_SYSTEM_ID;
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myAndroidProject = androidProject;
    myCachedVariantsByName = cachedVariantsByName;
    setSelectedVariantName(variantName);

    myAgpVersion = GradleVersion.parseAndroidGradlePluginVersion(myAndroidProject.getAgpVersion()); // Fail sync if the reported version cannot be parsed.
    myFeatures = new AndroidModelFeatures(myAgpVersion);
    myBuildTypesByName = myAndroidProject.getBuildTypes().stream().collect(toMap(it -> it.getBuildType().getName(), it -> it));
    myProductFlavorsByName = myAndroidProject.getProductFlavors().stream().collect(toMap(it -> it.getProductFlavor().getName(), it -> it));

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

  @Override
  @NotNull
  public GradleVersion getAgpVersion() {
    return myAgpVersion;
  }

  @NotNull
  public IdeAndroidArtifact getMainArtifact() {
    return getSelectedVariant().getMainArtifact();
  }

  @NotNull
  public IdeSourceProvider getDefaultSourceProvider() {
    return getAndroidProject().getDefaultConfig().getSourceProvider();
  }

  @NotNull
  public List<IdeSourceProvider> getActiveSourceProviders() {
    return AndroidModelSourceProviderUtils.collectMainSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<IdeSourceProvider> getUnitTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectUnitTestSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<IdeSourceProvider> getAndroidTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAndroidTestSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<IdeSourceProvider> getTestFixturesSourceProviders() {
    return AndroidModelSourceProviderUtils.collectTestFixturesSourceProviders(this, getSelectedVariant());
  }

  @NotNull
  public List<IdeSourceProvider> getTestSourceProviders(@NotNull IdeArtifactName artifactName) {
    switch (artifactName) {
      case ANDROID_TEST:
        return AndroidModelSourceProviderUtils.collectAndroidTestSourceProviders(this, getSelectedVariant());
      case UNIT_TEST:
        return AndroidModelSourceProviderUtils.collectUnitTestSourceProviders(this, getSelectedVariant());
    }
    return ImmutableList.of();
  }

  /**
   * @return true if the variant model with given name has been requested before.
   */
  public boolean variantExists(@NotNull String variantName) {
    return myCachedVariantsByName.containsKey(variantName);
  }

  @NotNull
  public List<IdeSourceProvider> getAllSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllSourceProviders(this);
  }

  @NotNull
  public List<IdeSourceProvider> getAllUnitTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllUnitTestSourceProviders(this);
  }

  @NotNull
  public List<IdeSourceProvider> getAllAndroidTestSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllAndroidTestSourceProviders(this);
  }

  @NotNull
  public List<IdeSourceProvider> getAllTestFixturesSourceProviders() {
    return AndroidModelSourceProviderUtils.collectAllTestFixturesSourceProviders(this);
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
    for (IdeVariant variant : getVariants()) {
      String applicationId = getApplicationIdUsingCache(variant.getName());
      if (!UNINITIALIZED_APPLICATION_ID.equals(applicationId)) {
        ids.add(applicationId);
      }
    }
    return ids;
  }

  @Override
  public Boolean isDebuggable() {
    IdeBuildTypeContainer buildTypeContainer = findBuildType(getSelectedVariant().getBuildType());
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
  @NotNull
  public AndroidVersion getMinSdkVersion() {
    if (myMinSdkVersion == null) {
      IdeApiVersion minSdkVersion = getSelectedVariant().getMinSdkVersion();
      if (minSdkVersion.getCodename() != null) {
        IdeApiVersion defaultConfigVersion = getAndroidProject().getDefaultConfig().getProductFlavor().getMinSdkVersion();
        if (defaultConfigVersion != null) {
          minSdkVersion = defaultConfigVersion;
        }

        List<String> flavors = getSelectedVariant().getProductFlavors();
        for (String flavor : flavors) {
          IdeProductFlavorContainer productFlavor = findProductFlavor(flavor);
          assert productFlavor != null;
          IdeApiVersion flavorVersion = productFlavor.getProductFlavor().getMinSdkVersion();
          if (flavorVersion != null) {
            minSdkVersion = flavorVersion;
            break;
          }
        }
      }
      myMinSdkVersion = convertVersion(minSdkVersion, null);
    }

    return myMinSdkVersion;
  }

  @Override
  @NotNull
  public AndroidVersion getRuntimeMinSdkVersion() {
    IdeApiVersion minSdkVersion = getSelectedVariant().getMinSdkVersion();
    return convertVersion(minSdkVersion, null);
  }

  @Override
  @Nullable
  public AndroidVersion getTargetSdkVersion() {
    IdeApiVersion targetSdkVersion = getSelectedVariant().getTargetSdkVersion();
    return targetSdkVersion != null ? convertVersion(targetSdkVersion, null) : null;
  }

  @Override
  public @NotNull EnumSet<Abi> getSupportedAbis() {
    return getSelectedVariant().getMainArtifact().getAbiFilters()
      .stream().map(Abi::getEnum).collect(Collectors.toCollection(() -> EnumSet.noneOf(Abi.class)));
  }

  /**
   * @return the version code associated with the merged flavor of the selected variant, or {@code null} if none have been set.
   */
  @Nullable
  public Integer getVersionCode() {
    return getSelectedVariant().getVersionCode();
  }

  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return myProjectSystemId;
  }

  @Nullable
  public IdeBuildTypeContainer findBuildType(@NotNull String name) {
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
  public IdeProductFlavorContainer findProductFlavor(@NotNull String name) {
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
    IdeVariant selected = myCachedVariantsByName.get(mySelectedVariantName);
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
    return ImmutableList.copyOf(myCachedVariantsByName.values());
  }

  @Nullable
  public IdeVariant findVariantByName(@NotNull String variantName) {
    return myCachedVariantsByName.get(variantName);
  }

  /**
   * Updates the name of the selected build variant. If the given name does not belong to an existing variant, this method will pick up
   * the first variant, in alphabetical order.
   *
   * @param name the new name.
   */
  public void setSelectedVariantName(@NotNull String name) {
    if (findVariantByName(name) == null) throw new IllegalStateException("Unknown variant: " + name);
    mySelectedVariantName = name;

    // force lazy recompute
    myOverridesManifestPackage = null;
    myMinSdkVersion = null;
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
    Collection<String> names = myAndroidProject.getVariantNames();
    return names != null ? names : myCachedVariantsByName.keySet();
  }

  @Nullable
  public LanguageLevel getJavaLanguageLevel() {
    IdeJavaCompileOptions compileOptions = myAndroidProject.getJavaCompileOptions();
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

      IdeVariant variant = getSelectedVariant();

      List<String> flavors = variant.getProductFlavors();
      for (String flavor : flavors) {
        IdeProductFlavorContainer productFlavor = findProductFlavor(flavor);
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

  /**
   * Returns the {@link IdeAndroidArtifact} that should be used for instrumented testing.
   *
   * <p>For test-only modules this is the main artifact.
   */
  @Nullable
  public IdeAndroidArtifact getArtifactForAndroidTest() {
    return getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_TEST ?
           getSelectedVariant().getMainArtifact() :
           getSelectedVariant().getAndroidTestArtifact();
  }

  @Nullable
  public IdeAndroidArtifact getArtifactForTestFixtures() {
    return getSelectedVariant().getTestFixturesArtifact();
  }

  @Nullable
  public IdeTestOptions.Execution getTestExecutionStrategy() {
    IdeAndroidArtifact artifact = getArtifactForAndroidTest();
    if (artifact != null) {
      IdeTestOptions testOptions = artifact.getTestOptions();
      if (testOptions != null) {
        return testOptions.getExecution();
      }
    }

    return null;
  }

  @NotNull
  @Override
  public Namespacing getNamespacing() {
    IdeAaptOptions.Namespacing namespacing = myAndroidProject.getAaptOptions().getNamespacing();
    switch (namespacing) {
      case DISABLED:
        return Namespacing.DISABLED;
      case REQUIRED:
        return Namespacing.REQUIRED;
      default:
        throw new IllegalStateException("Unknown namespacing option: " + namespacing);
    }
  }

  @NotNull
  @Override
  public Set<Desugaring> getDesugaring() {
    GradleVersion version = getAgpVersion();
    if (version == null) {
      return Desugaring.NONE;
    }

    return getGradleDesugaring(version, getJavaLanguageLevel(), myAndroidProject.getJavaCompileOptions().isCoreLibraryDesugaringEnabled());
  }

  @Override
  @NotNull
  public Map<String, DynamicResourceValue> getResValues() {
    return classFieldsToDynamicResourceValues(getSelectedVariant().getMainArtifact().getResValues());
  }

  @NotNull
  private String getApplicationIdUsingCache(@NotNull String variantName) {
    IdeBuildTasksAndOutputInformation variantOutputInformation =
      variantOutputInformation(myAndroidProject.getVariantsBuildInformation(), variantName);
    if (variantOutputInformation == null) return UNINITIALIZED_APPLICATION_ID;
    return getApplicationIdUsingCache(variantOutputInformation);
  }

  @NotNull
  public String getApplicationIdUsingCache(IdeBuildTasksAndOutputInformation variantOutputInformation) {
    GenericBuiltArtifactsWithTimestamp artifactsWithTimestamp =
      // We do not know which of outputs was built more recently, and thus we need to read (hopefully from the cache) both and compare.
      GenericBuiltArtifactsWithTimestamp.mostRecentNotNull(
        getGenericBuiltArtifactsWithTimestamp(variantOutputInformation, OutputType.Apk),
        getGenericBuiltArtifactsWithTimestamp(variantOutputInformation, OutputType.ApkFromBundle)
      );
    if (artifactsWithTimestamp == null) return UNINITIALIZED_APPLICATION_ID;
    GenericBuiltArtifacts artifacts = artifactsWithTimestamp.getGenericBuiltArtifacts();
    if (artifacts == null) {
      return UNINITIALIZED_APPLICATION_ID;
    }

    return artifacts.getApplicationId();
  }

  @Nullable
  private GenericBuiltArtifactsWithTimestamp getGenericBuiltArtifactsWithTimestamp(@NotNull IdeBuildTasksAndOutputInformation variantOutputInformation,
                                                                                   @NotNull OutputType outputType) {
    String buildOutputListingFile = getOutputListingFile(variantOutputInformation, outputType);
    if (buildOutputListingFile == null) {
      return null;
    }
    GenericBuiltArtifactsWithTimestamp artifactsWithTimestamp = getGenericBuiltArtifactsUsingCache(buildOutputListingFile);
    if (artifactsWithTimestamp.getGenericBuiltArtifacts() == null) {
      return null;
    }
    return artifactsWithTimestamp;
  }

  @NotNull
  private GenericBuiltArtifactsWithTimestamp getGenericBuiltArtifactsUsingCache(@NotNull String buildOutputListingFile) {
    GenericBuiltArtifactsWithTimestamp artifactsWithTimestamp;
    synchronized (myGenericBuiltArtifactsMap) {
      artifactsWithTimestamp = myGenericBuiltArtifactsMap.get(buildOutputListingFile);
      long lastSyncOrBuild = Long.MAX_VALUE; // If we don't have a module default to MAX which will always trigger a re-compute.
      if (myModule != null) {
        lastSyncOrBuild = myModule.getProject().getService(LastBuildOrSyncService.class).getLastBuildOrSyncTimeStamp();
      }
      else {
        Logger.getInstance(GradleAndroidModel.class).warn("No module set on model named: " + myModuleName);
      }
      if (artifactsWithTimestamp == null || lastSyncOrBuild >= artifactsWithTimestamp.getTimeStamp()) {
        // Cache is invalid
        artifactsWithTimestamp =
          new GenericBuiltArtifactsWithTimestamp(loadBuildOutputListingFile(buildOutputListingFile), System.currentTimeMillis());
        myGenericBuiltArtifactsMap.put(buildOutputListingFile, artifactsWithTimestamp);
      }
    }
    return artifactsWithTimestamp;
  }

  @Override
  public @NotNull TestOptions getTestOptions() {
    @Nullable IdeAndroidArtifact testArtifact = getSelectedVariant().getAndroidTestArtifact();
    @Nullable IdeTestOptions testOptions = testArtifact != null ? testArtifact.getTestOptions() : null;
    @Nullable IdeTestOptions.Execution execution = testOptions != null ? testOptions.getExecution() : null;

    TestExecutionOption executionOption;
    if (execution != null) {
      switch (execution) {
        case ANDROID_TEST_ORCHESTRATOR:
          executionOption = TestExecutionOption.ANDROID_TEST_ORCHESTRATOR;
          break;
        case ANDROIDX_TEST_ORCHESTRATOR:
          executionOption = TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR;
          break;
        case HOST:
          executionOption = TestExecutionOption.HOST;
          break;
        default:
          throw new IllegalStateException("Unknown option: " + execution);
      }
    }
    else {
      executionOption = null;
    }

    boolean animationsDisabled = testOptions != null && testOptions.getAnimationsDisabled();

    return new TestOptions(
      executionOption,
      animationsDisabled,
      getSelectedVariant().getTestInstrumentationRunner(),
      getSelectedVariant().getTestInstrumentationRunnerArguments()
    );
  }

  @Override
  public @Nullable String getResourcePrefix() {
    return myAndroidProject.getResourcePrefix();
  }

  @Override
  public boolean isBaseSplit() {
    return myAndroidProject.isBaseSplit();
  }

  @Override
  public boolean isInstantAppCompatible() {
    return getSelectedVariant().getInstantAppCompatible();
  }
}
