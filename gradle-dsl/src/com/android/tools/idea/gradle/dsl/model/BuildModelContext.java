/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS;
import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.EXT_VERSIONS_TOML;
import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.DependencyManager;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFileCache;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion;
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.utils.BuildScriptUtil;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

/**
 * A context object used to hold information relevant to each unique instance of the project/build model.
 * This means there is one {@link BuildModelContext} for each call to the following methods,
 * {@link GradleBuildModel#parseBuildFile(VirtualFile, Project)}, {@link GradleBuildModel#get(Module)}
 * and {@link ProjectBuildModel#get(Project)}. This can be accessed from each of the {@link GradleDslFile}s
 * and their associated models.
 */
public final class BuildModelContext {

  public interface ResolvedConfigurationFileLocationProvider {
    @Nullable
    VirtualFile getGradleBuildFile(@NotNull Module module);

    @Nullable
    @SystemIndependent String getGradleProjectRootPath(@NotNull Module module);

    @Nullable
    @SystemIndependent String getGradleProjectRootPath(@NotNull Project project);
  }

  @NotNull
  private final Project myProject;
  @NotNull
  private final GradleDslFileCache myFileCache;
  @NotNull
  private final ResolvedConfigurationFileLocationProvider myResolvedConfigurationFileLocationProvider;
  @NotNull
  private final Map<GradleDslFile, ClassToInstanceMap<BuildModelNotification>> myNotifications = new HashMap<>();
  @NotNull
  private final DependencyManager myDependencyManager;
  @Nullable
  private GradleBuildFile myRootProjectFile;
  @Nullable
  private AndroidGradlePluginVersion agpVersion;
  @NotNull
  private final Set<GradleVersionCatalogFile> myVersionCatalogFiles = new LinkedHashSet<>();

  public void setRootProjectFile(@NotNull GradleBuildFile rootProjectFile) {
    myRootProjectFile = rootProjectFile;
  }

  @Nullable
  public GradleBuildFile getRootProjectFile() {
    return myRootProjectFile;
  }

  public void setAgpVersion(@Nullable AndroidGradlePluginVersion agpVersion) {
    this.agpVersion = agpVersion;
  }

  @Nullable
  public AndroidGradlePluginVersion getAgpVersion() {
    return agpVersion;
  }

  @NotNull
  public static BuildModelContext create(@NotNull Project project,
                                         @NotNull ResolvedConfigurationFileLocationProvider resolvedConfigurationFileLocationProvider) {
    return new BuildModelContext(project, resolvedConfigurationFileLocationProvider);
  }

  private BuildModelContext(@NotNull Project project,
                            @NotNull ResolvedConfigurationFileLocationProvider resolvedConfigurationFileLocationProvider) {
    myProject = project;
    myFileCache = new GradleDslFileCache(project);
    myResolvedConfigurationFileLocationProvider = resolvedConfigurationFileLocationProvider;
    myDependencyManager = DependencyManager.create();
    myRootProjectFile = null;
  }

  @NotNull
  public DependencyManager getDependencyManager() {
    return myDependencyManager;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<BuildModelNotification> getPublicNotifications(@NotNull GradleDslFile file) {
    return new ArrayList<>(myNotifications.getOrDefault(file, MutableClassToInstanceMap.create()).values());
  }

  @NotNull
  public <T extends BuildModelNotification> T getNotificationForType(@NotNull GradleDslFile file,
                                                                     @NotNull NotificationTypeReference<T> type) {
    ClassToInstanceMap<BuildModelNotification> notificationMap =
      myNotifications.computeIfAbsent(file, (f) -> MutableClassToInstanceMap.create());
    if (notificationMap.containsKey(type.getClazz())) {
      return notificationMap.getInstance(type.getClazz());
    }
    else {
      T notification = type.getConstructor().produce();
      notificationMap.putInstance(type.getClazz(), notification);
      return notification;
    }
  }

  @Nullable
  public VirtualFile getCurrentParsingRoot() {
    return myFileCache.getCurrentParsingRoot();
  }

  /**
   * Resets the state of the build context.
   */
  public void reset() {
    myFileCache.clearAllFiles();
  }

  /* The following methods are just wrappers around the same methods in GradleDslFileCache but pass this build
   * context along as well. */
  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file, @NotNull String name, boolean isApplied) {
    return myFileCache.getOrCreateBuildFile(file, name, this, isApplied);
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file, boolean isApplied) {
    return getOrCreateBuildFile(file, file.getName(), isApplied);
  }

