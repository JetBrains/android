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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependencySet;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.android.builder.model.AndroidProject.*;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static org.jetbrains.android.facet.IdeaSourceProvider.getAllSourceFolders;

/**
 * Android test artifacts {@code GlobalSearchScope}s:
 * <ul>
 * <li>Android test source</li>
 * <li>Unit test source</li>
 * <li>"Excluded" for Android test source (unit test's source / library / module dependencies)</li>
 * <li>"Excluded" for unit test source</li>
 * </ul>
 */
public final class TestArtifactSearchScopes implements Disposable {
  private static final Key<TestArtifactSearchScopes> SEARCH_SCOPES_KEY = Key.create("TEST_ARTIFACT_SEARCH_SCOPES");

  @NotNull private final Module myModule;

  private FileRootSearchScope myAndroidTestSourceScope;
  private FileRootSearchScope myUnitTestSourceScope;
  private FileRootSearchScope myAndroidTestExcludeScope;
  private FileRootSearchScope myUnitTestExcludeScope;
  private FileRootSearchScope myAndroidTestDependencyExcludeScope;
  private FileRootSearchScope mySharedTestsExcludeScope;
  private FileRootSearchScope myUnitTestDependencyExcludeScope;

  private final Object myLock = new Object();

  private DependencySet myMainDependencies;
  private DependencySet myUnitTestDependencies;
  private DependencySet myAndroidTestDependencies;
  private boolean myAlreadyResolved;

  @Nullable
  public static TestArtifactSearchScopes get(@NotNull VirtualFile file, @NotNull Project project) {
    if (GradleSyncState.getInstance(project).lastSyncFailed()) {
      return null;
    }

    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    Module module = projectFileIndex.getModuleForFile(file);
    return module != null ? get(module) : null;
  }

  @Nullable
  public static TestArtifactSearchScopes get(@NotNull Module module) {
    return module.getUserData(SEARCH_SCOPES_KEY);
  }

  /**
   * Initialize the test scopes in the given module if the module is Gradle-based Android.
   *
   * @param module the given module.
   */
  public static void initializeScope(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    TestArtifactSearchScopes scopes = androidModel != null ? new TestArtifactSearchScopes(module) : null;
    module.putUserData(SEARCH_SCOPES_KEY, scopes);
  }

