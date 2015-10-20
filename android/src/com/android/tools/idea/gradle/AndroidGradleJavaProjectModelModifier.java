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

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.dsl.dependencies.Dependencies;
import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.intellij.openapi.roots.libraries.LibraryUtil.findLibrary;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class AndroidGradleJavaProjectModelModifier extends JavaProjectModelModifier {
  @NotNull private Project myProject;

  public AndroidGradleJavaProjectModelModifier(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope) {
    String gradlePath = getGradlePath(to);
    // TODO use new gradle API
    final GradleBuildFile gradleBuildFile = GradleBuildFile.get(from);

    if (gradleBuildFile != null && gradlePath != null) {
      Dependency dependency = new Dependency(getDependencyScope(from, scope), Dependency.Type.MODULE, gradlePath);
      final List<BuildFileStatement> dependencies = Lists.newArrayList(gradleBuildFile.getDependencies());
      dependencies.add(dependency);

      new WriteCommandAction(myProject, "Add Gradle Dependency") {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
          registerUndoAction(myProject);
        }
      }.execute();
      return requestProjectSync(myProject);
    }

    if ((gradleBuildFile == null) ^ (gradlePath == null)) {
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
    ExternalDependencySpec dependencySpec;
    // TODO update
    // com.intellij.codeInsight.daemon.impl.quickfix.JetBrainsAnnotationsExternalLibraryResolver.JetBrainsAnnotationsLibraryDescriptor
    if ("com.intellij".equals(descriptor.getLibraryGroupId()) && "annotations".equals(descriptor.getLibraryArtifactId())) {
      dependencySpec = new ExternalDependencySpec("annotations", "org.jetbrains", "13.0");
    }
    else {
      dependencySpec =
        new ExternalDependencySpec(descriptor.getLibraryArtifactId(), descriptor.getLibraryGroupId(), selectVersion(descriptor));
    }
    return addExternalLibraryDependency(modules, dependencySpec, scope);
  }

  @Nullable
  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope) {
    if (!isBuildWithGradle(from)) {
      return null;
    }
    ExternalDependencySpec dependencySpec = findNewExternalDependency(library);
    if (dependencySpec == null) {
      return Promise.REJECTED;
    }
    return addExternalLibraryDependency(ImmutableList.of(from), dependencySpec, scope);
  }

  @Nullable
  private Promise<Void> addExternalLibraryDependency(@NotNull Collection<Module> modules,
                                                     @NotNull ExternalDependencySpec dependencySpec,
                                                     @NotNull DependencyScope scope) {
    final List<Dependencies> dependenciesToChange = Lists.newArrayList();
    for (Module module : modules) {
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        return null;
      }
      String configurationName = getConfigurationName(module, scope);
      Dependencies dependencies = buildModel.dependencies();
      dependencies.add(configurationName, dependencySpec);
      dependenciesToChange.add(dependencies);
    }

    new WriteCommandAction(myProject, "Add Gradle Dependency") {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (Dependencies dependencies : dependenciesToChange) {
          dependencies.applyChanges();
        }
        registerUndoAction(myProject);
      }
    }.execute();

    return requestProjectSync(myProject);
  }

  @Nullable
  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level) {
    if (!isBuildWithGradle(module)) {
      return null;
    }
    // TODO finish this after dsl parser have been done
    return Promise.REJECTED;
  }

  @NotNull
  static String getConfigurationName(@NotNull Module module, DependencyScope scope) {
    if (!scope.isForProductionCompile()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
        if (androidModel != null && ARTIFACT_ANDROID_TEST.equals(androidModel.getSelectedTestArtifactName())) {
          return ANDROID_TEST_COMPILE;
        }
      }
      return TEST_COMPILE;
    }
    return COMPILE;
  }

  // TODO remove after switch to the new API
  @NotNull
  private static Dependency.Scope getDependencyScope(@NotNull Module module, DependencyScope scope) {
    if (!scope.isForProductionCompile()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
        if (androidModel != null && ARTIFACT_ANDROID_TEST.equals(androidModel.getSelectedTestArtifactName())) {
          return Dependency.Scope.ANDROID_TEST_COMPILE;
        }
      }
      return Dependency.Scope.TEST_COMPILE;
    }
    return Dependency.Scope.COMPILE;
  }

  private static Map<String, String> externalLibraryVersions = ImmutableMap
    .of("net.jcip:jcip-annotations", "1.0", "org.jetbrains:annotations", "13.0", "junit:junit", "4.12", "org.testng:testng", "6.9.6");

  @Nullable
  private static String selectVersion(@NotNull ExternalLibraryDescriptor descriptor) {
    String groupAndId = descriptor.getLibraryGroupId() + ":" + descriptor.getLibraryArtifactId();
    return externalLibraryVersions.get(groupAndId);
  }

  private static Promise<Void> requestProjectSync(@NotNull Project project) {
    final AsyncPromise<Void> promise = new AsyncPromise<Void>();
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

  private static void registerUndoAction(@NotNull final Project project) {
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
  private ExternalDependencySpec findNewExternalDependency(@NotNull Library library) {
    if (library.getName() == null) {
      return null;
    }
    ExternalDependencySpec result = null;
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
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
  private static ExternalDependencySpec findNewExternalDependency(@NotNull Library library, @NotNull AndroidGradleModel androidModel) {
    BaseArtifact testArtifact = androidModel.findSelectedTestArtifactInSelectedVariant();

    com.android.builder.model.Library matchedLibrary = null;
    if (testArtifact != null) {
      matchedLibrary = findMatchedLibrary(library, testArtifact);
    }
    if (matchedLibrary == null) {
      Variant selectedVariant = androidModel.getSelectedVariant();
      matchedLibrary = findMatchedLibrary(library, selectedVariant.getMainArtifact());
    }
    if (matchedLibrary == null) {
      return null;
    }

    // TODO use getRequestedCoordinates once the interface is fixed.
    MavenCoordinates coordinates = matchedLibrary.getResolvedCoordinates();
    if (coordinates == null) {
      return null;
    }
    return new ExternalDependencySpec(coordinates.getArtifactId(), coordinates.getGroupId(), coordinates.getVersion());
  }

  @Nullable
  private static com.android.builder.model.Library findMatchedLibrary(@NotNull Library library, @NotNull BaseArtifact artifact) {
    for (JavaLibrary gradleLibrary : artifact.getDependencies().getJavaLibraries()) {
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
  private static ExternalDependencySpec findNewExternalDependencyByExaminingPath(@NotNull Library library) {
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
          return new ExternalDependencySpec(artifactId, groupId, version);
        }
      }
    }
    return null;
  }
}