  @NotNull
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull VirtualFile settingsFile) {
    return myFileCache.getOrCreateSettingsFile(settingsFile, this);
  }

  @Nullable
  public GradlePropertiesFile getOrCreatePropertiesFile(@NotNull VirtualFile file, @NotNull String moduleName) {
    return myFileCache.getOrCreatePropertiesFile(file, moduleName, this);
  }

  @NotNull
  public GradleVersionCatalogFile getOrCreateVersionCatalogFile(@NotNull VirtualFile file, @NotNull String catalogName) {
    return myFileCache.getOrCreateVersionCatalogFile(file, catalogName, this);
  }

  /**
   * Parses a build file and produces the {@link GradleBuildFile} that represents it.
   *
   * @param project    the project that the build file belongs to
   * @param file       the build file that should be parsed, this must be a gradle build file
   * @param moduleName the name of the module
   * @param isApplied  whether or not the file should be parsed as if it was applied, if true we do not populate the
   *                   file with the properties found in the subprojects block. This should be true for any file that is not part of the
   *                   main build.gradle structure (i.e project and module files) otherwise we might attempt to parse the file we are parsing
   *                   again leading to a stack overflow.
   * @return the model of the given Gradle file.
   */
  @NotNull
  public GradleBuildFile parseBuildFile(@NotNull Project project,
                                        @NotNull VirtualFile file,
                                        @NotNull String moduleName,
                                        boolean isApplied) {
    GradleBuildFile buildDslFile = new GradleBuildFile(file, project, moduleName, this);
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!isApplied) {
        GradleSettingsModel gradleSettingsModel = getSettingsModel(buildDslFile);
        populateWithParentModuleSubProjectsProperties(buildDslFile, gradleSettingsModel);
      }
      populateSiblingDslFileWithGradlePropertiesFile(buildDslFile);
      buildDslFile.parse();
    });
    return buildDslFile;
  }

  @Nullable
  public GradleBuildFile initializeContext(@NotNull Project project, @Nullable VirtualFile file) {
    // First parse the main project build file.
    GradleBuildFile result = file != null ? new GradleBuildFile(file, project, ":", this) : null;
    if (result != null) {
      setRootProjectFile(result);
      ApplicationManager.getApplication().runReadAction(() -> {
        GradleSettingsModel gradleSettingsModel = getSettingsModel(result);
        populateWithParentModuleSubProjectsProperties(result, gradleSettingsModel);
        populateSiblingDslFileWithGradlePropertiesFile(result);
        populateVersionCatalogFiles(gradleSettingsModel);
        result.parse();
      });
      putBuildFile(file.getUrl(), result);
    }
    else {
      // in case root build file is not there, we still need to parse settings for catalogs
      ApplicationManager.getApplication().runReadAction(() -> {
        VirtualFile maybeSettingsFile = getProjectSettingsFile();
        if (maybeSettingsFile != null) {
          GradleSettingsFile settingsFile = getOrCreateSettingsFile(maybeSettingsFile);
          populateVersionCatalogFiles(new GradleSettingsModelImpl(settingsFile));
        }
      });
    }
    return result;
  }

  private void putBuildFile(@NotNull String name, @NotNull GradleBuildFile buildFile) {
    myFileCache.putBuildFile(name, buildFile);
  }

  @NotNull
  public List<GradleDslFile> getAllRequestedFiles() {
    return myFileCache.getAllFiles();
  }

  @NotNull
  public Set<GradleVersionCatalogFile> getVersionCatalogFiles() {
    return myVersionCatalogFiles;
  }

  private void populateVersionCatalogFiles(@Nullable GradleSettingsModel gradleSettingsModel) {
    if (!GradleDslModelExperimentalSettings.getInstance().isVersionCatalogEnabled()) return;
    if (gradleSettingsModel == null) {
      checkVersionCatalog(VersionCatalogModel.DEFAULT_CATALOG_FILE, VersionCatalogModel.DEFAULT_CATALOG_NAME)
        .ifPresent(myVersionCatalogFiles::add);
      return;
    }
    for (VersionCatalogModel versionCatalogModel : gradleSettingsModel.dependencyResolutionManagement().versionCatalogs()) {
      String from = versionCatalogModel.from().getValue(GradlePropertyModel.STRING_TYPE);
      if (from == null) continue;
      checkVersionCatalog(from, versionCatalogModel.getName()).ifPresent(myVersionCatalogFiles::add);
    }
  }

  private Optional<GradleVersionCatalogFile> checkVersionCatalog(String filePath, String name) {
    @SystemIndependent String fromPath = toSystemIndependentName(filePath);
    @SystemIndependent String rootPath = myResolvedConfigurationFileLocationProvider.getGradleProjectRootPath(getProject());
    @SystemIndependent String path = String.join("/", rootPath, fromPath);
    VirtualFile versionCatalogFile = findFileByIoFile(new File(toSystemDependentName(path)), false);
    if (versionCatalogFile == null) return Optional.empty();
    return Optional.of(getOrCreateVersionCatalogFile(versionCatalogFile, name));
  }

  private void populateSiblingDslFileWithGradlePropertiesFile(@NotNull GradleBuildFile buildDslFile) {
    File propertiesFilePath = new File(buildDslFile.getDirectoryPath(), FN_GRADLE_PROPERTIES);
    VirtualFile propertiesFile = findFileByIoFile(propertiesFilePath, false);
    if (propertiesFile == null) {
      return;
    }

    GradlePropertiesFile parsedProperties = getOrCreatePropertiesFile(propertiesFile, buildDslFile.getName());
    if (parsedProperties == null) {
      return;
    }
    GradlePropertiesModelImpl propertiesModel = new GradlePropertiesModelImpl(parsedProperties);

    GradlePropertiesFile propertiesDslFile = propertiesModel.myGradlePropertiesFile;
    buildDslFile.setPropertiesFile(propertiesDslFile);
  }

  private @Nullable GradleSettingsModel getSettingsModel(@NotNull GradleBuildFile buildDslFile) {
    VirtualFile maybeSettingsFile = buildDslFile.tryToFindSettingsFile();
    if (maybeSettingsFile == null) {
      return null;
    }
    GradleSettingsFile settingsFile = getOrCreateSettingsFile(maybeSettingsFile);
    return new GradleSettingsModelImpl(settingsFile);
  }

  private void populateWithParentModuleSubProjectsProperties(@NotNull GradleBuildFile buildDslFile,
                                                             @Nullable GradleSettingsModel gradleSettingsModel) {
    if (gradleSettingsModel == null) return;

    String modulePath = gradleSettingsModel.moduleWithDirectory(buildDslFile.getDirectoryPath());
    if (modulePath == null) {
      return;
    }

    GradleBuildModel parentModuleModel = gradleSettingsModel.getParentModuleModel(modulePath);
    if (!(parentModuleModel instanceof GradleBuildModelImpl)) {
      return;
    }

    GradleBuildModelImpl parentModuleModelImpl = (GradleBuildModelImpl)parentModuleModel;

    GradleBuildFile parentModuleDslFile = parentModuleModelImpl.myGradleBuildFile;
    buildDslFile.setParentModuleBuildFile(parentModuleDslFile);

    SubProjectsDslElement subProjectsDslElement = parentModuleDslFile.getPropertyElement(SUBPROJECTS);
    if (subProjectsDslElement == null) {
      return;
    }

    buildDslFile.addAppliedProperty(subProjectsDslElement);
    for (Map.Entry<String, GradleDslElement> entry : subProjectsDslElement.getPropertyElements().entrySet()) {
      GradleDslElement element = entry.getValue();
      if (element instanceof ApplyDslElement) {
        ApplyDslElement subProjectsApply = (ApplyDslElement)element;
        ApplyDslElement myApply = new ApplyDslElement(buildDslFile, buildDslFile);
        buildDslFile.setParsedElement(myApply);
        for (GradleDslElement appliedElement : subProjectsApply.getAllElements()) {
          myApply.addParsedElement(appliedElement);
        }
      }
      else {
        buildDslFile.addAppliedProperty(dslTreeCopy(element, buildDslFile));
      }
    }
  }

  private GradleDslElement dslTreeCopy(GradleDslElement element, GradleDslElement parent) {
    return dslTreeCopy(element, parent, new HashMap<>());
  }

  private GradleDslElement dslTreeCopy(GradleDslElement element, GradleDslElement parent, Map<GradleDslElement, GradleDslElement> seen) {
    GradleDslElement previous = seen.get(element);
    if (previous != null) {
      return previous;
    }
    GradleDslElement result;
    if (element instanceof DescribedGradlePropertiesDslElement) {
      // Strictly speaking, it's not the fact that we have a description for the element that matters; what matters is whether this
      // element should be considered to be a leaf in the Dsl tree, or an internal node.  This has more to do with the semantics of our
      // model (i.e. what model operations are permitted, and what their effect should be if those operations are performed on elements
      // included through allprojects/subprojects) than the object structure: consider for example that we need to be able to remove
      // repositories by identity, even though they have block-nature.  For now, this will do: we implement the
      // DescribedGradlePropertiesDslElement interface incrementally on elements where there is an observable problem if it is not
      // implemented.
      PropertiesElementDescription description = ((DescribedGradlePropertiesDslElement<?>)element).getDescription();
      GradlePropertiesDslElement myProperties = description.constructor.construct(parent, GradleNameElement.copy(element.getNameElement()));
      result = myProperties;
      seen.put(element, result);
      for (GradleDslElement subElement : ((GradlePropertiesDslElement)element).getAllElements()) {
        myProperties.addAppliedProperty(dslTreeCopy(subElement, myProperties, seen));
      }
    }
    else {
      result = element;
      seen.put(element, result);
    }
    return result;
  }

  @Nullable
  public VirtualFile getGradleBuildFile(@NotNull Module module) {
    VirtualFile result = myResolvedConfigurationFileLocationProvider.getGradleBuildFile(module);
    if (result != null) return result;

    @SystemIndependent String rootPath = myResolvedConfigurationFileLocationProvider.getGradleProjectRootPath(module);
    if (rootPath == null) return null;
    File moduleRoot = new File(toSystemDependentName(rootPath));
    return getGradleBuildFile(moduleRoot);
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'. This method does not cause a VFS
   * refresh of the file, this should be done by the caller if it is likely that the file has just been created on disk.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the build.gradle file in the directory at the given path, or {@code null} if there is no build.gradle file in the given
   * directory path.
   */
  @Nullable
  public VirtualFile getGradleBuildFile(@NotNull File dirPath) {
    File gradleBuildFilePath = BuildScriptUtil.findGradleBuildFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleBuildFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  /**
   * Returns the VirtualFile corresponding to the Gradle settings file for the given directory, this method will not attempt to refresh the
   * file system which means it is safe to be called from a read action. If the most up to date information is needed then the caller
   * should use {@link BuildScriptUtil#findGradleSettingsFile(File)} along with
   * {@link com.intellij.openapi.vfs.VfsUtil#findFileByIoFile(File, boolean)}
   * to ensure a refresh occurs.
   *
   * @param dirPath the path to find the Gradle settings file for.
   * @return the VirtualFile representing the Gradle settings file or null if it was unable to be found or the file is invalid.
   */
  @Nullable
  public VirtualFile getGradleSettingsFile(@NotNull File dirPath) {
    File gradleSettingsFilePath = BuildScriptUtil.findGradleSettingsFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleSettingsFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  @Nullable
  public VirtualFile getProjectSettingsFile() {
    @SystemIndependent String rootPath = myResolvedConfigurationFileLocationProvider.getGradleProjectRootPath(getProject());
    if (rootPath == null) return null;
    return getGradleSettingsFile(new File(toSystemDependentName(rootPath)));
  }

  public @Nullable VirtualFile getVersionCatalogFile(@NotNull File dirPath, @NotNull String name) {
    File versionCatalogFilePath = new File(dirPath, name + "." + EXT_VERSIONS_TOML);
    VirtualFile result = findFileByIoFile(versionCatalogFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  public @Nullable VirtualFile getVersionCatalogFile(@NotNull String name) {
    @SystemIndependent String rootPath = myResolvedConfigurationFileLocationProvider.getGradleProjectRootPath(getProject());
    if (rootPath == null) return null;
    return getVersionCatalogFile(new File(toSystemDependentName(rootPath), "gradle"), name);
  }
}
