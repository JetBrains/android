/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.tools.idea.gradle.LibraryFilePaths.NAME_PREFIX;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_IMPLEMENTATION;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.IMPLEMENTATION;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.SCREENSHOT_TEST_IMPLEMENTATION;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_IMPLEMENTATION;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.getGradleProjectPath;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ADD_LIBRARY_DEPENDENCY;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ADD_MODULE_DEPENDENCY;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_LANGUAGE_LEVEL_CHANGED;
import static com.intellij.openapi.roots.libraries.LibraryUtil.findLibrary;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Version;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.tools.idea.gradle.dependencies.DependenciesHelper;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleFileModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.model.IdeArtifactLibrary;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeLibrary;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModifier;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class AndroidGradleJavaProjectModelModifier extends JavaProjectModelModifier {
  @NotNull
  private static final Map<String, String> EXTERNAL_LIBRARY_VERSIONS = ImmutableMap.of("net.jcip:jcip-annotations", "1.0",
                                                                                       "org.jetbrains:annotations-java5", "15.0",
                                                                                       "org.jetbrains:annotations", "15.0",
                                                                                       "junit:junit", "4.12",
                                                                                       "org.testng:testng", "6.9.6");

  @NotNull
  private static final Set<String> ANDROID_PLUGIN_IDENTIFIERS =
    ImmutableSet.of("android", "android-library",
                    "com.android.application", "com.android.library", "com.android.instantapp",
                    "com.android.feature", "com.android.dynamic-feature", "com.android.test");

  @NotNull
  private static final Set<String> JAVA_PLUGIN_IDENTIFIERS = ImmutableSet.of("java", "java-library");

  private static boolean isAndroidGradleProject(@NotNull Project project) {
    return ProjectSystemUtil.requiresAndroidModel(project);
  }

  @Nullable
  // returns single external project path if it is the same for all the modules, or null
  private static String getSingleExternalProjectPathOrNull(Collection<? extends Module> modules) {
    String projectPath = null;
    for (Module module : modules) {
      String rootProjectPathForModule = getSingleExternalProjectPathOrNull(module);
      if (rootProjectPathForModule == null) return null;
      if (projectPath == null) {
        projectPath = rootProjectPathForModule;
      }
      else if (!projectPath.equals(rootProjectPathForModule)) {
        return null;
      }
    }
    return projectPath;
  }

  @Nullable
  private static String getSingleExternalProjectPathOrNull(Module module) {
    return ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath();
  }

  @Nullable
  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    @Nullable String externalProjectPath = getSingleExternalProjectPathOrNull(from);
    if (externalProjectPath == null) return null;

    Project project = from.getProject();
    VirtualFile openedFile = FileEditorManagerEx.getInstanceEx(from.getProject()).getCurrentFile();
    GradleProjectPath gradlePath = getGradleProjectPath(to);
    GradleBuildModel buildModel = GradleBuildModel.get(from);

    if (buildModel != null && gradlePath != null) {
      DependenciesModel dependencies = buildModel.dependencies();
      String configurationName = getConfigurationName(from, scope, openedFile);
      dependencies.addModule(configurationName, gradlePath.getPath(), null);

      WriteCommandAction.writeCommandAction(project).withName("Add Gradle Module Dependency").run(() -> {
        buildModel.applyChanges();
        registerUndoAction(project, externalProjectPath);
      });
      return requestProjectSync(project, externalProjectPath, TRIGGER_MODIFIER_ADD_MODULE_DEPENDENCY);
    }

    if ((buildModel == null) ^ (gradlePath == null)) {
      // If one of them is gradle module and one of them are not, reject since this is invalid dependency
      return Promises.rejectedPromise();
    }
    return null;
  }

  @Nullable
  @Override
  public Promise<Void> addExternalLibraryDependency(@NotNull Collection<? extends Module> modules,
                                                    @NotNull ExternalLibraryDescriptor descriptor,
                                                    @NotNull DependencyScope scope) {
    @Nullable String externalProjectPath = getSingleExternalProjectPathOrNull(modules);
    if (externalProjectPath == null) return null;

    ArtifactDependencySpec dependencySpec =
      ArtifactDependencySpec.create(descriptor.getLibraryArtifactId(), descriptor.getLibraryGroupId(), selectVersion(descriptor));
    return addExternalLibraryDependency(modules, dependencySpec, scope);
  }

  @Nullable
  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    @Nullable String externalProjectPath = getSingleExternalProjectPathOrNull(from);
    if (externalProjectPath == null) return null;

    ArtifactDependencySpec dependencySpec = findNewExternalDependency(from.getProject(), library);
    if (dependencySpec == null) {
      return Promises.rejectedPromise();
    }
    return addExternalLibraryDependency(ImmutableList.of(from), dependencySpec, scope);
  }

  @Nullable
  private static Promise<Void> addExternalLibraryDependency(@NotNull Collection<? extends Module> modules,
                                                            @NotNull ArtifactDependencySpec dependencySpec,
                                                            @NotNull DependencyScope scope) {
    @Nullable String externalProjectPath = getSingleExternalProjectPathOrNull(modules);
    if (externalProjectPath == null) return null;

    Module firstModule = Iterables.getFirst(modules, null);
    if (firstModule == null) {
      return null;
    }
    Project project = firstModule.getProject();

    VirtualFile openedFile = FileEditorManagerEx.getInstanceEx(firstModule.getProject()).getCurrentFile();
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(project);

    List<GradleFileModel> buildModelsToUpdate = new ArrayList<>();
    for (Module module : modules) {
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        return null;
      }
      String configurationName = getConfigurationName(module, scope, openedFile);
      DependenciesHelper.withModel(projectBuildModel)
        .addDependency(configurationName, dependencySpec.compactNotation(), buildModel);
      buildModelsToUpdate.add(buildModel);
    }
    GradleVersionCatalogModel maybeCatalog =
      DependenciesHelper.getDefaultCatalogModel(projectBuildModel);
    if (maybeCatalog != null) buildModelsToUpdate.add(maybeCatalog);

    WriteCommandAction.writeCommandAction(project).withName("Add Gradle Library Dependency").run(() -> {
      for (GradleFileModel buildModel : buildModelsToUpdate) {
        buildModel.applyChanges();
      }
      registerUndoAction(project, externalProjectPath);
    });

    return requestProjectSync(project, externalProjectPath, TRIGGER_MODIFIER_ADD_LIBRARY_DEPENDENCY);
  }

  @Nullable
  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level) {
    @Nullable String externalProjectPath = getSingleExternalProjectPathOrNull(module);
    if (externalProjectPath == null) return null;

    Project project = module.getProject();
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel == null) {
      return null;
    }

    List<String> pluginNames = PluginModel.extractNames(buildModel.appliedPlugins());
    List<String> androidPluginNames = new ArrayList<>(pluginNames);
    androidPluginNames.retainAll(ANDROID_PLUGIN_IDENTIFIERS);
    List<String> javaPluginNames = new ArrayList<>(pluginNames);
    javaPluginNames.retainAll(JAVA_PLUGIN_IDENTIFIERS);

    if (!androidPluginNames.isEmpty()) {
      AndroidModel android = buildModel.android();
      CompileOptionsModel compileOptions = android.compileOptions();
      compileOptions.sourceCompatibility().setLanguageLevel(level);
      compileOptions.targetCompatibility().setLanguageLevel(level);
    }
    if (!javaPluginNames.isEmpty()) {
      JavaModel javaModel = buildModel.java();
      javaModel.sourceCompatibility().setLanguageLevel(level);
      javaModel.targetCompatibility().setLanguageLevel(level);
    }

    WriteCommandAction.writeCommandAction(project).withName("Change Gradle Language Level").run(() -> {
      buildModel.applyChanges();
      registerUndoAction(project, externalProjectPath);
    });

    return requestProjectSync(project, externalProjectPath, TRIGGER_MODIFIER_LANGUAGE_LEVEL_CHANGED);
  }

  @NotNull
  private static String getConfigurationName(@NotNull Module module,
                                                   @NotNull DependencyScope scope,
                                                   @Nullable VirtualFile openedFile) {
    if (!scope.isForProductionCompile()) {
      TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.getInstance(module);

      boolean isAndroid = testScopes != null && openedFile != null && testScopes.isAndroidTestSource(openedFile);
      boolean isScreenshotTest = testScopes != null && openedFile != null && testScopes.isScreenshotTestSource(openedFile);
      return isAndroid ? ANDROID_TEST_IMPLEMENTATION : (isScreenshotTest ? SCREENSHOT_TEST_IMPLEMENTATION : TEST_IMPLEMENTATION);
    }
    return IMPLEMENTATION;
  }

  @Nullable
  static String selectVersion(@NotNull ExternalLibraryDescriptor descriptor) {
    if (descriptor.getPreferredVersion() != null) {
      return descriptor.getPreferredVersion();
    }

    String libraryArtifactId = descriptor.getLibraryArtifactId();
    String libraryGroupId = descriptor.getLibraryGroupId();
    String groupAndId = libraryGroupId + ":" + libraryArtifactId;
    String version = EXTERNAL_LIBRARY_VERSIONS.get(groupAndId);
    if (version == null) {
      GoogleMavenArtifactId library = GoogleMavenArtifactId.Companion.find(libraryGroupId, libraryArtifactId);
      if (library != null) {
        Predicate<Version> filter =
          descriptor.getMinVersion() == null ? null : (v -> v.toString().startsWith(descriptor.getMinVersion()));

        Component component = RepositoryUrlManager.get().getArtifactComponent(library, filter, false);
        if (component != null) {
          version = component.getVersion().toString();
        }
      }
    }
    if (version == null) {
      version = descriptor.getPreferredVersion();
    }
    return version;
  }

  @NotNull
  private static Promise<Void> requestProjectSync(@NotNull Project project,
                                                  @NotNull String externalProjectPath,
                                                  @NotNull GradleSyncStats.Trigger trigger) {
    if (isAndroidGradleProject(project)) {
      return doAndroidGradleSync(project, trigger);
    }
    else {
      return doIdeaGradleSync(project, externalProjectPath);
    }
  }

  @NotNull
  private static AsyncPromise<Void> doAndroidGradleSync(@NotNull Project project, @NotNull GradleSyncStats.Trigger trigger) {
    AsyncPromise<Void> promise = new AsyncPromise<>();
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);

    GradleSyncInvoker.getInstance().requestProjectSync(project, request, new GradleSyncListener() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        promise.setResult(null);
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        promise.setError(errorMessage);
      }
    });

    return promise;
  }

  @NotNull
  private static AsyncPromise<Void> doIdeaGradleSync(@NotNull Project project,
                                                     @NotNull String externalProjectPath) {
    AsyncPromise<Void> promise = new AsyncPromise<>();
    ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID);
    importSpecBuilder.callback(new ExternalProjectRefreshCallback() {
      private final ImportSpecBuilder.DefaultProjectRefreshCallback
        defaultCallback = new ImportSpecBuilder.DefaultProjectRefreshCallback(importSpecBuilder.build());

      @Override
      public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
        defaultCallback.onSuccess(externalProject);
        // defaultCallback may defer some tasks with `invokeLater`. We should trigger promise after all the EDT events are finished
        ApplicationManager.getApplication().invokeLater(() -> promise.setResult(null));
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        defaultCallback.onFailure(errorMessage, errorDetails);
        promise.setError(errorMessage);
      }
    });
    ExternalSystemUtil.refreshProject(externalProjectPath, importSpecBuilder);
    return promise;
  }

  private static void registerUndoAction(@NotNull Project project, @NotNull String externalProjectPath) {
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
      @Override
      public void undo() throws UnexpectedUndoException {
        requestProjectSync(project, externalProjectPath, TRIGGER_MODIFIER_ACTION_UNDONE);
      }

      @Override
      public void redo() throws UnexpectedUndoException {
        requestProjectSync(project, externalProjectPath, TRIGGER_MODIFIER_ACTION_REDONE);
      }
    });
  }

  /**
   * Given a library entry, find out its corresponded gradle dependency entry like 'group:name:version".
   */
  @Nullable
  private static ArtifactDependencySpec findNewExternalDependency(@NotNull Project project, @NotNull Library library) {
    if (library.getName() == null) {
      return null;
    }
    ArtifactDependencySpec result = null;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      GradleAndroidModel androidModel = GradleAndroidModel.get(module);
      if (androidModel != null && findLibrary(module, library.getName()) != null) {
        result = findNewExternalDependency(library, androidModel.getSelectedVariant());
        break;
      }
    }

    if (result == null) {
      result = findNewExternalDependencyByExaminingPath(library);
    }
    return result;
  }

  @Nullable
  private static ArtifactDependencySpec findNewExternalDependency(@NotNull Library library, @NotNull IdeVariant selectedVariant) {
    @Nullable ArtifactDependencySpec matchedLibrary = null;
    @Nullable IdeBaseArtifact artifact = selectedVariant.getHostTestArtifacts().stream().filter(it -> it.getName() == IdeArtifactName.UNIT_TEST).findFirst().orElse(null);
    if (artifact != null) {
      matchedLibrary = findMatchedLibrary(library, artifact);
    }
    if (matchedLibrary == null) {
      artifact = selectedVariant.getDeviceTestArtifacts().stream().filter(it -> it.getName() == IdeArtifactName.ANDROID_TEST).findFirst().orElse(null);
      if (artifact != null) {
        matchedLibrary = findMatchedLibrary(library, artifact);
      }
    }
    if (matchedLibrary == null) {
      artifact = selectedVariant.getTestFixturesArtifact();
      if (artifact != null) {
        matchedLibrary = findMatchedLibrary(library, artifact);
      }
    }
    if (matchedLibrary == null) {
      artifact = selectedVariant.getHostTestArtifacts().stream().filter(it -> it.getName() == IdeArtifactName.SCREENSHOT_TEST).findFirst().orElse(null);
      if (artifact != null) {
        matchedLibrary = findMatchedLibrary(library, artifact);
      }
    }
    if (matchedLibrary == null) {
      matchedLibrary = findMatchedLibrary(library, selectedVariant.getMainArtifact());
    }

    return matchedLibrary;
  }

  @Nullable
  private static ArtifactDependencySpec findMatchedLibrary(@NotNull Library library, @NotNull IdeBaseArtifact artifact) {
    IdeDependencies dependencies = artifact.getCompileClasspath();
    for (IdeLibrary gradleLibrary : dependencies.getLibraries()) {
      if (!(gradleLibrary instanceof IdeArtifactLibrary dependency)) continue;
      String dependencyAddress = dependency.getArtifactAddress();
      String libraryName = library.getName();
      if (libraryName != null && libraryName.startsWith(NAME_PREFIX)) {
        libraryName = libraryName.substring(NAME_PREFIX.length());
      }
      if (dependencyAddress.equals(libraryName)) {
        Component component = dependency.getComponent();
        if (component != null) {
          return ArtifactDependencySpec.create(component.getName(), component.getGroup(), component.getVersion().toString());
        }
      }
    }
    return null;
  }

  /**
   * Gradle dependencies are stored in following path:  xxx/:groupId/:artifactId/:version/xxx/:artifactId-:version.jar
   * therefor, if we can't get the artifact information from model, then try to extract from path.
   */
  @Nullable
  private static ArtifactDependencySpec findNewExternalDependencyByExaminingPath(@NotNull Library library) {
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (files.length == 0) {
      return null;
    }
    File file = virtualToIoFile(files[0]);
    String libraryName = library.getName();
    if (libraryName == null) {
      return null;
    }

    List<String> pathSegments = splitPath(file.getPath());

    for (int i = 1; i < pathSegments.size() - 2; i++) {
      if (libraryName.startsWith(pathSegments.get(i))) {
        String groupId = pathSegments.get(i - 1);
        String artifactId = pathSegments.get(i);
        String version = pathSegments.get(i + 1);
        if (libraryName.endsWith(version)) {
          return ArtifactDependencySpec.create(artifactId, groupId, version);
        }
      }
    }
    return null;
  }
}