  private TestArtifactSearchScopes(@NotNull Module module) {
    myModule = module;
    Disposer.register(module, this);
    module.putUserData(SEARCH_SCOPES_KEY, this);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public boolean isAndroidTestSource(@NotNull VirtualFile file) {
    return getAndroidTestSourceScope().accept(file);
  }

  public boolean isUnitTestSource(@NotNull VirtualFile file) {
    return getUnitTestSourceScope().accept(file);
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
    Set<File> roots = Collections.emptySet();
    AndroidModuleModel androidModel = getAndroidModel();
    if (androidModel != null) {
      roots = new HashSet<>();

      if (artifactName.equals(ARTIFACT_ANDROID_TEST) && androidModel.getAndroidProject().getProjectType() == PROJECT_TYPE_TEST) {
        // Special case where android tests correspond actually to the _main_ artifact
        for (SourceProvider sourceProvider : androidModel.getActiveSourceProviders()) {
          roots.addAll(getAllSourceFolders(sourceProvider));
        }
      }
      else {
        // TODO consider generated source
        for (SourceProvider sourceProvider : androidModel.getTestSourceProviders(artifactName)) {
          roots.addAll(getAllSourceFolders(sourceProvider));
        }
      }
    }
    return new FileRootSearchScope(myModule.getProject(), roots);
  }

  @NotNull
  public FileRootSearchScope getAndroidTestExcludeScope() {
    if (myAndroidTestExcludeScope == null) {
      FileRootSearchScope exclude = getUnitTestSourceScope().exclude(getAndroidTestSourceScope());
      myAndroidTestExcludeScope = exclude.merge(getAndroidTestDependencyExcludeScope());
    }
    return myAndroidTestExcludeScope;
  }

  @NotNull
  public FileRootSearchScope getUnitTestExcludeScope() {
    if (myUnitTestExcludeScope == null) {
      FileRootSearchScope exclude = getAndroidTestSourceScope().exclude(getUnitTestSourceScope());
      myUnitTestExcludeScope = exclude.merge(getUnitTestDependencyExcludeScope());
    }
    return myUnitTestExcludeScope;
  }

  @NotNull
  public FileRootSearchScope getSharedTestsExcludeScope() {
    if (mySharedTestsExcludeScope == null) {
      // When a file is shared by both tests, then the test should only access the dependencies that android test and unit test both
      // have. Since the API requires us return a excluding scope. So we want to exclude all the dependencies android test doesn't
      // includes and the ones that unit test doesn't have.
      mySharedTestsExcludeScope = getAndroidTestDependencyExcludeScope().merge(getUnitTestDependencyExcludeScope());
    }
    return mySharedTestsExcludeScope;
  }

  @NotNull
  private FileRootSearchScope getAndroidTestDependencyExcludeScope() {
    if (myAndroidTestDependencyExcludeScope == null) {
      myAndroidTestDependencyExcludeScope = getExcludedDependenciesScope(ARTIFACT_ANDROID_TEST);
    }
    return myAndroidTestDependencyExcludeScope;
  }

  @NotNull
  private FileRootSearchScope getUnitTestDependencyExcludeScope() {
    if (myUnitTestDependencyExcludeScope == null) {
      myUnitTestDependencyExcludeScope = getExcludedDependenciesScope(ARTIFACT_UNIT_TEST);
    }
    return myUnitTestDependencyExcludeScope;
  }

  @NotNull
  private FileRootSearchScope getExcludedDependenciesScope(@NotNull String artifactName) {
    if (getAndroidModel() == null) {
      return new FileRootSearchScope(myModule.getProject(), Collections.emptyList());
    }

    resolveDependencies();

    boolean isAndroidTest = ARTIFACT_ANDROID_TEST.equals(artifactName);
    Collection<File> excluded;
    synchronized (myLock) {
      DependencySet dependenciesToInclude = isAndroidTest ? myAndroidTestDependencies : myUnitTestDependencies;
      DependencySet dependenciesToExclude = isAndroidTest ? myUnitTestDependencies : myAndroidTestDependencies;

      ExcludedModules excludedModules = new ExcludedModules(myModule);
      excludedModules.add(dependenciesToExclude);
      excludedModules.remove(dependenciesToInclude);
      excludedModules.remove(myMainDependencies);

      ExcludedRoots excludedRoots =
        new ExcludedRoots(myModule, excludedModules, dependenciesToExclude, dependenciesToInclude, isAndroidTest);
      excludedRoots.removeLibraryPaths(myMainDependencies);
      excluded = excludedRoots.get();
    }

    return new FileRootSearchScope(myModule.getProject(), excluded);
  }

  @VisibleForTesting
  void resolveDependencies() {
    AndroidModuleModel androidModel = getAndroidModel();
    synchronized (myLock) {
      if (androidModel == null || myAlreadyResolved) {
        return;
      }

      myAlreadyResolved = true;

      extractMainDependencies(androidModel);
      extractAndroidTestDependencies(androidModel);
      extractUnitTestDependencies(androidModel);

      // mainDependencies' mainDependencies should be merged to own mainDependencies, others shouldn't be merged
      mergeSubmoduleDependencies(myMainDependencies, myMainDependencies, null, null);
      // androidTestDependencies' mainDependencies and androidTestDependencies should be merged to own androidTestDependencies, others
      // shouldn't be merged
      mergeSubmoduleDependencies(myAndroidTestDependencies, myAndroidTestDependencies, myAndroidTestDependencies, null);
      // unitTestDependencies' mainDependencies and unitTestDependencies should be merged to own unitTestDependencies, others shouldn't be
      // merged
      mergeSubmoduleDependencies(myUnitTestDependencies, myUnitTestDependencies, null, myUnitTestDependencies);
    }
  }

  private void extractMainDependencies(@NotNull AndroidModuleModel androidModel) {
    synchronized (myLock) {
      if (myMainDependencies == null) {
        myMainDependencies = extractDependencies(COMPILE, androidModel.getMainArtifact());
      }
    }
  }

  private void extractUnitTestDependencies(@NotNull AndroidModuleModel androidModel) {
    synchronized (myLock) {
      if (myUnitTestDependencies == null) {
        IdeBaseArtifact artifact = androidModel.getSelectedVariant().getUnitTestArtifact();
        myUnitTestDependencies = extractTestDependencies(artifact);
      }
    }
  }

  private void extractAndroidTestDependencies(@NotNull AndroidModuleModel androidModel) {
    synchronized (myLock) {
      if (myAndroidTestDependencies == null) {
        IdeBaseArtifact artifact = androidModel.getSelectedVariant().getAndroidTestArtifact();
        myAndroidTestDependencies = extractTestDependencies(artifact);
      }
    }
  }

  @NotNull
  private static DependencySet extractTestDependencies(@Nullable IdeBaseArtifact artifact) {
    return extractDependencies(TEST, artifact);
  }

  @NotNull
  private static DependencySet extractDependencies(@NotNull DependencyScope scope, @Nullable IdeBaseArtifact artifact) {
    return artifact != null ? DependenciesExtractor.getInstance().extractFrom(artifact, scope) : DependencySet.EMPTY;
  }

  @Nullable
  private AndroidModuleModel getAndroidModel() {
    return myModule.isDisposed() ? null : AndroidModuleModel.get(myModule);
  }

  /**
   * Adds children modules' dependencies to own set of dependencies
   *
   * @param original       {@link DependencySet} where module children are
   * @param toMergeMain    the set in which should be merged children's main dependencies
   * @param toMergeAndroid the set in which should be merged children's android test dependencies
   * @param toMergeUnit    the set in which should be merged children's unit test dependencies
   */
  private void mergeSubmoduleDependencies(@NotNull DependencySet original,
                                          @Nullable DependencySet toMergeMain,
                                          @Nullable DependencySet toMergeAndroid,
                                          @Nullable DependencySet toMergeUnit) {
    // We have to copy the collection because the Map where it comes from is modified inside the loop (see http://b.android.com/230391)
    Set<ModuleDependency> moduleDependencies = new HashSet<>(original.onModules());
    synchronized (myLock) {
      for (ModuleDependency moduleDependency : moduleDependencies) {
        Module module = moduleDependency.getModule(myModule.getProject());
        if (module != null) {
          TestArtifactSearchScopes moduleScope = get(module);
          if (moduleScope != null) {
            moduleScope.resolveDependencies();
            if (toMergeMain != null) {
              toMergeMain.addAll(moduleScope.myMainDependencies);
            }
            if (toMergeAndroid != null) {
              toMergeAndroid.addAll(moduleScope.myAndroidTestDependencies);
            }
            if (toMergeUnit != null) {
              toMergeUnit.addAll(moduleScope.myUnitTestDependencies);
            }
          }
        }
      }
    }
  }

  @Override
  public void dispose() {
    myModule.putUserData(SEARCH_SCOPES_KEY, null);
  }

  @VisibleForTesting
  @Nullable
  DependencySet getMainDependencies() {
    synchronized (myLock) {
      return myMainDependencies;
    }
  }

  @VisibleForTesting
  @Nullable
  DependencySet getUnitTestDependencies() {
    synchronized (myLock) {
      return myUnitTestDependencies;
    }
  }

  @VisibleForTesting
  @Nullable
  DependencySet getAndroidTestDependencies() {
    synchronized (myLock) {
      return myAndroidTestDependencies;
    }
  }

  @Override
  public String toString() {
    return myModule.getName();
  }
}
