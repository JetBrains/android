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

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.java.JavaModel;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.testing.TestArtifactSearchScopes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getDependencies;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.android.tools.idea.gradle.util.Projects.getAndroidModel;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.intellij.openapi.roots.libraries.LibraryUtil.findLibrary;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class AndroidGradleJavaProjectModelModifier extends JavaProjectModelModifier {
  @Nullable
  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope) {
    Project project = from.getProject();
    VirtualFile openedFile = FileEditorManagerEx.getInstanceEx(from.getProject()).getCurrentFile();
    String gradlePath = getGradlePath(to);
    GradleBuildModel buildModel = GradleBuildModel.get(from);

    if (buildModel != null && gradlePath != null) {
      DependenciesModel dependencies = buildModel.dependencies();
      String configurationName = getConfigurationName(from, scope, openedFile);
      dependencies.addModule(configurationName, gradlePath, null);

      new WriteCommandAction(project, "Add Gradle Module Dependency") {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          buildModel.applyChanges();
          registerUndoAction(project);
        }
      }.execute();
      return requestProjectSync(project);
    }

    if ((buildModel == null) ^ (gradlePath == null)) {
      // If one of them is gradle module and one of them are not, reject since this is invalid dependency
      return Promise.REJECTED;
    }
    return null;
  }

  @Nullable
  @Override
  public Promise<Void> addExternalLibraryDependency(@NotNull Collection<Module> modules,
                                                    @NotNull ExternalLibraryDescriptor descriptor,
                                                    @NotNull DependencyScope scope) {
    ArtifactDependencySpec dependencySpec =
        new ArtifactDependencySpec(descriptor.getLibraryArtifactId(), descriptor.getLibraryGroupId(), selectVersion(descriptor));
    return addExternalLibraryDependency(modules, dependencySpec, scope);
  }

  @Nullable
  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope) {
    if (!isBuildWithGradle(from)) {
      return null;
    }
    ArtifactDependencySpec dependencySpec = findNewExternalDependency(from.getProject(), library);
    if (dependencySpec == null) {
      return Promise.REJECTED;
    }
    return addExternalLibraryDependency(ImmutableList.of(from), dependencySpec, scope);
  }

  @Nullable
  private static Promise<Void> addExternalLibraryDependency(@NotNull Collection<Module> modules,
                                                            @NotNull ArtifactDependencySpec dependencySpec,
                                                            @NotNull DependencyScope scope) {
    Module firstModule = Iterables.getFirst(modules, null);
    if (firstModule == null) {
      return null;
    }
    Project project = firstModule.getProject();

    VirtualFile openedFile = FileEditorManagerEx.getInstanceEx(firstModule.getProject()).getCurrentFile();

    List<GradleBuildModel> buildModelsToUpdate = Lists.newArrayList();
    for (Module module : modules) {
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        return null;
      }
      String configurationName = getConfigurationName(module, scope, openedFile);
      DependenciesModel dependencies = buildModel.dependencies();
      dependencies.addArtifact(configurationName, dependencySpec);
      buildModelsToUpdate.add(buildModel);
    }

    new WriteCommandAction(project, "Add Gradle Library Dependency") {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (GradleBuildModel buildModel : buildModelsToUpdate) {
          buildModel.applyChanges();
        }
        registerUndoAction(project);
      }
    }.execute();

    return requestProjectSync(project);
  }

  @Nullable
  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level) {
    Project project = module.getProject();
    if (!isBuildWithGradle(module)) {
      return null;
    }

    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel == null) {
      return null;
    }

    if (getAndroidModel(module) != null) {
      CompileOptionsModel compileOptions = buildModel.android().compileOptions();
      compileOptions.setSourceCompatibility(level);
      compileOptions.setTargetCompatibility(level);
    }
    else {
      JavaGradleFacet javaGradleFacet = JavaGradleFacet.getInstance(module);
      if (javaGradleFacet == null || javaGradleFacet.getJavaProject() == null) {
        return null;
      }
      JavaModel javaModel = buildModel.java();
      javaModel.setSourceCompatibility(level);
      javaModel.setTargetCompatibility(level);
    }

    new WriteCommandAction(project, "Change Gradle Language Level") {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        buildModel.applyChanges();
        registerUndoAction(project);
      }
    }.execute();

    return requestProjectSync(project);
  }

  @NotNull
  private static String getConfigurationName(@NotNull Module module, @NotNull  DependencyScope scope, @Nullable VirtualFile openedFile) {
    if (!scope.isForProductionCompile()) {
      TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(module);

      if (testScopes != null && openedFile != null) {
        if (testScopes.isAndroidTestSource(openedFile)) {
          return ANDROID_TEST_COMPILE;
        }
        return TEST_COMPILE;
      }
      return COMPILE;
    }
    return COMPILE;
  }

  private final static Map<String, String> externalLibraryVersions = ImmutableMap.of("net.jcip:jcip-annotations", "1.0",
                                                                                     "org.jetbrains:annotations-java5", "15.0",
                                                                                     "org.jetbrains:annotations", "15.0",
                                                                                     "junit:junit", "4.12",
                                                                                     "org.testng:testng", "6.9.6");

  @Nullable
  private static String selectVersion(@NotNull ExternalLibraryDescriptor descriptor) {
    String groupAndId = descriptor.getLibraryGroupId() + ":" + descriptor.getLibraryArtifactId();
    return externalLibraryVersions.get(groupAndId);
  }

  private static Promise<Void> requestProjectSync(@NotNull Project project) {
    AsyncPromise<Void> promise = new AsyncPromise<>();
    GradleProjectImporter.getInstance().requestProjectSync(project, false, new GradleSyncListener.Adapter() {
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

  private static void registerUndoAction(@NotNull Project project) {
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
      @Override
      public void undo() throws UnexpectedUndoException {
        requestProjectSync(project);
      }

      @Override
      public void redo() throws UnexpectedUndoException {
        requestProjectSync(project);
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
      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(module);
      if (androidGradleModel != null && findLibrary(module, library.getName()) != null) {
        result = findNewExternalDependency(library, androidGradleModel);
        break;
      }
    }

    if (result == null) {
      result = findNewExternalDependencyByExaminingPath(library);
    }
    return result;
  }

  @Nullable
  private static ArtifactDependencySpec findNewExternalDependency(@NotNull Library library,
                                                                  @NotNull AndroidGradleModel androidModel) {
    GradleVersion modelVersion = androidModel.getModelVersion();

    BaseArtifact testArtifact = androidModel.findSelectedTestArtifactInSelectedVariant();

    JavaLibrary matchedLibrary = null;
    if (testArtifact != null) {
      matchedLibrary = findMatchedLibrary(library, testArtifact, modelVersion);
    }
    if (matchedLibrary == null) {
      Variant selectedVariant = androidModel.getSelectedVariant();
      matchedLibrary = findMatchedLibrary(library, selectedVariant.getMainArtifact(), modelVersion);
    }
    if (matchedLibrary == null) {
      return null;
    }

    // TODO use getRequestedCoordinates once the interface is fixed.
    MavenCoordinates coordinates = matchedLibrary.getResolvedCoordinates();
    if (coordinates == null) {
      return null;
    }
    return new ArtifactDependencySpec(coordinates.getArtifactId(), coordinates.getGroupId(), coordinates.getVersion());
  }

  @Nullable
  private static JavaLibrary findMatchedLibrary(@NotNull Library library,
                                                @NotNull BaseArtifact artifact,
                                                @Nullable GradleVersion modelVersion) {
    Dependencies dependencies = getDependencies(artifact, modelVersion);
    for (JavaLibrary gradleLibrary : dependencies.getJavaLibraries()) {
      String libraryName = getNameWithoutExtension(gradleLibrary.getJarFile());
      if (libraryName.equals(library.getName())) {
        return gradleLibrary;
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
          return new ArtifactDependencySpec(artifactId, groupId, version);
        }
      }
    }
    return null;
  }
}
