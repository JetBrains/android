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
package com.android.tools.idea.testartifacts.scopes;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependencySet;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.android.tools.idea.io.FilePaths.getJarFromJarUrl;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static org.jetbrains.android.facet.IdeaSourceProvider.getAllSourceFolders;

class ExcludedRoots {
  @NotNull private final ExcludedModules myExcludedModules;
  private final boolean myAndroidTest;

  @NotNull private final Set<File> myExcludedRoots = new HashSet<>();
  @NotNull private final Set<String> myIncludedRootNames = new HashSet<>();

  ExcludedRoots(@NotNull ExcludedModules excludedModules,
                @NotNull DependencySet dependenciesToExclude,
                @NotNull DependencySet dependenciesToInclude,
                boolean isAndroidTest) {
    myExcludedModules = excludedModules;
    myAndroidTest = isAndroidTest;
    addFolderPathsFromExcludedModules();
    addRemainingModelsIfNecessary();

    for (LibraryDependency libraryDependency : dependenciesToInclude.onLibraries()) {
      File[] binaryPaths = libraryDependency.getBinaryPaths();
      for (File binaryPath : binaryPaths) {
        myIncludedRootNames.add(binaryPath.getName());
      }
    }

    addLibraryPaths(dependenciesToExclude);
    removeLibraryPaths(dependenciesToInclude);
  }

  private void addFolderPathsFromExcludedModules() {
    for (Module module : myExcludedModules) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      for (ContentEntry entry : rootManager.getContentEntries()) {
        for (SourceFolder sourceFolder : entry.getSourceFolders()) {
          myExcludedRoots.add(urlToFilePath(sourceFolder.getUrl()));
        }

        CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
        String url = compiler.getCompilerOutputUrl();
        if (isNotEmpty(url)) {
          myExcludedRoots.add(urlToFilePath(url));
        }
      }

      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel != null) {
        myExcludedRoots.add(androidModuleModel.getMainArtifact().getJavaResourcesFolder());
      }
    }
  }

  @Nullable
  private static File urlToFilePath(@NotNull String url) {
    if (url.startsWith(JAR_PROTOCOL_PREFIX)) {
      return getJarFromJarUrl(url);
    }
    String path = urlToPath(url);
    return new File(toSystemDependentPath(path));
  }

  private void addRemainingModelsIfNecessary() {
    ModuleManager moduleManager = ModuleManager.getInstance(myExcludedModules.getProject());
    for (Module module : moduleManager.getModules()) {
      if (myExcludedModules.contains(module)) {
        // Excluded modules have already been dealt with.
        continue;
      }
      addModuleIfNecessary(module);
    }
  }

  private void addModuleIfNecessary(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      IdeVariant variant = androidModel.getSelectedVariant();
      IdeBaseArtifact unitTestArtifact = variant.getUnitTestArtifact();
      IdeBaseArtifact androidTestArtifact = variant.getAndroidTestArtifact();

      IdeBaseArtifact excludeArtifact = myAndroidTest ? unitTestArtifact : androidTestArtifact;
      IdeBaseArtifact includeArtifact = myAndroidTest ? androidTestArtifact : unitTestArtifact;

      if (excludeArtifact != null) {
        processFolders(excludeArtifact, androidModel, myExcludedRoots::add);
      }

      if (includeArtifact != null) {
        processFolders(includeArtifact, androidModel, myExcludedRoots::remove);
      }
    }
  }

  private static void processFolders(@NotNull IdeBaseArtifact artifact,
                                     @NotNull AndroidModuleModel androidModel,
                                     @NotNull Consumer<File> action) {
    action.accept(artifact.getClassesFolder());
    for (File file : artifact.getGeneratedSourceFolders()) {
      action.accept(file);
    }

    String artifactName = artifact.getName();
    List<SourceProvider> testSourceProviders = androidModel.getTestSourceProviders(artifactName);
    for (SourceProvider sourceProvider : testSourceProviders) {
      for (File file : getAllSourceFolders(sourceProvider)) {
        action.accept(file);
      }
    }
  }

  private void addLibraryPaths(@NotNull DependencySet dependencies) {
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      Collections.addAll(myExcludedRoots, dependency.getBinaryPaths());
    }
  }

  void removeLibraryPaths(@NotNull DependencySet dependencies) {
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      for (File path : dependency.getBinaryPaths()) {
        myExcludedRoots.remove(path);
      }
    }

    //// Now we need to add to 'excluded' roots the libraries that are in the modules to include, but are in the scope that needs to be
    //// excluded.
    //// https://code.google.com/p/android/issues/detail?id=206481
    for (ModuleDependency dependency : dependencies.onModules()) {
      Module module = dependency.getModule();
      if (module != null) {
        addLibraryPaths(module);
      }
    }
  }

  private void addLibraryPaths(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    if (model != null) {
      IdeVariant variant = model.getSelectedVariant();
      IdeBaseArtifact exclude = myAndroidTest ? variant.getUnitTestArtifact() : variant.getAndroidTestArtifact();
      IdeBaseArtifact include = myAndroidTest ? variant.getAndroidTestArtifact() : variant.getUnitTestArtifact();
      if (exclude != null) {
        addLibraryPaths(exclude);
      }
      if (include != null) {
        removeLibraryPaths(include);
      }
    }
  }

  private void addLibraryPaths(@NotNull IdeBaseArtifact artifact) {
    IdeDependencies dependencies = (IdeDependencies)artifact.getDependencies();
    dependencies.forEachLibrary(library -> {
      if (isEmpty(library.getProject())) {
        for (File file : library.getLocalJars()) {
          if (!isAlreadyIncluded(file)) {
            myExcludedRoots.add(file);
          }
        }
      }
    });
    dependencies.forEachJavaLibrary(library -> {
      if (isEmpty(library.getProject())) {
        File jarFile = library.getJarFile();
        if (!isAlreadyIncluded(jarFile)) {
          myExcludedRoots.add(jarFile);
        }
      }
    });
  }

  private boolean isAlreadyIncluded(@NotNull File file) {
    // Do not exclude any library that was already marked as "included"
    // See:
    // https://code.google.com/p/android/issues/detail?id=219089
    return myIncludedRootNames.contains(file.getName());
  }

  private void removeLibraryPaths(@NotNull IdeBaseArtifact artifact) {
    IdeDependencies dependencies = (IdeDependencies)artifact.getDependencies();
    dependencies.forEachLibrary(library -> {
      if (isEmpty(library.getProject())) {
        for (File file : library.getLocalJars()) {
          myExcludedRoots.remove(file);
        }
      }
    });
    dependencies.forEachJavaLibrary(library -> {
      if (isEmpty(library.getProject())) {
        myExcludedRoots.remove(library.getJarFile());
      }
    });
  }

  @NotNull
  public Set<File> get() {
    return myExcludedRoots;
  }
}
