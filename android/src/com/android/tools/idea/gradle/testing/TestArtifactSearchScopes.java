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
package com.android.tools.idea.gradle.testing;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.dependency.Dependency;
import com.android.tools.idea.gradle.customizer.dependency.DependencySet;
import com.android.tools.idea.gradle.customizer.dependency.LibraryDependency;
import com.android.tools.idea.gradle.customizer.dependency.ModuleDependency;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.tools.idea.gradle.util.FilePaths.getJarFromJarUrl;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static org.jetbrains.android.facet.IdeaSourceProvider.getAllSourceFolders;

/**
 * Android test artifacts {@code GlobalSearchScope}s:
 * <ul>
 *   <li>Android test source</li>
 *   <li>Unit test source</li>
 *   <li>"Excluded" for Android test source (unit test's source / library / module dependencies)</li>
 *   <li>"Excluded" for unit test source</li>
 * </ul>
 */
public final class TestArtifactSearchScopes {
  private static final Key<TestArtifactSearchScopes> SEARCH_SCOPES_KEY = Key.create("TEST_ARTIFACT_SEARCH_SCOPES");

  @Nullable
  public static TestArtifactSearchScopes get(@NotNull VirtualFile file, @NotNull Project project) {
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    Module module = projectFileIndex.getModuleForFile(file);
    return module != null ? get(module) : null;
  }

  @Nullable
  public static TestArtifactSearchScopes get(@NotNull Module module) {
    return module.getUserData(SEARCH_SCOPES_KEY);
  }

