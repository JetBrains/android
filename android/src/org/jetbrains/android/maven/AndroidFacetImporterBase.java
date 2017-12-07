/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.maven;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.ui.CustomNotificationListener;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.facet.FacetType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.ZipUtil;
import org.jdom.Element;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidDexCompilerConfiguration;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModuleImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.jps.android.model.impl.AndroidImportableProperty;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidFacetImporterBase extends FacetImporter<AndroidFacet, AndroidFacetConfiguration, AndroidFacetType> {
  private static final String DEX_CORE_LIBRARY_PROPERTY = "dexCoreLibrary";

  public static volatile String ANDROID_SDK_PATH_TEST = null;

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.maven.AndroidFacetImporterBase");

  private static final Key<Boolean> MODULE_IMPORTED = Key.create("ANDROID_NEWLY_CREATED_KEY");
  @NonNls private static final String DEFAULT_NATIVE_ARCHITECTURE = "armeabi";

  private static final Key<Boolean> DELETE_OBSOLETE_MODULE_TASK_KEY = Key.create("DELETE_OBSOLETE_MODULE_TASK");
  private static final Key<Set<MavenId>> RESOLVED_APKLIB_ARTIFACTS_KEY = Key.create("RESOLVED_APKLIB_ARTIFACTS");
  private static final Key<Map<MavenId, String>> IMPORTED_AAR_ARTIFACTS = Key.create("IMPORTED_AAR_ARTIFACTS");

  public AndroidFacetImporterBase(@NotNull String groupId, @NotNull String pluginId) {
    super(groupId, pluginId, FacetType.findInstance(AndroidFacetType.class));
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return ArrayUtil.find(getSupportedPackagingTypes(), mavenProject.getPackaging()) >= 0 &&
           super.isApplicable(mavenProject);
  }


  @Override
  public void getSupportedPackagings(Collection<String> result) {
    result.addAll(Arrays.asList(getSupportedPackagingTypes()));
  }

  @NotNull
  private static String[] getSupportedPackagingTypes() {
    return new String[]{AndroidMavenUtil.APK_PACKAGING_TYPE, AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE,
      AndroidMavenUtil.AAR_DEPENDENCY_AND_PACKAGING_TYPE};
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    result.add(AndroidMavenUtil.APKSOURCES_DEPENDENCY_TYPE);
    result.add(AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE);
    result.add(AndroidMavenUtil.AAR_DEPENDENCY_AND_PACKAGING_TYPE);
  }

  @Override
  protected void setupFacet(AndroidFacet facet, MavenProject mavenProject) {
    String mavenProjectDirPath = FileUtil.toSystemIndependentName(mavenProject.getDirectory());
    facet.getConfiguration().init(facet.getModule(), mavenProjectDirPath);
    AndroidMavenProviderImpl.setPathsToDefault(mavenProject, facet.getModule(), facet.getConfiguration());

    final boolean hasApkSources = AndroidMavenProviderImpl.hasApkSourcesDependency(mavenProject);
    AndroidMavenProviderImpl.configureAaptCompilation(mavenProject, facet.getModule(), facet.getConfiguration(), hasApkSources);
    final String packaging = mavenProject.getPackaging();

    if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(packaging) ||
        AndroidMavenUtil.AAR_DEPENDENCY_AND_PACKAGING_TYPE.equals(packaging)) {
      facet.setProjectType(PROJECT_TYPE_LIBRARY);
    }
    facet.getConfiguration().setIncludeAssetsFromLibraries(true);

    if (hasApkSources) {
      AndroidUtils.reportImportErrorToEventLog("'apksources' dependency is deprecated and can be poorly supported by IDE. " +
                                               "It is strongly recommended to use 'apklib' dependency instead.",
                                               facet.getModule().getName(), facet.getModule().getProject());
    }

    if (Boolean.parseBoolean(findConfigValue(mavenProject, DEX_CORE_LIBRARY_PROPERTY))) {
      AndroidDexCompilerConfiguration.getInstance(facet.getModule().getProject()).CORE_LIBRARY = true;
    }
  }

  @Override
  protected void reimportFacet(IdeModifiableModelsProvider modelsProvider,
                               Module module,
                               MavenRootModelAdapter rootModel,
                               AndroidFacet facet,
                               MavenProjectsTree mavenTree,
                               MavenProject mavenProject,
                               MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    configurePaths(facet, mavenProject);
    facet.getProperties().ENABLE_MANIFEST_MERGING = Boolean.parseBoolean(findConfigValue(mavenProject, "mergeManifests"));
    facet.getProperties().COMPILE_CUSTOM_GENERATED_SOURCES = false;

    configureAndroidPlatform(facet, mavenProject, modelsProvider);
    final Project project = module.getProject();
    importExternalAndroidLibDependencies(project, rootModel, modelsProvider, mavenTree, mavenProject, mavenProjectToModuleName,
                                         postTasks);

    if (hasAndroidLibDependencies(mavenProject) &&
        MavenProjectsManager.getInstance(project).getImportingSettings().isUseMavenOutput()) {
      // IDEA's apklibs building model is different from Maven's one, so we cannot use the same
      rootModel.useModuleOutput(mavenProject.getBuildDirectory() + "/idea-classes",
                                mavenProject.getBuildDirectory() + "/idea-test-classes");
    }
    project.putUserData(DELETE_OBSOLETE_MODULE_TASK_KEY, Boolean.TRUE);
    postTasks.add(new MyDeleteObsoleteApklibModulesTask(project));

    // exclude folders where Maven generates sources if gen source roots were changed by user manually
    final AndroidFacetConfiguration defaultConfig = new AndroidFacetConfiguration();
    AndroidMavenProviderImpl.setPathsToDefault(mavenProject, module, defaultConfig);

    if (!defaultConfig.getState().GEN_FOLDER_RELATIVE_PATH_APT.equals(
      facet.getProperties().GEN_FOLDER_RELATIVE_PATH_APT)) {
      final String rPath = mavenProject.getGeneratedSourcesDirectory(false) + "/r";
      rootModel.unregisterAll(rPath, false, true);
      rootModel.addExcludedFolder(rPath);
    }

    if (!defaultConfig.getState().GEN_FOLDER_RELATIVE_PATH_AIDL.equals(
      facet.getProperties().GEN_FOLDER_RELATIVE_PATH_AIDL)) {
      final String aidlPath = mavenProject.getGeneratedSourcesDirectory(false) + "/aidl";
      rootModel.unregisterAll(aidlPath, false, true);
      rootModel.addExcludedFolder(aidlPath);
    }

    if (facet.isLibraryProject()) {
      removeAttachedJarDependency(modelsProvider, mavenTree, mavenProject);
    }
  }

  private static void removeAttachedJarDependency(IdeModifiableModelsProvider modelsProvider,
                                                  MavenProjectsTree mavenTree,
                                                  MavenProject mavenProject) {
    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      final MavenProject depProject = mavenTree.findProject(depArtifact);

      if (depProject == null) {
        continue;
      }
      final String attachedJarsLibName = MavenModuleImporter.getAttachedJarsLibName(depArtifact);
      final Library attachedJarsLib = modelsProvider.getLibraryByName(attachedJarsLibName);

      if (attachedJarsLib != null) {
        final Library.ModifiableModel attachedJarsLibModel = modelsProvider.getModifiableLibraryModel(attachedJarsLib);

        if (attachedJarsLibModel != null) {
          final String targetJarPath = depProject.getBuildDirectory() + "/" + depProject.getFinalName() + ".jar";

          for (String url : attachedJarsLibModel.getUrls(OrderRootType.CLASSES)) {
            if (FileUtil.pathsEqual(targetJarPath, JpsPathUtil.urlToPath(url))) {
              attachedJarsLibModel.removeRoot(url, OrderRootType.CLASSES);
            }
          }
        }
      }
    }
  }

  private void importNativeDependencies(@NotNull AndroidFacet facet, @NotNull MavenProject mavenProject, @NotNull String moduleDirPath) {
    final List<AndroidNativeLibData> additionalNativeLibs = new ArrayList<>();
    final String localRepository = MavenProjectsManager.getInstance(facet.getModule().getProject()).getLocalRepository().getPath();

    String defaultArchitecture = getPathFromConfig(facet.getModule(), mavenProject, moduleDirPath,
                                                   "nativeLibrariesDependenciesHardwareArchitectureDefault", false, true);
    if (defaultArchitecture == null) {
      defaultArchitecture = DEFAULT_NATIVE_ARCHITECTURE;
    }
    final String forcedArchitecture = getPathFromConfig(facet.getModule(), mavenProject, moduleDirPath,
                                                        "nativeLibrariesDependenciesHardwareArchitectureOverride", false, true);

    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      if (AndroidMavenUtil.SO_PACKAGING_AND_DEPENDENCY_TYPE.equals(depArtifact.getType())) {
        final String architecture;
        if (forcedArchitecture != null) {
          architecture = forcedArchitecture;
        }
        else {
          final String classifier = depArtifact.getClassifier();
          architecture = classifier != null ? classifier : defaultArchitecture;
        }
        final String path = FileUtil.toSystemIndependentName(localRepository + '/' + depArtifact.getRelativePath());
        final String artifactId = depArtifact.getArtifactId();
        final String targetFileName = artifactId.startsWith("lib") ? artifactId + ".so" : "lib" + artifactId + ".so";
        additionalNativeLibs.add(new AndroidNativeLibData(architecture, path, targetFileName));
      }
    }
    facet.getConfiguration().setAdditionalNativeLibraries(additionalNativeLibs);
  }

  private static boolean hasAndroidLibDependencies(@NotNull MavenProject mavenProject) {
    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      final String type = depArtifact.getType();

      if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(type) ||
          AndroidMavenUtil.AAR_DEPENDENCY_AND_PACKAGING_TYPE.equals(type)) {
        return true;
      }
    }
    return false;
  }

  private static void importExternalAndroidLibDependencies(Project project,
                                                           MavenRootModelAdapter rootModelAdapter,
                                                           IdeModifiableModelsProvider modelsProvider,
                                                           MavenProjectsTree mavenTree,
                                                           MavenProject mavenProject,
                                                           Map<MavenProject, String> mavenProject2ModuleName,
                                                           List<MavenProjectsProcessorTask> tasks) {
    final ModifiableRootModel rootModel = rootModelAdapter.getRootModel();
    removeUselessDependencies(rootModel, modelsProvider, mavenProject);

    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      if (mavenTree.findProject(depArtifact) != null) {
        continue;
      }
      final String type = depArtifact.getType();

      if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(type)) {
        final AndroidExternalApklibDependenciesManager.MavenDependencyInfo depInfo =
          AndroidExternalApklibDependenciesManager.MavenDependencyInfo.create(depArtifact);

        final String apklibModuleName = doImportExternalApklibDependency(
          project, modelsProvider, mavenTree, mavenProject,
          mavenProject2ModuleName, tasks, depInfo);

        if (ArrayUtil.find(rootModel.getDependencyModuleNames(), apklibModuleName) < 0) {
          final DependencyScope scope = getApklibModuleDependencyScope(depArtifact);

          if (scope != null) {
            addModuleDependency(modelsProvider, rootModel, apklibModuleName, scope);
          }
        }
      }
      else if (AndroidMavenUtil.AAR_DEPENDENCY_AND_PACKAGING_TYPE.equals(type) &&
               MavenConstants.SCOPE_COMPILE.equals(depArtifact.getScope())) {
        importExternalAarDependency(depArtifact, mavenProject, mavenTree, rootModelAdapter, modelsProvider, project, tasks);
      }
    }
  }

  @Nullable
  private static String findExtractedAarDirectory(@NotNull List<MavenProject> allProjects, @NotNull String dirName) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();

    for (MavenProject project : allProjects) {
      final VirtualFile file = lfs.refreshAndFindFileByPath(AndroidMavenUtil.getGenExternalApklibDirInProject(project) + "/" + dirName);

      if (file != null) {
        return file.getPath();
      }
    }
    return null;
  }

  private static void importExternalAarDependency(@NotNull MavenArtifact artifact,
                                                  @NotNull MavenProject mavenProject,
                                                  @NotNull MavenProjectsTree mavenTree,
                                                  @NotNull MavenRootModelAdapter rootModelAdapter,
                                                  @NotNull IdeModifiableModelsProvider modelsProvider,
                                                  @NotNull Project project,
                                                  @NotNull List<MavenProjectsProcessorTask> postTasks) {
    final Library aarLibrary = rootModelAdapter.findLibrary(artifact);

    if (aarLibrary == null) {
      return;
    }
    final MavenId mavenId = artifact.getMavenId();
    Map<MavenId, String> importedAarArtifacts = project.getUserData(IMPORTED_AAR_ARTIFACTS);

    if (importedAarArtifacts == null) {
      importedAarArtifacts = new HashMap<>();
      project.putUserData(IMPORTED_AAR_ARTIFACTS, importedAarArtifacts);

      postTasks.add((project1, embeddersManager, console, indicator) -> project1.putUserData(IMPORTED_AAR_ARTIFACTS, null));
    }
    final List<MavenProject> allProjects = mavenTree.getProjects();
    String aarDirPath = importedAarArtifacts.get(mavenId);

    if (aarDirPath == null) {
      final String aarDirName = AndroidMavenUtil.getMavenIdStringForFileName(mavenId);
      aarDirPath = findExtractedAarDirectory(allProjects, aarDirName);

      if (aarDirPath == null) {
        final String genDirPath = AndroidMavenUtil.computePathForGenExternalApklibsDir(mavenId, mavenProject, allProjects);

        if (genDirPath == null) {
          return;
        }
        aarDirPath = genDirPath + "/" + aarDirName;
      }
      importedAarArtifacts.put(mavenId, aarDirPath);
      extractArtifact(artifact.getPath(), aarDirPath, project, mavenProject.getName());
    }
    final Library.ModifiableModel aarLibModel = modelsProvider.getModifiableLibraryModel(aarLibrary);
    final String classesJarPath = aarDirPath + "/" + SdkConstants.FN_CLASSES_JAR;
    final String classesJarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, classesJarPath) +
                                 JarFileSystem.JAR_SEPARATOR;
    final String resDirUrl = VfsUtilCore.pathToUrl(aarDirPath + "/" + SdkConstants.FD_RES);
    final Set<String> urlsToAdd = new HashSet<>(Arrays.asList(classesJarUrl, resDirUrl));
    collectJarsInAarLibsFolder(aarDirPath, urlsToAdd);

    for (String url : aarLibModel.getUrls(OrderRootType.CLASSES)) {
      if (!urlsToAdd.remove(url)) {
        aarLibModel.removeRoot(url, OrderRootType.CLASSES);
      }
    }
    for (String url : urlsToAdd) {
      aarLibModel.addRoot(url, OrderRootType.CLASSES);
    }
  }

  private static void collectJarsInAarLibsFolder(@NotNull String aarDirPath, @NotNull Set<String> urlsToAdd) {
    final File libsFolder = new File(aarDirPath, SdkConstants.LIBS_FOLDER);

    if (!libsFolder.isDirectory()) {
      return;
    }
    final File[] children = libsFolder.listFiles();

    if (children != null) {
      for (File child : children) {
        if (FileUtilRt.extensionEquals(child.getName(), "jar")) {
          final String url = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, FileUtil.
            toSystemIndependentName(child.getPath())) + JarFileSystem.JAR_SEPARATOR;
          urlsToAdd.add(url);
        }
      }
    }
  }

  private static String doImportExternalApklibDependency(Project project,
                                                         IdeModifiableModelsProvider modelsProvider,
                                                         MavenProjectsTree mavenTree,
                                                         MavenProject mavenProject,
                                                         Map<MavenProject, String> mavenProject2ModuleName,
                                                         List<MavenProjectsProcessorTask> tasks,
                                                         AndroidExternalApklibDependenciesManager.MavenDependencyInfo depInfo) {
    final MavenId depArtifactMavenId = new MavenId(depInfo.getGroupId(), depInfo.getArtifactId(), depInfo.getVersion());
    final ModifiableModuleModel moduleModel = modelsProvider.getModifiableModuleModel();
    final String apklibModuleName = AndroidMavenUtil.getModuleNameForExtApklibArtifact(depArtifactMavenId);
    Module apklibModule = moduleModel.findModuleByName(apklibModuleName);

    if ((apklibModule == null || apklibModule.getUserData(MODULE_IMPORTED) == null) &&
        MavenConstants.SCOPE_COMPILE.equals(depInfo.getScope())) {
      apklibModule =
        importExternalApklibArtifact(project, apklibModule, modelsProvider, mavenProject, mavenTree, depArtifactMavenId,
                                     depInfo.getPath(), moduleModel, mavenProject2ModuleName);
      if (apklibModule != null) {
        apklibModule.putUserData(MODULE_IMPORTED, Boolean.TRUE);
        final Module finalGenModule = apklibModule;

        tasks.add((project1, embeddersManager, console, indicator) -> finalGenModule.putUserData(MODULE_IMPORTED, null));
        final MavenArtifactResolvedInfo resolvedDepArtifact =
          AndroidExternalApklibDependenciesManager.getInstance(project).getResolvedInfoForArtifact(depArtifactMavenId);

        if (resolvedDepArtifact != null) {
          for (AndroidExternalApklibDependenciesManager.MavenDependencyInfo depDepInfo : resolvedDepArtifact.getDependencies()) {
            final MavenId depDepMavenId = new MavenId(depDepInfo.getGroupId(), depDepInfo.getArtifactId(), depDepInfo.getVersion());

            if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(depDepInfo.getType()) &&
                mavenTree.findProject(depDepMavenId) == null) {
              doImportExternalApklibDependency(project, modelsProvider, mavenTree, mavenProject,
                                               mavenProject2ModuleName, tasks, depDepInfo);
            }
          }
        }
        else {
          AndroidUtils.reportImportErrorToEventLog("Cannot find resolved info for artifact " + depArtifactMavenId.getKey(),
                                                   apklibModuleName, project);
        }
      }
    }
    return apklibModuleName;
  }

  @Nullable
  private static DependencyScope getApklibModuleDependencyScope(@NotNull MavenArtifact apklibArtifact) {
    final String scope = apklibArtifact.getScope();

    if (MavenConstants.SCOPE_COMPILE.equals(scope)) {
      return DependencyScope.COMPILE;
    }
    else if (MavenConstants.SCOPE_PROVIDED.equals(scope)) {
      return DependencyScope.PROVIDED;
    }
    else if (MavenConstants.SCOPE_TEST.equals(scope)) {
      return DependencyScope.TEST;
    }
    return null;
  }

  private static void removeUselessDependencies(ModifiableRootModel modifiableRootModel,
                                                IdeModifiableModelsProvider modelsProvider, MavenProject mavenProject) {
    for (OrderEntry entry : modifiableRootModel.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module depModule = ((ModuleOrderEntry)entry).getModule();
        if (depModule != null && AndroidMavenUtil.isExtApklibModule(depModule)) {
          modifiableRootModel.removeOrderEntry(entry);
        }
      }
      else if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libOrderEntry = (LibraryOrderEntry)entry;

        if (containsDependencyOnApklibFile(libOrderEntry, modelsProvider) ||
            pointsIntoUnpackedLibsDir(libOrderEntry, modelsProvider, mavenProject)) {
          modifiableRootModel.removeOrderEntry(entry);
        }
      }
    }
  }

  private static boolean pointsIntoUnpackedLibsDir(@NotNull LibraryOrderEntry entry,
                                                   @NotNull IdeModifiableModelsProvider provider,
                                                   @NotNull MavenProject mavenProject) {
    final Library library = entry.getLibrary();

    if (library == null) {
      return false;
    }
    final Library.ModifiableModel libraryModel = provider.getModifiableLibraryModel(library);
    final String[] urls = libraryModel.getUrls(OrderRootType.CLASSES);
    final String unpackedLibsDir = FileUtil.toCanonicalPath(mavenProject.getBuildDirectory()) + "/unpacked-libs";

    for (String url : urls) {
      if (VfsUtilCore.urlToPath(url).startsWith(unpackedLibsDir)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsDependencyOnApklibFile(@NotNull LibraryOrderEntry libraryOrderEntry,
                                                        @NotNull IdeModifiableModelsProvider modelsProvider) {
    final Library library = libraryOrderEntry.getLibrary();

    if (library == null) {
      return false;
    }
    final Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    final String[] urls = libraryModel.getUrls(OrderRootType.CLASSES);

    for (String url : urls) {
      final String fileName = PathUtil.getFileName(PathUtil.toPresentableUrl(url));

      if (FileUtilRt.extensionEquals(fileName, "apklib")) {
        return true;
      }
    }
    return false;
  }

  private static void addModuleDependency(@NotNull IdeModifiableModelsProvider modelsProvider,
                                          @NotNull ModifiableRootModel rootModel,
                                          @NotNull final String moduleName,
                                          @NotNull DependencyScope compile) {
    if (findModuleDependency(rootModel, moduleName) != null) {
      return;
    }

    final Module module = modelsProvider.getModifiableModuleModel().findModuleByName(moduleName);

    final ModuleOrderEntry entry = module != null
                                   ? rootModel.addModuleOrderEntry(module)
                                   : rootModel.addInvalidModuleEntry(moduleName);
    entry.setScope(compile);
  }

  private static ModuleOrderEntry findModuleDependency(ModifiableRootModel rootModel, final String moduleName) {
    final Ref<ModuleOrderEntry> result = Ref.create(null);

    rootModel.orderEntries().forEach(entry -> {
      if (entry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleEntry = (ModuleOrderEntry)entry;
        final String name = moduleEntry.getModuleName();
        if (moduleName.equals(name)) {
          result.set(moduleEntry);
        }
      }
      return true;
    });

    return result.get();
  }

  @Nullable
  private static Module importExternalApklibArtifact(Project project,
                                                     Module apklibModule,
                                                     IdeModifiableModelsProvider modelsProvider,
                                                     MavenProject mavenProject,
                                                     MavenProjectsTree mavenTree,
                                                     MavenId artifactMavenId,
                                                     String artifactFilePath,
                                                     ModifiableModuleModel moduleModel,
                                                     Map<MavenProject, String> mavenProject2ModuleName) {
    final String genModuleName = AndroidMavenUtil.getModuleNameForExtApklibArtifact(artifactMavenId);
    String genExternalApklibsDirPath = null;
    String targetDirPath = null;

    if (apklibModule == null) {
      genExternalApklibsDirPath =
        AndroidMavenUtil.computePathForGenExternalApklibsDir(artifactMavenId, mavenProject, mavenTree.getProjects());

      targetDirPath = genExternalApklibsDirPath != null
                      ? genExternalApklibsDirPath + '/' + AndroidMavenUtil.getMavenIdStringForFileName(artifactMavenId)
                      : null;
    }
    else {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(apklibModule).getContentRoots();
      if (contentRoots.length == 1) {
        targetDirPath = contentRoots[0].getPath();
      }
      else {
        final String moduleDir = new File(apklibModule.getModuleFilePath()).getParent();
        if (moduleDir != null) {
          targetDirPath = moduleDir + '/' + AndroidMavenUtil.getMavenIdStringForFileName(artifactMavenId);
        }
      }
    }

    if (targetDirPath == null) {
      return null;
    }

    if (!extractArtifact(artifactFilePath, targetDirPath, project, genModuleName)){
      return null;
    }
    final AndroidExternalApklibDependenciesManager adm = AndroidExternalApklibDependenciesManager.getInstance(project);
    adm.setArtifactFilePath(artifactMavenId, FileUtil.toSystemIndependentName(artifactFilePath));

    final VirtualFile vApklibDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(targetDirPath);
    if (vApklibDir == null) {
      LOG.error("Cannot find file " + targetDirPath + " in VFS");
      return null;
    }

    if (apklibModule == null) {
      final String genModuleFilePath = genExternalApklibsDirPath + '/' + genModuleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
      apklibModule = moduleModel.newModule(genModuleFilePath, StdModuleTypes.JAVA.getId());
    }

    final ModifiableRootModel apklibModuleModel = modelsProvider.getModifiableRootModel(apklibModule);
    final ContentEntry contentEntry = apklibModuleModel.addContentEntry(vApklibDir);

    final VirtualFile sourceRoot = vApklibDir.findChild(AndroidMavenUtil.APK_LIB_ARTIFACT_SOURCE_ROOT);
    if (sourceRoot != null) {
      contentEntry.addSourceFolder(sourceRoot, false);
    }
    final AndroidFacet facet = AndroidUtils.addAndroidFacet(apklibModuleModel.getModule(), vApklibDir, true);

    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    String s = AndroidRootUtil.getPathRelativeToModuleDir(apklibModule, vApklibDir.getPath());
    if (s != null) {
      s = !s.isEmpty() ? '/' + s + '/' : "/";
      configuration.getState().RES_FOLDER_RELATIVE_PATH = s + AndroidMavenUtil.APK_LIB_ARTIFACT_RES_DIR;
      configuration.getState().LIBS_FOLDER_RELATIVE_PATH = s + AndroidMavenUtil.APK_LIB_ARTIFACT_NATIVE_LIBS_DIR;
      configuration.getState().MANIFEST_FILE_RELATIVE_PATH = s + AndroidMavenUtil.APK_LIB_ARTIFACT_MANIFEST_FILE;
    }

    importSdkAndDependenciesForApklibArtifact(project, apklibModuleModel, modelsProvider, mavenTree,
                                              artifactMavenId, mavenProject2ModuleName);
    return apklibModule;
  }

  private static boolean extractArtifact(String zipFilePath, String targetDirPath, Project project, String moduleName) {
    final File targetDir = new File(targetDirPath);
    if (targetDir.exists()) {
      if (!FileUtil.delete(targetDir)) {
        AndroidUtils.reportImportErrorToEventLog("Cannot delete old " + targetDirPath, moduleName, project);
        return false;
      }
    }

    if (!targetDir.mkdirs()) {
      AndroidUtils.reportImportErrorToEventLog("Cannot create directory " + targetDirPath, moduleName, project);
      return false;
    }
    final File artifactFile = new File(zipFilePath);

    if (artifactFile.exists()) {
      try {
        ZipUtil.extract(artifactFile, targetDir, null);
      }
      catch (IOException e) {
        reportIoErrorToEventLog(e, moduleName, project);
        return false;
      }
    }
    else {
      AndroidUtils.reportImportErrorToEventLog("Cannot find file " + artifactFile.getPath(), moduleName, project);
    }
    return true;
  }

  private static void reportIoErrorToEventLog(IOException e, String moduleName, Project project) {
    final String message = e.getMessage();

    if (message == null) {
      LOG.error(e);
    }
    else {
      AndroidUtils.reportImportErrorToEventLog("I/O error: " + message, moduleName, project);
    }
  }

  private static void importSdkAndDependenciesForApklibArtifact(Project project,
                                                                ModifiableRootModel apklibModuleModel,
                                                                IdeModifiableModelsProvider modelsProvider,
                                                                MavenProjectsTree mavenTree,
                                                                MavenId artifactMavenId,
                                                                Map<MavenProject, String> mavenProject2ModuleName) {
    final String apklibModuleName = apklibModuleModel.getModule().getName();
    final AndroidExternalApklibDependenciesManager adm = AndroidExternalApklibDependenciesManager.getInstance(project);
    final MavenArtifactResolvedInfo resolvedInfo =
      adm.getResolvedInfoForArtifact(artifactMavenId);

    for (OrderEntry entry : apklibModuleModel.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry || entry instanceof LibraryOrderEntry) {
        apklibModuleModel.removeOrderEntry(entry);
      }
    }

    if (resolvedInfo != null) {
      final String apiLevel = resolvedInfo.getApiLevel();
      final Sdk sdk = findOrCreateAndroidPlatform(apiLevel, null);

      if (sdk != null) {
        apklibModuleModel.setSdk(sdk);
      }
      else {
        reportCannotFindAndroidPlatformError(apklibModuleName, apiLevel, project);
      }

      for (AndroidExternalApklibDependenciesManager.MavenDependencyInfo depArtifactInfo : resolvedInfo.getDependencies()) {
        final MavenId depMavenId = new MavenId(depArtifactInfo.getGroupId(), depArtifactInfo.getArtifactId(),
                                               depArtifactInfo.getVersion());

        final String type = depArtifactInfo.getType();
        final String scope = depArtifactInfo.getScope();
        final String path = depArtifactInfo.getPath();
        final String libName = depArtifactInfo.getLibName();

        if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(type) &&
            MavenConstants.SCOPE_COMPILE.equals(scope)) {
          final MavenProject depProject = mavenTree.findProject(depMavenId);

          if (depProject != null) {
            final String depModuleName = mavenProject2ModuleName.get(depProject);

            if (depModuleName != null) {
              addModuleDependency(modelsProvider, apklibModuleModel, depModuleName, DependencyScope.COMPILE);
            }
          }
          else {
            final String depApklibGenModuleName = AndroidMavenUtil.getModuleNameForExtApklibArtifact(depMavenId);
            addModuleDependency(modelsProvider, apklibModuleModel, depApklibGenModuleName, DependencyScope.COMPILE);
          }
        }
        else {
          final DependencyScope depScope = MavenModuleImporter.selectScope(scope);

          if (scope != null) {
            addLibraryDependency(libName, depScope, modelsProvider, apklibModuleModel, path);
          }
          else {
            LOG.info("Unknown Maven scope " + depScope);
          }
        }
      }
    }
    else {
      AndroidUtils.reportImportErrorToEventLog("Cannot find sdk info for artifact " + artifactMavenId.getKey(), apklibModuleName,
                                               project);
    }
  }

  private static void addLibraryDependency(@NotNull String libraryName,
                                           @NotNull DependencyScope scope,
                                           @NotNull IdeModifiableModelsProvider provider,
                                           @NotNull ModifiableRootModel model,
                                           @NotNull String path) {
    // let's use the same format for libraries imported from Gradle, to be compatible with API like ExternalSystemApiUtil.isExternalSystemLibrary()
    // and be able to reuse common cleanup service, see LibraryDataService.postProcess()
    String prefix = GradleConstants.SYSTEM_ID.getReadableName() + ": ";
    libraryName = libraryName.isEmpty() || StringUtil.startsWith(libraryName, prefix) ? libraryName : prefix + libraryName;

    Library library = provider.getLibraryByName(libraryName);

    if (library == null) {
      library = provider.createLibrary(libraryName);
    }
    Library.ModifiableModel libraryModel = provider.getModifiableLibraryModel(library);
    updateUrl(libraryModel, path);
    final LibraryOrderEntry entry = model.addLibraryEntry(library);
    entry.setScope(scope);
  }

  private static void updateUrl(@NotNull Library.ModifiableModel library, @NotNull String path) {
    final OrderRootType type = OrderRootType.CLASSES;
    final String newUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path) + JarFileSystem.JAR_SEPARATOR;
    boolean urlExists = false;

    for (String url : library.getUrls(type)) {
      if (newUrl.equals(url)) {
        urlExists = true;
      }
      else {
        library.removeRoot(url, type);
      }
    }

    if (!urlExists) {
      library.addRoot(newUrl, type);
    }
  }

  private static void reportCannotFindAndroidPlatformError(String moduleName, @Nullable String apiLevel, Project project) {
    final OpenAndroidSdkManagerHyperlink hyperlink = new OpenAndroidSdkManagerHyperlink();
    AndroidUtils.reportImportErrorToEventLog(
      "Cannot find appropriate Android platform" + (apiLevel != null ? " for API level " + apiLevel : "") +
      ". " + hyperlink.toHtml(),
      moduleName, project, new CustomNotificationListener(project, hyperlink));
  }

  @Override
  public void resolve(final Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context)
    throws MavenProcessCanceledException {
    final AndroidExternalApklibDependenciesManager adm =
      AndroidExternalApklibDependenciesManager.getInstance(project);

    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      final MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

      if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(depArtifact.getType()) &&
          mavenProjectsManager.findProject(depArtifact) == null &&
          MavenConstants.SCOPE_COMPILE.equals(depArtifact.getScope())) {

        Set<MavenId> resolvedArtifacts = context.getUserData(RESOLVED_APKLIB_ARTIFACTS_KEY);

        if (resolvedArtifacts == null) {
          resolvedArtifacts = new HashSet<>();
          context.putUserData(RESOLVED_APKLIB_ARTIFACTS_KEY, resolvedArtifacts);
        }
        if (resolvedArtifacts.add(depArtifact.getMavenId())) {
          doResolveApklibArtifact(project, depArtifact, embedder, mavenProjectsManager, mavenProject.getName(), adm, context);
        }
      }
    }
  }

  @Nullable
  private static File buildFakeArtifactPomFile(@NotNull MavenArtifact artifact, @Nullable String moduleName, @NotNull Project project) {
    File tmpFile = null;
    try {
      tmpFile = FileUtil.createTempFile("intellij_fake_artifat_pom", "tmp");
      FileUtil.writeToFile(
        tmpFile,
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
        "  <modelVersion>4.0.0</modelVersion>\n" +
        "  <groupId>intellij-fake-artifact-group</groupId>\n" +
        "  <artifactId>intellij-fake-artifact</artifactId>\n" +
        "  <version>1.0-SNAPSHOT</version>\n" +
        "  <packaging>jar</packaging>\n" +
        "  <name>Fake</name>" +
        "  <dependencies>" +
        "    <dependency>" +
        "      <groupId>" + artifact.getGroupId() + "</groupId>" +
        "      <artifactId>" + artifact.getArtifactId() + "</artifactId>" +
        "      <version>" + artifact.getVersion() + "</version>" +
        "    </dependency>" +
        "  </dependencies>" +
        "</project>");
      return tmpFile;
    }
    catch (IOException e) {
      reportIoErrorToEventLog(e, moduleName, project);

      if (tmpFile != null) {
        FileUtil.delete(tmpFile);
      }
      return null;
    }
  }

  private void doResolveApklibArtifact(Project project,
                                       MavenArtifact artifact,
                                       MavenEmbedderWrapper embedder,
                                       MavenProjectsManager mavenProjectsManager,
                                       String moduleName,
                                       AndroidExternalApklibDependenciesManager adm,
                                       ResolveContext context) throws MavenProcessCanceledException {
    final File depArtifacetFile = new File(FileUtil.getNameWithoutExtension(artifact.getPath()) + ".pom");
    if (!depArtifacetFile.exists()) {
      AndroidUtils.reportImportErrorToEventLog("Cannot find file " + depArtifacetFile.getPath(), moduleName, project);
      return;
    }

    final VirtualFile vDepArtifactFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(depArtifacetFile);
    if (vDepArtifactFile == null) {
      AndroidUtils.reportImportErrorToEventLog("Cannot find file " + depArtifacetFile.getPath() + " in VFS", moduleName, project);
      return;
    }

    final MavenProject projectForExternalApklib = new MavenProject(vDepArtifactFile);
    final MavenGeneralSettings generalSettings = mavenProjectsManager.getGeneralSettings();
    final MavenProjectReader mavenProjectReader = new MavenProjectReader(project);

    final MavenProjectReaderProjectLocator locator = coordinates -> null;
    final MavenArtifactResolvedInfo info = new MavenArtifactResolvedInfo();
    final MavenId mavenId = artifact.getMavenId();
    adm.setResolvedInfoForArtifact(mavenId, info);

    projectForExternalApklib.read(generalSettings, mavenProjectsManager.getExplicitProfiles(), mavenProjectReader, locator);
    projectForExternalApklib.resolve(project, generalSettings, embedder, mavenProjectReader, locator, context);

    final String apiLevel = getPlatformFromConfig(projectForExternalApklib);

    final List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> dependencies = new ArrayList<>();

    List<MavenArtifact> deps = projectForExternalApklib.getDependencies();

    if (deps.isEmpty()) {
      // Hack for solving IDEA-119450. Maven reports "unknown packaging 'apklib'" when resolving if android plugin is not specified
      // in the "build" section of the pom, so we create fake jar artifact dependent on the apklib artifact and resolve it
      final File fakePomFile = buildFakeArtifactPomFile(artifact, moduleName, project);

      if (fakePomFile != null) {
        try {
          final VirtualFile vFakePomFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fakePomFile);

          if (vFakePomFile != null) {
            final MavenProject fakeProject = new MavenProject(vFakePomFile);
            fakeProject.read(generalSettings, mavenProjectsManager.getExplicitProfiles(), mavenProjectReader, locator);
            fakeProject.resolve(project, generalSettings, embedder, mavenProjectReader, locator, context);
            deps = fakeProject.getDependencies();

            for (Iterator<MavenArtifact> it = deps.iterator(); it.hasNext(); ) {
              final MavenArtifact dep = it.next();

              if (dep.getMavenId().equals(mavenId)) {
                it.remove();
              }
             }
          }
          else {
            LOG.error("Cannot find file " + fakePomFile.getPath() + " in the VFS");
          }
        }
        finally {
          FileUtil.delete(fakePomFile);
        }
      }
    }
    for (MavenArtifact depArtifact : deps) {
      dependencies.add(AndroidExternalApklibDependenciesManager.MavenDependencyInfo.create(depArtifact));
    }
    info.setApiLevel(apiLevel != null ? apiLevel : "");
    info.setDependencies(dependencies);
  }

  private void configureAndroidPlatform(AndroidFacet facet, MavenProject project, IdeModifiableModelsProvider modelsProvider) {
    final ModifiableRootModel model = modelsProvider.getModifiableRootModel(facet.getModule());
    configureAndroidPlatform(project, model);
  }

  private void configureAndroidPlatform(MavenProject project, ModifiableRootModel model) {
    final Sdk currentSdk = model.getSdk();

    if (currentSdk == null || !isAppropriateSdk(currentSdk, project)) {
      final String apiLevel = getPlatformFromConfig(project);
      final String predefinedSdkPath = getSdkPathFromConfig(project);
      final Sdk platformLib = findOrCreateAndroidPlatform(apiLevel, predefinedSdkPath);

      if (platformLib != null) {
        model.setSdk(platformLib);
      }
      else {
        reportCannotFindAndroidPlatformError(model.getModule().getName(), apiLevel, model.getProject());
      }
    }
  }

  private boolean isAppropriateSdk(@NotNull Sdk sdk, MavenProject mavenProject) {
    if (!AndroidSdks.getInstance().isAndroidSdk(sdk)) {
      return false;
    }
    final String platformId = getPlatformFromConfig(mavenProject);
    final AndroidPlatform androidPlatform = AndroidPlatform.getInstance(sdk);
    if (androidPlatform == null) {
      return false;
    }

    final IAndroidTarget target = androidPlatform.getTarget();
    return (platformId == null || AndroidSdkUtils.targetHasId(target, platformId)) &&
           AndroidSdkUtils.checkSdkRoots(sdk, target, true);
  }

  @Nullable
  private static Sdk findOrCreateAndroidPlatform(String apiLevel, String predefinedSdkPath) {
    if (predefinedSdkPath != null) {
      final Sdk sdk = doFindOrCreateAndroidPlatform(predefinedSdkPath, apiLevel);
      if (sdk != null) {
        return sdk;
      }
    }
    String sdkPath;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      sdkPath = ANDROID_SDK_PATH_TEST;
    }
    else {
      sdkPath = System.getenv(SdkConstants.ANDROID_HOME_ENV);
    }
    LOG.info("android home: " + sdkPath);

    if (sdkPath != null) {
      final Sdk sdk = doFindOrCreateAndroidPlatform(sdkPath, apiLevel);
      if (sdk != null) {
        return sdk;
      }
    }

    final Collection<File> candidates = AndroidSdks.getInstance().getAndroidSdkPathsFromExistingPlatforms();
    LOG.info("suggested sdks: " + candidates);

    for (File candidate : candidates) {
      final Sdk sdk = doFindOrCreateAndroidPlatform(candidate.getPath(), apiLevel);
      if (sdk != null) {
        return sdk;
      }
    }
    return null;
  }

  @Nullable
  private static Sdk doFindOrCreateAndroidPlatform(@Nullable String sdkPath, @Nullable String apiLevel) {
    if (sdkPath != null) {
      AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdkPath);
      if (sdkData != null) {
        IAndroidTarget target = apiLevel != null && !apiLevel.isEmpty()
                                ? sdkData.findTargetByApiLevel(apiLevel)
                                : findNewestPlatformTarget(sdkData);
        if (target != null) {
          Sdk library = AndroidSdkUtils.findAppropriateAndroidPlatform(target, sdkData, true);
          if (library == null) {
            library = createNewAndroidSdkForMaven(toSystemDependentPath(sdkPath), target);
          }
          return library;
        }
      }
    }
    return null;
  }

  @Nullable
  private static IAndroidTarget findNewestPlatformTarget(AndroidSdkData data) {
    IAndroidTarget result = null;

    for (IAndroidTarget target : data.getTargets()) {
      if (target.isPlatform() && (result == null || result.getVersion().compareTo(target.getVersion()) < 0)) {
        result = target;
      }
    }
    return result;
  }

  @Nullable
  private static Sdk createNewAndroidSdkForMaven(File sdkPath, IAndroidTarget target) {
    AndroidSdks androidSdks = AndroidSdks.getInstance();
    Sdk sdk = null;
    Sdk jdk = Jdks.getInstance().chooseOrCreateJavaSdk();
    if (jdk != null) {
      String sdkName = "Maven " + androidSdks.chooseNameForNewLibrary(target);
      sdk = androidSdks.create(target, sdkPath, sdkName, jdk, false);
    }

    if (sdk == null) {
      return null;
    }
    SdkModificator modificator = sdk.getSdkModificator();

    for (OrderRoot root : androidSdks.getLibraryRootsForTarget(target, sdkPath, false)) {
      modificator.addRoot(root.getFile(), root.getType());
    }
    AndroidSdkAdditionalData data = androidSdks.getAndroidSdkAdditionalData(sdk);

    if (data != null) {
      final Sdk javaSdk = data.getJavaSdk();

      if (javaSdk != null) {
        for (VirtualFile file : javaSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
          modificator.addRoot(file, OrderRootType.CLASSES);
        }
      }
      else {
        LOG.error("AndroidSdkUtils.createNewAndroidPlatform should return Android SDK with a valid JDK reference, or return null");
      }
    }
    modificator.commitChanges();
    return sdk;
  }

  @Nullable
  private String getPlatformFromConfig(MavenProject project) {
    Element sdkRoot = getConfig(project, "sdk");
    if (sdkRoot != null) {
      Element platform = sdkRoot.getChild("platform");
      if (platform != null) {
        return platform.getValue();
      }
    }
    final String platformFromProperty =
      project.getProperties().getProperty("android.sdk.platform");

    if (platformFromProperty != null) {
      return platformFromProperty;
    }
    return null;
  }

  @Nullable
  private String getSdkPathFromConfig(MavenProject project) {
    Element sdkRoot = getConfig(project, "sdk");
    if (sdkRoot != null) {
      Element path = sdkRoot.getChild("path");
      if (path != null) {
        return path.getValue();
      }
    }
    final String pathFromProperty =
      project.getProperties().getProperty("android.sdk.path");

    if (pathFromProperty != null) {
      return pathFromProperty;
    }
    return null;
  }

  private void configurePaths(AndroidFacet facet, MavenProject project) {
    Module module = facet.getModule();
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    if (moduleDirPath == null) {
      return;
    }
    AndroidFacetConfiguration configuration = facet.getConfiguration();

    if (configuration.isImportedProperty(AndroidImportableProperty.RESOURCES_DIR_PATH)) {
      String resFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceDirectory", true, true);

      if (resFolderRelPath != null && isFullyResolved(resFolderRelPath)) {
        configuration.getState().RES_FOLDER_RELATIVE_PATH = '/' + resFolderRelPath;
      }
      String resFolderForCompilerRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceDirectory", false, true);

      if (resFolderForCompilerRelPath != null &&
          !resFolderForCompilerRelPath.equals(resFolderRelPath)) {
        configuration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER = true;
        configuration.getState().CUSTOM_APK_RESOURCE_FOLDER = '/' + resFolderForCompilerRelPath;
        configuration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK = true;
      }
    }
    configuration.getState().RES_OVERLAY_FOLDERS = Collections.singletonList("/res-overlay");

    Element resourceOverlayDirectories = getConfig(project, "resourceOverlayDirectories");
    if (resourceOverlayDirectories != null) {
      List<String> dirs = new ArrayList<>();
      for (Object child : resourceOverlayDirectories.getChildren()) {
        String dir = ((Element)child).getTextTrim();
        if (dir != null && !dir.isEmpty()) {
          String relativePath = getRelativePath(moduleDirPath, makePath(project, dir));
          if (relativePath != null && !relativePath.isEmpty()) {
            dirs.add('/' + relativePath);
          }
        }
      }
      if (!dirs.isEmpty()) {
        configuration.getState().RES_OVERLAY_FOLDERS = dirs;
      }
    }
    else {
      String resOverlayFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceOverlayDirectory", true, true);
      if (resOverlayFolderRelPath != null && isFullyResolved(resOverlayFolderRelPath)) {
        configuration.getState().RES_OVERLAY_FOLDERS = Collections.singletonList('/' + resOverlayFolderRelPath);
      }
    }

    if (configuration.isImportedProperty(AndroidImportableProperty.ASSETS_DIR_PATH)) {
      String assetsFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "assetsDirectory", false, true);
      if (assetsFolderRelPath != null && isFullyResolved(assetsFolderRelPath)) {
        configuration.getState().ASSETS_FOLDER_RELATIVE_PATH = '/' + assetsFolderRelPath;
      }
    }

    if (configuration.isImportedProperty(AndroidImportableProperty.MANIFEST_FILE_PATH)) {
      String manifestFileRelPath = getPathFromConfig(module, project, moduleDirPath, "androidManifestFile", true, false);
      if (manifestFileRelPath != null && isFullyResolved(manifestFileRelPath)) {
        configuration.getState().MANIFEST_FILE_RELATIVE_PATH = '/' + manifestFileRelPath;
      }

      String manifestFileForCompilerRelPath = getPathFromConfig(module, project, moduleDirPath, "androidManifestFile", false, false);
      if (manifestFileForCompilerRelPath != null &&
          !manifestFileForCompilerRelPath.equals(manifestFileRelPath) &&
          isFullyResolved(manifestFileForCompilerRelPath)) {
        configuration.getState().USE_CUSTOM_COMPILER_MANIFEST = true;
        configuration.getState().CUSTOM_COMPILER_MANIFEST = '/' + manifestFileForCompilerRelPath;
        configuration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK = true;
      }
    }

    if (MavenProjectsManager.getInstance(module.getProject()).getImportingSettings().isUseMavenOutput()) {
      final String buildDirectory = FileUtil.toSystemIndependentName(project.getBuildDirectory());
      final String buildDirRelPath = FileUtil.getRelativePath(moduleDirPath, buildDirectory, '/');
      configuration.getState().APK_PATH = '/' + buildDirRelPath + '/' + AndroidCompileUtil.getApkName(module);
    }
    else {
      configuration.getState().APK_PATH = "";
    }
    if (configuration.isImportedProperty(AndroidImportableProperty.NATIVE_LIBS_DIR_PATH)) {
      String nativeLibsFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "nativeLibrariesDirectory", false, true);
      if (nativeLibsFolderRelPath != null && isFullyResolved(nativeLibsFolderRelPath)) {
        configuration.getState().LIBS_FOLDER_RELATIVE_PATH = '/' + nativeLibsFolderRelPath;
      }
    }
    importNativeDependencies(facet, project, moduleDirPath);
  }

  private static boolean isFullyResolved(@NotNull String s) {
    return !s.contains("${");
  }

  @Nullable
  private String getPathFromConfig(Module module,
                                   MavenProject project,
                                   String moduleDirPath,
                                   String configTagName,
                                   boolean inResourceDir,
                                   boolean directory) {
    String resourceDir = findConfigValue(project, configTagName);
    if (resourceDir != null) {
      String path = makePath(project, resourceDir);
      if (inResourceDir) {
        MyResourceProcessor processor = new MyResourceProcessor(path, directory);
        AndroidMavenProviderImpl.processResources(module, project, processor);
        if (processor.myResult != null) {
          path = processor.myResult.getPath();
        }
      }
      String resFolderRelPath = getRelativePath(moduleDirPath, path);
      if (resFolderRelPath != null) {
        return resFolderRelPath;
      }
    }
    return null;
  }

  @Nullable
  private static String getRelativePath(String basePath, String absPath) {
    absPath = FileUtil.toSystemIndependentName(absPath);
    return FileUtil.getRelativePath(basePath, absPath, '/');
  }

  @Override
  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/combined-resources");
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/combined-assets");
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/extracted-dependencies");
  }

  private static class MyResourceProcessor implements AndroidMavenProviderImpl.ResourceProcessor {
    private final String myResourceOutputPath;
    private final boolean myDirectory;

    private VirtualFile myResult;

    private MyResourceProcessor(String resourceOutputPath, boolean directory) {
      myResourceOutputPath = resourceOutputPath;
      myDirectory = directory;
    }

    @Override
    public boolean process(@NotNull VirtualFile resource, @NotNull String outputPath) {
      if (!myDirectory && resource.isDirectory()) {
        return false;
      }
      outputPath = StringUtil.trimEnd(outputPath, "/");
      if (FileUtil.pathsEqual(outputPath, myResourceOutputPath)) {
        myResult = resource;
        return true;
      }

      if (myDirectory) {
        // we're looking for for resource directory

        if (outputPath.toLowerCase().startsWith(myResourceOutputPath.toLowerCase())) {
          final String parentPath = outputPath.substring(0, myResourceOutputPath.length());
          if (FileUtil.pathsEqual(parentPath, myResourceOutputPath)) {

            if (resource.isDirectory()) {
              // copying of directory that is located in resource dir, so resource dir is parent
              final VirtualFile parent = resource.getParent();
              if (parent != null) {
                myResult = parent;
                return true;
              }
            }
            else {
              // copying of resource file, we have to skip resource-type specific directory
              final VirtualFile parent = resource.getParent();
              final VirtualFile gp = parent != null ? parent.getParent() : null;
              if (gp != null) {
                myResult = gp;
                return true;
              }
            }
          }
        }
        return false;
      }

      return false;
    }
  }

  private static class MyDeleteObsoleteApklibModulesTask implements MavenProjectsProcessorTask {
    private final Project myProject;

    public MyDeleteObsoleteApklibModulesTask(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void perform(final Project project,
                        MavenEmbeddersManager embeddersManager,
                        MavenConsole console,
                        MavenProgressIndicator indicator)
      throws MavenProcessCanceledException {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed() || project.getUserData(DELETE_OBSOLETE_MODULE_TASK_KEY) != Boolean.TRUE) {
          return;
        }
        project.putUserData(DELETE_OBSOLETE_MODULE_TASK_KEY, null);
        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
        final Set<Module> referredModules = new HashSet<>();

        for (Module module : moduleModel.getModules()) {
          if (!AndroidMavenUtil.isExtApklibModule(module)) {
            collectDependenciesRecursively(module, referredModules);
          }
        }

        ApplicationManager.getApplication().runWriteAction(() -> {
          boolean modelChanged = false;

          for (final Module module : moduleModel.getModules()) {
            if (AndroidMavenUtil.isExtApklibModule(module) && !referredModules.contains(module)) {
              final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

              if (contentRoots.length > 0) {
                final VirtualFile contentRoot = contentRoots[0];
                try {
                  contentRoot.delete(myProject);
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
              moduleModel.disposeModule(module);
              modelChanged = true;
            }
          }
          if (modelChanged) {
            moduleModel.commit();
          }
          else {
            moduleModel.dispose();
          }
        });
      });
    }

    private static void collectDependenciesRecursively(@NotNull Module root, @NotNull Set<Module> result) {
      if (!result.add(root)) {
        return;
      }

      for (Module depModule : ModuleRootManager.getInstance(root).getDependencies()) {
        collectDependenciesRecursively(depModule, result);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      MyDeleteObsoleteApklibModulesTask task = (MyDeleteObsoleteApklibModulesTask)o;

      if (!myProject.equals(task.myProject)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myProject.hashCode();
      return result;
    }
  }
}
