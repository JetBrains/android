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

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleClassJarProvider;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.model.ide.android.*;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependencies;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependenciesFactory;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import static com.android.SdkConstants.DATA_BINDING_LIB_ARTIFACT;
import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.dependsOn;
import static com.android.tools.lint.detector.api.LintUtils.convertVersion;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.util.ArrayUtil.contains;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class AndroidModuleModel implements AndroidModel, ModuleModel {
  public static final String EXPLODED_AAR = "exploded-aar";

  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  private static final String[] TEST_ARTIFACT_NAMES = {ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST};

  @NotNull private ProjectSystemId myProjectSystemId;
  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private IdeAndroidProject myAndroidProject;

  @NotNull private transient AndroidModelFeatures myFeatures;
  @Nullable private transient GradleVersion myModelVersion;
  @NotNull private String mySelectedVariantName;

  private transient VirtualFile myRootDir;

  @Nullable private Boolean myOverridesManifestPackage;
  @Nullable private transient AndroidVersion myMinSdkVersion;

  @NotNull private Map<String, BuildTypeContainer> myBuildTypesByName = Maps.newHashMap();
  @NotNull private Map<String, ProductFlavorContainer> myProductFlavorsByName = Maps.newHashMap();
  @NotNull private Map<String, IdeVariant> myVariantsByName = Maps.newHashMap();

  @NotNull private Set<File> myExtraGeneratedSourceFolders = Sets.newHashSet();

  @Nullable
  public static AndroidModuleModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static AndroidModuleModel get(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = androidFacet.getAndroidModel();
    return androidModel instanceof AndroidModuleModel ? (AndroidModuleModel)androidModel : null;
  }

  /**
   * Creates a new {@link AndroidModuleModel}.
   *
   * @param moduleName          the name of the IDEA module, created from {@code delegate}.
   * @param rootDirPath         the root directory of the imported Android-Gradle project.
   * @param androidProject      imported Android-Gradle project.
   * @param selectedVariantName the name of selected variant.
   * @param dependenciesFactory the factory instance to create {@link IdeDependencies}.
   */
  public AndroidModuleModel(@NotNull String moduleName,
                            @NotNull File rootDirPath,
                            @NotNull AndroidProject androidProject,
                            @NotNull String selectedVariantName,
                            @NotNull IdeDependenciesFactory dependenciesFactory) {
    myProjectSystemId = GRADLE_SYSTEM_ID;
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myAndroidProject = new IdeAndroidProjectImpl(androidProject, dependenciesFactory);
    parseAndSetModelVersion();
    myFeatures = new AndroidModelFeatures(myModelVersion);

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    mySelectedVariantName = findVariantToSelect(selectedVariantName);
  }

  private void populateBuildTypesByName() {
    for (BuildTypeContainer container : myAndroidProject.getBuildTypes()) {
      String name = container.getBuildType().getName();
      myBuildTypesByName.put(name, container);
    }
  }

  private void populateProductFlavorsByName() {
    for (ProductFlavorContainer container : myAndroidProject.getProductFlavors()) {
      String name = container.getProductFlavor().getName();
      myProductFlavorsByName.put(name, container);
    }
  }

  private void populateVariantsByName() {
    myAndroidProject.forEachVariant(variant -> myVariantsByName.put(variant.getName(), variant));
  }

  /**
   * @deprecated Use {@link #getSelectedMainCompileLevel2Dependencies()}
   */
  @Deprecated
  @NotNull
  public Dependencies getSelectedMainCompileDependencies() {
    AndroidArtifact mainArtifact = getMainArtifact();
    return mainArtifact.getDependencies();
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

  @Override
  @NotNull
  public SourceProvider getDefaultSourceProvider() {
    return getAndroidProject().getDefaultConfig().getSourceProvider();
  }

  @Override
  @NotNull
  public List<SourceProvider> getActiveSourceProviders() {
    return getMainSourceProviders(mySelectedVariantName);
  }

  @NotNull
  public List<SourceProvider> getMainSourceProviders(@NotNull String variantName) {
    Variant variant = myVariantsByName.get(variantName);
    if (variant == null) {
      getLogger().error("Unknown variant name '" + variantName + "' found in the module '" + myModuleName + "'");
      return ImmutableList.of();
    }

    List<SourceProvider> providers = Lists.newArrayList();
    // Main source provider.
    providers.add(getDefaultSourceProvider());
    // Flavor source providers.
    for (String flavor : variant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.add(productFlavor.getSourceProvider());
    }

    // Multi-flavor source provider.
    AndroidArtifact mainArtifact = variant.getMainArtifact();
    SourceProvider multiFlavorProvider = mainArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorProvider != null) {
      providers.add(multiFlavorProvider);
    }

    // Build type source provider.
    BuildTypeContainer buildType = findBuildType(variant.getBuildType());
    assert buildType != null;
    providers.add(buildType.getSourceProvider());

    // Variant  source provider.
    SourceProvider variantProvider = mainArtifact.getVariantSourceProvider();
    if (variantProvider != null) {
      providers.add(variantProvider);
    }
    return providers;
  }

  @NotNull
  public Collection<SourceProvider> getTestSourceProviders(@NotNull Iterable<SourceProviderContainer> containers) {
    return getSourceProvidersForArtifacts(containers, TEST_ARTIFACT_NAMES);
  }

  @Override
  @NotNull
  public List<SourceProvider> getTestSourceProviders() {
    return getTestSourceProviders(mySelectedVariantName, TEST_ARTIFACT_NAMES);
  }

  @NotNull
  public List<SourceProvider> getTestSourceProviders(@NotNull String artifactName) {
    return getTestSourceProviders(mySelectedVariantName, artifactName);
  }

  @NotNull
  public List<SourceProvider> getTestSourceProviders(@NotNull String variantName, @NotNull String... testArtifactNames) {
    validateTestArtifactNames(testArtifactNames);

    List<SourceProvider> providers = Lists.newArrayList();
    // Collect the default config test source providers.
    Collection<SourceProviderContainer> extraSourceProviders = getAndroidProject().getDefaultConfig().getExtraSourceProviders();
    providers.addAll(getSourceProvidersForArtifacts(extraSourceProviders, testArtifactNames));

    Variant variant = myVariantsByName.get(variantName);
    assert variant != null;

    // Collect the product flavor test source providers.
    for (String flavor : variant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.addAll(getSourceProvidersForArtifacts(productFlavor.getExtraSourceProviders(), testArtifactNames));
    }

    // Collect the build type test source providers.
    BuildTypeContainer buildType = findBuildType(variant.getBuildType());
    assert buildType != null;
    providers.addAll(getSourceProvidersForArtifacts(buildType.getExtraSourceProviders(), testArtifactNames));

    // TODO: Does it make sense to add multi-flavor test source providers?
    // TODO: Does it make sense to add variant test source providers?
    return providers;
  }

  private static void validateTestArtifactNames(@NotNull String[] testArtifactNames) {
    for (String name : testArtifactNames) {
      if (!isTestArtifact(name)) {
        String msg = String.format("'%1$s' is not a test artifact", name);
        throw new IllegalArgumentException(msg);
      }
    }
  }

  private static boolean isTestArtifact(@Nullable String artifactName) {
    return contains(artifactName, TEST_ARTIFACT_NAMES);
  }

  @Override
  @NotNull
  public List<SourceProvider> getAllSourceProviders() {
    Collection<Variant> variants = myAndroidProject.getVariants();
    List<SourceProvider> providers = Lists.newArrayList();

    // Add main source set
    providers.add(getDefaultSourceProvider());

    // Add all flavors
    Collection<ProductFlavorContainer> flavors = myAndroidProject.getProductFlavors();
    for (ProductFlavorContainer pfc : flavors) {
      providers.add(pfc.getSourceProvider());
    }

    // Add the multi-flavor source providers
    for (Variant v : variants) {
      SourceProvider provider = v.getMainArtifact().getMultiFlavorSourceProvider();
      if (provider != null) {
        providers.add(provider);
      }
    }

    // Add all the build types
    Collection<BuildTypeContainer> buildTypes = myAndroidProject.getBuildTypes();
    for (BuildTypeContainer btc : buildTypes) {
      providers.add(btc.getSourceProvider());
    }

    // Add all the variant source providers
    for (Variant v : variants) {
      SourceProvider provider = v.getMainArtifact().getVariantSourceProvider();
      if (provider != null) {
        providers.add(provider);
      }
    }

    return providers;
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return getSelectedVariant().getMainArtifact().getApplicationId();
  }

  @NotNull
  @Override
  public Set<String> getAllApplicationIds() {
    Set<String> ids = Sets.newHashSet();
    for (Variant v : myAndroidProject.getVariants()) {
      String applicationId = v.getMergedFlavor().getApplicationId();
      if (applicationId != null) {
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
  @Override
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

      if (minSdkVersion != null) {
        myMinSdkVersion = convertVersion(minSdkVersion, null);
      }
      else {
        myMinSdkVersion = NOT_SPECIFIED;
      }
    }

    return myMinSdkVersion != NOT_SPECIFIED ? myMinSdkVersion : null;
  }

  @Nullable
  @Override
  public AndroidVersion getRuntimeMinSdkVersion() {
    ApiVersion minSdkVersion = getSelectedVariant().getMergedFlavor().getMinSdkVersion();
    if (minSdkVersion != null) {
      return convertVersion(minSdkVersion, null);
    }
    return null;
  }

  @Nullable
  @Override
  public AndroidVersion getTargetSdkVersion() {
    ApiVersion targetSdkVersion = getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
    if (targetSdkVersion != null) {
      return convertVersion(targetSdkVersion, null);
    }
    return null;
  }

  /**
   * @return the version code associated with the merged flavor of the selected variant, or {@code null} if none have been set.
   */
  @Nullable
  @Override
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
  @Override
  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  /**
   * @return the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing the
   * build.gradle file.
   */
  @Override
  @NotNull
  public VirtualFile getRootDir() {
    if (myRootDir == null) {
      VirtualFile found = findFileByIoFile(myRootDirPath, true);
      // the module's root directory can never be null.
      assert found != null;
      myRootDir = found;
    }
    return myRootDir;
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

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(AndroidModuleModel.class);
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

  @Nullable
  public Variant findVariantByName(@NotNull String variantName) {
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

  @NotNull
  private String findVariantToSelect(@NotNull String variantName) {
    Collection<String> variantNames = getVariantNames();
    String newVariantName;
    if (variantNames.contains(variantName)) {
      newVariantName = variantName;
    }
    else {
      List<String> sorted = Lists.newArrayList(variantNames);
      Collections.sort(sorted);
      // AndroidProject has always at least 2 variants (debug and release.)
      newVariantName = sorted.get(0);
    }
    return newVariantName;
  }

  @NotNull
  private static Collection<SourceProvider> getSourceProvidersForArtifacts(@NotNull Iterable<SourceProviderContainer> containers,
                                                                           @NotNull String... artifactNames) {
    Set<SourceProvider> providers = Sets.newHashSet();
    for (SourceProviderContainer container : containers) {
      for (String artifactName : artifactNames) {
        if (artifactName.equals(container.getArtifactName())) {
          providers.add(container.getSourceProvider());
          break;
        }
      }
    }
    return providers;
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
    return myVariantsByName.keySet();
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

  /**
   * Registers the path of a source folder that has been incorrectly generated outside of the default location (${buildDir}/generated.)
   *
   * @param folderPath the path of the generated source folder.
   */
  public void registerExtraGeneratedSourceFolder(@NotNull File folderPath) {
    myExtraGeneratedSourceFolders.add(folderPath);
  }

  @NotNull
  public List<File> getExcludedFolderPaths() {
    File buildFolderPath = getAndroidProject().getBuildFolder();
    List<File> excludedFolderPaths = Lists.newArrayList();

    if (buildFolderPath.isDirectory()) {
      for (File folderPath : notNullize(buildFolderPath.listFiles())) {
        String folderName = folderPath.getName();
        if (folderName.equals(FD_INTERMEDIATES) || folderName.equals(FD_GENERATED)) {
          // Folders 'intermediates' and 'generated' are never excluded (some children of 'intermediates' are excluded though.)
          continue;
        }
        excludedFolderPaths.add(folderPath);
      }
      File intermediates = new File(buildFolderPath, FD_INTERMEDIATES);
      if (intermediates.isDirectory()) {
        for (File folderPath : notNullize(intermediates.listFiles())) {
          String folderName = folderPath.getName();
          if (folderName.equals(EXPLODED_AAR) || folderName.equals("manifest")) {
            continue;
          }
          excludedFolderPaths.add(folderPath);
        }
      }
    }
    else {
      // We know these folders have to be always excluded
      excludedFolderPaths.add(new File(buildFolderPath, FD_OUTPUTS));
      excludedFolderPaths.add(new File(buildFolderPath, "tmp"));
    }

    return excludedFolderPaths;
  }

  /**
   * @return the paths of generated sources placed at the wrong location (not in ${build}/generated.)
   */
  @NotNull
  public File[] getExtraGeneratedSourceFolderPaths() {
    return myExtraGeneratedSourceFolders.toArray(new File[myExtraGeneratedSourceFolders.size()]);
  }

  @Nullable
  public Collection<SyncIssue> getSyncIssues() {
    if (getFeatures().isIssueReportingSupported()) {
      return myAndroidProject.getSyncIssues();
    }
    return null;
  }

  @Nullable
  public SourceFileContainerInfo containsSourceFile(@NotNull File file) {
    ProductFlavorContainer defaultConfig = myAndroidProject.getDefaultConfig();
    if (containsSourceFile(defaultConfig, file)) {
      return new SourceFileContainerInfo();
    }
    for (IdeVariant variant : myVariantsByName.values()) {
      IdeAndroidArtifact artifact = variant.getMainArtifact();
      if (containsSourceFile(artifact, file)) {
        return new SourceFileContainerInfo(variant, artifact);
      }
      for (AndroidArtifact extraArtifact : variant.getExtraAndroidArtifacts()) {
        if (containsSourceFile((IdeAndroidArtifact)extraArtifact, file)) {
          return new SourceFileContainerInfo(variant, extraArtifact);
        }
      }
      String buildTypeName = variant.getBuildType();
      BuildTypeContainer buildTypeContainer = findBuildType(buildTypeName);
      if (buildTypeContainer != null) {
        if (containsFile(buildTypeContainer.getSourceProvider(), file)) {
          return new SourceFileContainerInfo(variant);
        }
        for (SourceProviderContainer extraSourceProvider : buildTypeContainer.getExtraSourceProviders()) {
          if (containsFile(extraSourceProvider.getSourceProvider(), file)) {
            return new SourceFileContainerInfo(variant);
          }
        }
      }
      for (String flavorName : variant.getProductFlavors()) {
        ProductFlavorContainer flavor = findProductFlavor(flavorName);
        if (flavor != null && containsSourceFile(flavor, file)) {
          return new SourceFileContainerInfo(variant);
        }
      }
    }

    return null; // not found.
  }

  private static boolean containsSourceFile(@NotNull ProductFlavorContainer flavorContainer, @NotNull File file) {
    if (containsFile(flavorContainer.getSourceProvider(), file)) {
      return true;
    }
    // Test source roots
    for (SourceProviderContainer extraSourceProvider : flavorContainer.getExtraSourceProviders()) {
      if (containsFile(extraSourceProvider.getSourceProvider(), file)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsSourceFile(@NotNull IdeBaseArtifact artifact, @NotNull File file) {
    if (artifact instanceof AndroidArtifact) {
      AndroidArtifact androidArtifact = (AndroidArtifact)artifact;
      if (containsFile(androidArtifact.getGeneratedSourceFolders(), file) ||
          containsFile(androidArtifact.getGeneratedResourceFolders(), file)) {
        return true;
      }
    }
    SourceProvider sourceProvider = artifact.getVariantSourceProvider();
    if (sourceProvider != null && containsFile(sourceProvider, file)) {
      return true;
    }
    sourceProvider = artifact.getMultiFlavorSourceProvider();
    return sourceProvider != null && containsFile(sourceProvider, file);
  }

  private static boolean containsFile(@NotNull SourceProvider sourceProvider, @NotNull File file) {
    return containsFile(sourceProvider.getAidlDirectories(), file) ||
           containsFile(sourceProvider.getAssetsDirectories(), file) ||
           containsFile(sourceProvider.getCDirectories(), file) ||
           containsFile(sourceProvider.getCppDirectories(), file) ||
           containsFile(sourceProvider.getJavaDirectories(), file) ||
           containsFile(sourceProvider.getRenderscriptDirectories(), file) ||
           containsFile(sourceProvider.getResDirectories(), file) ||
           containsFile(sourceProvider.getResourcesDirectories(), file);
  }

  private static boolean containsFile(@NotNull Collection<File> directories, @NotNull File file) {
    for (File directory : directories) {
      if (FileUtil.isAncestor(directory, file, false)) {
        return true;
      }
    }
    return false;
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

  public static class SourceFileContainerInfo {
    @Nullable public final Variant variant;
    @Nullable public final BaseArtifact artifact;

    SourceFileContainerInfo() {
      this(null);
    }

    SourceFileContainerInfo(@Nullable Variant variant) {
      this(variant, null);
    }

    SourceFileContainerInfo(@Nullable Variant variant, @Nullable BaseArtifact artifact) {
      this.variant = variant;
      this.artifact = artifact;
    }

    public void updateSelectedVariantIn(@NotNull DataNode<ModuleData> moduleNode) {
      if (variant != null) {
        DataNode<AndroidModuleModel> androidProjectNode = find(moduleNode, ANDROID_MODEL);
        if (androidProjectNode != null) {
          androidProjectNode.getData().setSelectedVariantName(variant.getName());
        }
      }
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(myProjectSystemId);
    out.writeObject(myModuleName);
    out.writeObject(myRootDirPath);
    out.writeObject(myAndroidProject);
    out.writeObject(mySelectedVariantName);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myProjectSystemId = (ProjectSystemId)in.readObject();
    myModuleName = (String)in.readObject();
    myRootDirPath = (File)in.readObject();
    myAndroidProject = (IdeAndroidProject)in.readObject();

    parseAndSetModelVersion();
    myFeatures = new AndroidModelFeatures(myModelVersion);

    myBuildTypesByName = Maps.newHashMap();
    myProductFlavorsByName = Maps.newHashMap();
    myVariantsByName = Maps.newHashMap();
    myExtraGeneratedSourceFolders = Sets.newHashSet();

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    setSelectedVariantName((String)in.readObject());
  }

  private void parseAndSetModelVersion() {
    // Old plugin versions do not return model version.
    myModelVersion = GradleVersion.tryParse(myAndroidProject.getModelVersion());
  }

  /**
   * Returns the source provider for the current build type, which will never be {@code null} for a project backed by an
   * {@link AndroidProject}, and always {@code null} for a legacy Android project.
   *
   * @return the build type source set or {@code null}.
   */
  @NotNull
  public SourceProvider getBuildTypeSourceProvider() {
    Variant selectedVariant = getSelectedVariant();
    BuildTypeContainer buildType = findBuildType(selectedVariant.getBuildType());
    assert buildType != null;
    return buildType.getSourceProvider();
  }

  /**
   * Returns the source providers for the available flavors, which will never be {@code null} for a project backed by an
   * {@link AndroidProject}, and always {@code null} for a legacy Android project.
   *
   * @return the flavor source providers or {@code null} in legacy projects.
   */
  @NotNull
  public List<SourceProvider> getFlavorSourceProviders() {
    Variant selectedVariant = getSelectedVariant();
    List<String> productFlavors = selectedVariant.getProductFlavors();
    List<SourceProvider> providers = Lists.newArrayList();
    for (String flavor : productFlavors) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.add(productFlavor.getSourceProvider());
    }
    return providers;
  }

  public void syncSelectedVariantAndTestArtifact(@NotNull AndroidFacet facet) {
    IdeVariant variant = getSelectedVariant();
    JpsAndroidModuleProperties state = facet.getProperties();
    state.SELECTED_BUILD_VARIANT = variant.getName();

    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();

    // When multi test artifacts are enabled, test tasks are computed dynamically.
    updateGradleTaskNames(state, mainArtifact);
  }

  private static void updateGradleTaskNames(@NotNull JpsAndroidModuleProperties state, @NotNull IdeAndroidArtifact mainArtifact) {
    state.ASSEMBLE_TASK_NAME = mainArtifact.getAssembleTaskName();
    state.COMPILE_JAVA_TASK_NAME = mainArtifact.getCompileTaskName();
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet(mainArtifact.getIdeSetupTaskNames());

    state.ASSEMBLE_TEST_TASK_NAME = "";
    state.COMPILE_JAVA_TEST_TASK_NAME = "";
  }

  /**
   * Returns the source provider specific to the flavor combination, if any.
   *
   * @return the source provider or {@code null}.
   */
  @Nullable
  public SourceProvider getMultiFlavorSourceProvider() {
    AndroidArtifact mainArtifact = getSelectedVariant().getMainArtifact();
    return mainArtifact.getMultiFlavorSourceProvider();
  }

  /**
   * Returns the source provider specific to the variant, if any.
   *
   * @return the source provider or {@code null}.
   */
  @Nullable
  public SourceProvider getVariantSourceProvider() {
    AndroidArtifact mainArtifact = getSelectedVariant().getMainArtifact();
    return mainArtifact.getVariantSourceProvider();
  }

  @Override
  public boolean getDataBindingEnabled() {
    return dependsOn(this, DATA_BINDING_LIB_ARTIFACT);
  }

  @Override
  @NotNull
  public ClassJarProvider getClassJarProvider() {
    return new AndroidGradleClassJarProvider();
  }

  @Override
  public boolean isClassFileOutOfDate(@NotNull Module module, @NotNull String fqcn, @NotNull VirtualFile classFile) {
    Project project = module.getProject();
    GlobalSearchScope scope = module.getModuleWithDependenciesScope();
    VirtualFile sourceFile =
      ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqcn, scope);
        if (psiClass == null) {
          return null;
        }
        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile == null) {
          return null;
        }
        return psiFile.getVirtualFile();
      });

    if (sourceFile == null) {
      return false;
    }

    // Edited but not yet saved?
    if (FileDocumentManager.getInstance().isFileModified(sourceFile)) {
      return true;
    }

    // Check timestamp
    long sourceFileModified = sourceFile.getTimeStamp();

    // User modifications on the source file might not always result on a new .class file.
    // We use the project modification time instead to display the warning more reliably.
    long lastBuildTimestamp = classFile.getTimeStamp();
    Long projectBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).getLastBuildTimestamp();
    if (projectBuildTimestamp != null) {
      lastBuildTimestamp = projectBuildTimestamp;
    }
    return sourceFileModified > lastBuildTimestamp && lastBuildTimestamp > 0L;
  }
}