  /**
   * Initialize the scopes of all Gradle-based Android modules in the given project. This method must be invoked after a project sync with
   * Gradle.
   */
  public static void initializeScopes(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      TestArtifactSearchScopes scopes;
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      scopes = androidModel != null ? new TestArtifactSearchScopes(module, androidModel) : null;
      module.putUserData(SEARCH_SCOPES_KEY, scopes);
    }
  }

  @NotNull private final Module myModule;
  @NotNull private final AndroidGradleModel myAndroidModel;

  private FileRootSearchScope myAndroidTestSourceScope;
  private FileRootSearchScope myUnitTestSourceScope;
  private FileRootSearchScope myAndroidTestExcludeScope;
  private FileRootSearchScope myUnitTestExcludeScope;

  private TestArtifactSearchScopes(@NotNull Module module, @NotNull AndroidGradleModel androidModel) {
    myModule = module;
    myAndroidModel = androidModel;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public boolean isAndroidTestSource(@NotNull VirtualFile file) {
    return getAndroidTestSourceScope().accept(file);
  }

  public boolean isUnitTestSource(@NotNull VirtualFile vFile) {
    return getUnitTestSourceScope().accept(vFile);
  }

  @NotNull
  public FileRootSearchScope getAndroidTestSourceScope() {
    if (myAndroidTestSourceScope == null) {
      myAndroidTestSourceScope = getSourceScope(ARTIFACT_ANDROID_TEST);
    }
    return myAndroidTestSourceScope;
  }

  @NotNull
  public FileRootSearchScope getUnitTestSourceScope() {
    if (myUnitTestSourceScope == null) {
      myUnitTestSourceScope = getSourceScope(ARTIFACT_UNIT_TEST);
    }
    return myUnitTestSourceScope;
  }

  @NotNull
  private FileRootSearchScope getSourceScope(@NotNull String artifactName) {
    Set<File> roots = Sets.newHashSet();
    // TODO consider generated source
    for (SourceProvider sourceProvider : myAndroidModel.getTestSourceProviders(artifactName)) {
      roots.addAll(getAllSourceFolders(sourceProvider));
    }
    return new FileRootSearchScope(myModule.getProject(), roots);
  }

  @NotNull
  public FileRootSearchScope getAndroidTestExcludeScope() {
    if (myAndroidTestExcludeScope == null) {
      myAndroidTestExcludeScope = getExcludedScope(ARTIFACT_ANDROID_TEST);
    }
    return myAndroidTestExcludeScope;
  }

  @NotNull
  public FileRootSearchScope getUnitTestExcludeScope() {
    if (myUnitTestExcludeScope == null) {
      myUnitTestExcludeScope = getExcludedScope(ARTIFACT_UNIT_TEST);
    }
    return myUnitTestExcludeScope;
  }

  @NotNull
  private FileRootSearchScope getExcludedScope(@NotNull String artifactName) {
    FileRootSearchScope excludedSource;
    if (ARTIFACT_ANDROID_TEST.equals(artifactName)) {
      excludedSource = getUnitTestSourceScope();
    }
    else {
      excludedSource = getAndroidTestSourceScope();
    }
    FileRootSearchScope excludedLibs = getExcludedDependenciesScope(artifactName);
    return excludedSource.uniteWith(excludedLibs);
  }

  @NotNull
  private FileRootSearchScope getExcludedDependenciesScope(@NotNull String artifactName) {
    Set<File> excludedRoots = Sets.newHashSet();

    BaseArtifact unitTestArtifact = myAndroidModel.getUnitTestArtifactInSelectedVariant();
    BaseArtifact androidTestArtifact = myAndroidModel.getAndroidTestArtifactInSelectedVariant();

    boolean isAndroidTestArtifact = ARTIFACT_ANDROID_TEST.equals(artifactName);

    BaseArtifact excludeArtifact = isAndroidTestArtifact ? unitTestArtifact : androidTestArtifact;
    if (excludeArtifact != null) {
      excludedRoots.add(excludeArtifact.getClassesFolder());
    }

    DependencySet androidTestDependencies = null;
    DependencySet unitTestDependencies = null;

    DependencyScope scope = TEST;
    if (unitTestArtifact != null) {
      unitTestDependencies = Dependency.extractFrom(unitTestArtifact, scope);
    }
    if (androidTestArtifact != null) {
      androidTestDependencies = Dependency.extractFrom(androidTestArtifact, scope);
    }

    DependencySet dependenciesToInclude = isAndroidTestArtifact ? androidTestDependencies : unitTestDependencies;
    DependencySet dependenciesToExclude = isAndroidTestArtifact ? unitTestDependencies : androidTestDependencies;

    Project project = myModule.getProject();

    Set<Module> excludedModules = Sets.newHashSet();

    if (dependenciesToExclude != null) {
      for (LibraryDependency dependency : dependenciesToExclude.onLibraries()) {
        for (String path : dependency.getPaths(LibraryDependency.PathType.BINARY)) {
          excludedRoots.add(new File(path));
        }
      }

      for (ModuleDependency dependency : dependenciesToExclude.onModules()) {
        Module dependencyModule = dependency.getModule(project);
        if (dependencyModule != null) {
          excludedModules.add(dependencyModule);
        }
      }
    }

    if (dependenciesToInclude != null) {
      for (LibraryDependency dependency : dependenciesToInclude.onLibraries()) {
        for (String path : dependency.getPaths(LibraryDependency.PathType.BINARY)) {
          excludedRoots.remove(new File(path));
        }
      }

      for (ModuleDependency dependency : dependenciesToInclude.onModules()) {
        Module dependencyModule = dependency.getModule(project);
        if (dependencyModule != null) {
          excludedModules.remove(dependencyModule);
        }
      }
    }

    // This depends on all the modules are using explicit dependencies in android studio
    for (Module excludedModule : excludedModules) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(excludedModule);
      for (ContentEntry entry : rootManager.getContentEntries()) {
        for (SourceFolder sourceFolder : entry.getSourceFolders()) {
          excludedRoots.add(urlToFilePath(sourceFolder.getUrl()));
        }
        CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
        String url = compiler.getCompilerOutputUrl();
        if (isNotEmpty(url)) {
          excludedRoots.add(urlToFilePath(url));
        }
      }

      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(excludedModule);
      if (androidGradleModel != null) {
        excludedRoots.add(androidGradleModel.getMainArtifact().getJavaResourcesFolder());
      }
    }

    return new FileRootSearchScope(project, excludedRoots);
  }

  @Nullable
  private static File urlToFilePath(@NotNull String url) {
    if (url.startsWith(JAR_PROTOCOL_PREFIX)) {
      return getJarFromJarUrl(url);
    }
    String path = urlToPath(url);
    return new File(toSystemDependentPath(path));
  }
}
