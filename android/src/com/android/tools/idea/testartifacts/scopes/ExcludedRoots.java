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

import static com.android.tools.idea.io.FilePaths.getJarFromJarUrl;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeSourceProvider;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependencySet;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        IdeAndroidArtifact artifact = androidModuleModel.getMainArtifact();
        myExcludedRoots.addAll(getAdditionalClasspathFolders(artifact));
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

    IdeArtifactName artifactName = artifact.getName();
    List<IdeSourceProvider> testSourceProviders = androidModel.getTestSourceProviders(artifactName);
    for (IdeSourceProvider sourceProvider : testSourceProviders) {
      for (File file : getAllSourceFolders(sourceProvider)) {
        action.accept(file);
      }
    }
  }

  /**
   * Returns folders which are used for unit testing and stored in the model, but not represented in the IntelliJ project structure.
   */
  public static List<File> getAdditionalClasspathFolders(@NotNull IdeBaseArtifact artifact) {
    return ImmutableList.<File>builder()
      .add(artifact.getJavaResourcesFolder())
      .addAll(artifact.getAdditionalClassesFolders())
      .build();
  }

  private void addLibraryPaths(@NotNull DependencySet dependencies) {
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      Collections.addAll(myExcludedRoots, dependency.getBinaryPaths());
    }
  }

  void removeLibraryPaths(@NotNull DependencySet dependencies) {
    // Now we need to add to 'excluded' roots the libraries that are in the modules to include, but are in the scope that needs to be
    // excluded.
    // https://code.google.com/p/android/issues/detail?id=206481
    for (ModuleDependency dependency : dependencies.onModules()) {
      Module module = dependency.getModule();
      if (module != null) {
        addLibraryPaths(module);
      }
    }

    for (LibraryDependency dependency : dependencies.onLibraries()) {
      for (File path : dependency.getBinaryPaths()) {
        myExcludedRoots.remove(path);
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
    IdeDependencies dependencies = artifact.getLevel2Dependencies();
    dependencies.getAndroidLibraries().forEach(library -> {
      // This loop relies on the implementation details of compileJarFiles, and the code is expected to be removed soon anyway.
      for (String path : library.getCompileJarFiles().stream().skip(1).collect(Collectors.toList())) {
        File file = new File(path);
        if (!isAlreadyIncluded(file)) {
          myExcludedRoots.add(file);
        }
      }
    });
    dependencies.getJavaLibraries().forEach(library -> {
      File jarFile = library.getArtifact();
      if (!isAlreadyIncluded(jarFile)) {
        myExcludedRoots.add(jarFile);
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
    IdeDependencies dependencies = artifact.getLevel2Dependencies();
    dependencies.getAndroidLibraries().forEach(library -> {
      for (String path : library.getCompileJarFiles().stream().filter(
        it -> (it.lastIndexOf('/') + 1 < it.length()) && !it.substring(it.lastIndexOf('/') + 1).equals("api.jar"))
        .collect(Collectors.toList())) {
        myExcludedRoots.remove(new File(path));
      }
    });
    dependencies.getJavaLibraries().forEach(library -> {
      myExcludedRoots.remove(library.getArtifact());
    });
  }

  @NotNull
  public Set<File> get() {
    return myExcludedRoots;
  }

  public static Collection<File> getAllSourceFolders(IdeSourceProvider provider) {
    return Stream.of(
      provider.getJavaDirectories(),
      provider.getKotlinDirectories(),
      provider.getResDirectories(),
      provider.getAidlDirectories(),
      provider.getRenderscriptDirectories(),
      provider.getAssetsDirectories(),
      provider.getJniLibsDirectories()
    ).flatMap(it -> it.stream()).collect(Collectors.toList());
  }
}
