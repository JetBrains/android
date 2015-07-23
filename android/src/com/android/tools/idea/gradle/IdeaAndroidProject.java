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
import com.android.sdklib.repository.FullRevision;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_ANDROID_PROJECT;
import static com.android.tools.idea.gradle.customizer.android.ContentRootModuleCustomizer.EXCLUDED_OUTPUT_FOLDER_NAMES;
import static com.android.tools.idea.gradle.util.ProxyUtil.reproxy;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class IdeaAndroidProject implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;
  private static final Logger LOG = Logger.getInstance(IdeaAndroidProject.class);

  @NotNull private ProjectSystemId myProjectSystemId;
  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private AndroidProject myAndroidProject;

  @Nullable private transient CountDownLatch myProxyDelegateLatch;
  @Nullable private AndroidProject myProxyDelegate;

  @SuppressWarnings("NullableProblems") // Set in the constructor.
  @NotNull private String mySelectedVariantName;

  private transient VirtualFile myRootDir;

  @SuppressWarnings("NullableProblems") // Set in the constructor.
  @NotNull private String mySelectedTestArtifactName;

  @Nullable private Boolean myOverridesManifestPackage;
  @Nullable private transient AndroidVersion myMinSdkVersion;

  @NotNull private Map<String, BuildTypeContainer> myBuildTypesByName = Maps.newHashMap();
  @NotNull private Map<String, ProductFlavorContainer> myProductFlavorsByName = Maps.newHashMap();
  @NotNull private Map<String, Variant> myVariantsByName = Maps.newHashMap();

  @NotNull private Set<File> myExtraGeneratedSourceFolders = Sets.newHashSet();

  /**
   * Creates a new {@link IdeaAndroidProject}.
   *
   * @param projectSystemId     the external system used to build the project (e.g. Gradle).
   * @param moduleName          the name of the IDEA module, created from {@code delegate}.
   * @param rootDirPath         the root directory of the imported Android-Gradle project.
   * @param androidProject      imported Android-Gradle project.
   * @param selectedVariantName name of the selected build variant.
   */
  public IdeaAndroidProject(@NotNull ProjectSystemId projectSystemId,
                            @NotNull String moduleName,
                            @NotNull File rootDirPath,
                            @NotNull AndroidProject androidProject,
                            @NotNull String selectedVariantName,
                            @NotNull String selectedTestArtifactName) {
    myProjectSystemId = projectSystemId;
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myAndroidProject = androidProject;

    // Compute the proxy object to avoid re-proxying the model during every serialization operation and also schedule it to run
    // asynchronously to avoid blocking the project sync operation for reproxying to complete.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        myProxyDelegateLatch = new CountDownLatch(1);
        myProxyDelegate = reproxy(AndroidProject.class, myAndroidProject);
        myProxyDelegateLatch.countDown();
      }
    });

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    setSelectedVariantName(selectedVariantName);
    setSelectedTestArtifactName(selectedTestArtifactName);
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
    for (Variant variant : myAndroidProject.getVariants()) {
      myVariantsByName.put(variant.getName(), variant);
    }
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

  @Nullable
  public BaseArtifact findSelectedTestArtifact(@NotNull Variant variant) {
    BaseArtifact artifact = getBaseArtifact(variant.getExtraAndroidArtifacts());
    if (artifact != null) {
      return artifact;
    }
    return getBaseArtifact(variant.getExtraJavaArtifacts());
  }

  @Nullable
  private BaseArtifact getBaseArtifact(@NotNull Iterable<? extends BaseArtifact> artifacts) {
    for (BaseArtifact artifact : artifacts) {
      if (getSelectedTestArtifactName().equals(artifact.getName())) {
        return artifact;
      }
    }
    return null;
  }

  @Nullable
  public BaseArtifact findSelectedTestArtifactInSelectedVariant() {
    return findSelectedTestArtifact(getSelectedVariant());
  }

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
   * @return the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing the
   * build.gradle file.
   */
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

  /**
   * @return the imported Android-Gradle project.
   */
  @NotNull
  public AndroidProject getAndroidProject() {
    return myAndroidProject;
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

  public void setSelectedTestArtifactName(@NotNull String selectedTestArtifactName) {
    assert selectedTestArtifactName.equals(ARTIFACT_ANDROID_TEST) || selectedTestArtifactName.equals(ARTIFACT_UNIT_TEST);
    mySelectedTestArtifactName = selectedTestArtifactName;
  }

  @NotNull
  public String getSelectedTestArtifactName() {
    return mySelectedTestArtifactName;
  }

  @NotNull
  public Collection<SourceProvider> getSourceProvidersForSelectedTestArtifact(@NotNull Iterable<SourceProviderContainer> containers) {
    Set<SourceProvider> providers = Sets.newHashSet();

    for (SourceProviderContainer container : containers) {
      if (mySelectedTestArtifactName.equals(container.getArtifactName())) {
        providers.add(container.getSourceProvider());
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
   * Returns the package name used for the current variant in the given project.
   */
  @NotNull
  public String computePackageName() {
    return getSelectedVariant().getMainArtifact().getApplicationId();
  }

  public boolean isLibrary() {
    return getAndroidProject().isLibrary();
  }

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   *
   * @return true if the manifest package is overridden
   */
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
        ApiVersion defaultConfigVersion  = getAndroidProject().getDefaultConfig().getProductFlavor().getMinSdkVersion();
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
    if (FD_INTERMEDIATES.equals(name) || EXCLUDED_OUTPUT_FOLDER_NAMES.contains(name)) {
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
      if (isAncestor(folderPath, generatedSourceFolder, false)) {
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

  @Nullable
  public Collection<SyncIssue> getSyncIssues() {
    if (supportsIssueReporting()) {
      return myAndroidProject.getSyncIssues();
    }
    return null;
  }

  private boolean supportsIssueReporting() {
    String original = myAndroidProject.getModelVersion();
    FullRevision modelVersion;
    try {
      modelVersion = FullRevision.parseRevision(original);
    } catch (NumberFormatException e) {
      Logger.getInstance(IdeaAndroidProject.class).warn("Failed to parse '" + original + "'", e);
      return false;
    }
    return modelVersion.compareTo(FullRevision.parseRevision("1.1.0")) >= 0;
  }

  @Nullable
  public SourceFileContainerInfo containsSourceFile(@NotNull File file) {
    ProductFlavorContainer defaultConfig = myAndroidProject.getDefaultConfig();
    if (containsSourceFile(defaultConfig, file)) {
      return new SourceFileContainerInfo();
    }
    for (Variant variant : myAndroidProject.getVariants()) {
      AndroidArtifact artifact = variant.getMainArtifact();
      if (containsSourceFile(artifact, file)) {
        return new SourceFileContainerInfo(variant, artifact);
      }
      for (AndroidArtifact extraArtifact : variant.getExtraAndroidArtifacts()) {
        if (containsSourceFile(extraArtifact, file)) {
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

  private static boolean containsSourceFile(@NotNull BaseArtifact artifact, @NotNull File file) {
    if (artifact instanceof AndroidArtifact) {
      AndroidArtifact android = (AndroidArtifact)artifact;
      if (containsFile(android.getGeneratedSourceFolders(), file) || containsFile(android.getGeneratedResourceFolders(), file)) {
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
      if (isAncestor(directory, file, false)) {
        return true;
      }
    }
    return false;
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
        DataNode<IdeaAndroidProject> androidProjectNode = find(moduleNode, IDE_ANDROID_PROJECT);
        if (androidProjectNode != null) {
          androidProjectNode.getData().setSelectedVariantName(variant.getName());
        }
      }
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    if (myProxyDelegateLatch != null) {
      try {
        // If required, wait for the proxy operation to complete.
        myProxyDelegateLatch.await();
      }
      catch (InterruptedException e) {
        LOG.error(e);
        Thread.currentThread().interrupt();
      }
    }

    out.writeObject(myProjectSystemId);
    out.writeObject(myModuleName);
    out.writeObject(myRootDirPath);
    out.writeObject(myProxyDelegate);
    out.writeObject(mySelectedVariantName);
    out.writeObject(mySelectedTestArtifactName);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myProjectSystemId = (ProjectSystemId)in.readObject();
    myModuleName = (String)in.readObject();
    myRootDirPath = (File)in.readObject();
    myAndroidProject = (AndroidProject)in.readObject();
    myProxyDelegate = myAndroidProject;

    myBuildTypesByName = Maps.newHashMap();
    myProductFlavorsByName = Maps.newHashMap();
    myVariantsByName = Maps.newHashMap();
    myExtraGeneratedSourceFolders = Sets.newHashSet();

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    setSelectedVariantName((String)in.readObject());
    setSelectedTestArtifactName((String)in.readObject());
  }
}