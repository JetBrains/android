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

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_TEST;
import static com.android.tools.idea.testartifacts.scopes.ExcludedRoots.getAllSourceFolders;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependencySet;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gradle implementation of {@link TestArtifactSearchScopes}, differentiates {@code test/} and {@code androidTest/} sources based on
 * information from the model.
 */
public final class GradleTestArtifactSearchScopes implements TestArtifactSearchScopes {
  private static final Key<GradleTestArtifactSearchScopes> SEARCH_SCOPES_KEY = Key.create("TEST_ARTIFACT_SEARCH_SCOPES");

  @NotNull private final Module myModule;

  private FileRootSearchScope myAndroidTestSourceScope;
  private FileRootSearchScope myUnitTestSourceScope;
  private FileRootSearchScope myAndroidTestExcludeScope;
  private FileRootSearchScope myUnitTestExcludeScope;
  private FileRootSearchScope myAndroidTestDependencyExcludeScope;
  private FileRootSearchScope mySharedTestsExcludeScope;
  private FileRootSearchScope myUnitTestDependencyExcludeScope;

  private static final Object ourLock = new Object();

  private DependencySet myMainDependencies;
  private DependencySet myUnitTestDependencies;
  private DependencySet myAndroidTestDependencies;
  private boolean myAlreadyResolved;

  @Nullable
  public static GradleTestArtifactSearchScopes getInstance(@NotNull Module module) {
    if (GradleSyncState.getInstance(module.getProject()).lastSyncFailed()) {
      return null;
    }

    return module.getUserData(SEARCH_SCOPES_KEY);
  }

  /**
   * Initialize the test scopes in the given module if the module is Gradle-based Android.
   *
   * @param module the given module.
   */
  public static void initializeScope(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    GradleTestArtifactSearchScopes scopes = androidModel == null ? null : new GradleTestArtifactSearchScopes(module);
    module.putUserData(SEARCH_SCOPES_KEY, scopes);
  }

  private GradleTestArtifactSearchScopes(@NotNull Module module) {
    myModule = module;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  public boolean isAndroidTestSource(@NotNull VirtualFile file) {
    return getAndroidTestSourceScope().accept(file);
  }

  @Override
  public boolean isUnitTestSource(@NotNull VirtualFile file) {
    return getUnitTestSourceScope().accept(file);
  }

  @Override
  @NotNull
  public FileRootSearchScope getAndroidTestSourceScope() {
    if (myAndroidTestSourceScope == null) {
      myAndroidTestSourceScope = getSourceScope(ARTIFACT_ANDROID_TEST);
    }
    return myAndroidTestSourceScope;
  }

  @Override
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
        // Special case where android tests correspond actually to the _main_ artifact (i.e. com.android.test plugin).
        // There is only instrumentation test artifacts in this project type so the whole directory is in testing scope.
        roots.add(androidModel.getRootDirPath());
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

  /**
   * Returns a {@link GlobalSearchScope} that contains files to be excluded from resolution inside shared tests.
   *
   * <p>Note that currently there are no shared tests in AGP.
   */
  @Override
  @NotNull
  public FileRootSearchScope getSharedTestExcludeScope() {
    if (mySharedTestsExcludeScope == null) {
      // When a file is shared by both tests, then the test should only access the dependencies that android test and unit test both
      // have. Since the API requires us return a excluding scope, we want to exclude all the dependencies android test doesn't
      // includes and the ones that unit test doesn't have.
      mySharedTestsExcludeScope = getAndroidTestExcludeClasspathScope().add(getUnitTestExcludeClasspathScope());
    }
    return mySharedTestsExcludeScope;
  }

  @Override
  @NotNull
  public FileRootSearchScope getAndroidTestExcludeScope() {
    if (myAndroidTestExcludeScope == null) {
      // Exclude all unit tests, unless some of them are also android tests (currently that's never the case).
      FileRootSearchScope exclude = getUnitTestSourceScope().subtract(getAndroidTestSourceScope());
      // Exclude all dependencies which are only for unit tests.
      myAndroidTestExcludeScope = exclude.add(getAndroidTestExcludeClasspathScope());
    }
    return myAndroidTestExcludeScope;
  }

  @Override
  @NotNull
  public FileRootSearchScope getUnitTestExcludeScope() {
    if (myUnitTestExcludeScope == null) {
      // Exclude all android tests, unless some of them are also unit tests (currently that's never the case).
      FileRootSearchScope exclude = getAndroidTestSourceScope().subtract(getUnitTestSourceScope());
      // Exclude all dependencies which are only for android tests.
      myUnitTestExcludeScope = exclude.add(getUnitTestExcludeClasspathScope());
    }
    return myUnitTestExcludeScope;
  }

  @Override
  public boolean includeInUnitTestClasspath(@NotNull File file) {
    return !getUnitTestExcludeScope().accept(file);
  }

  @NotNull
  private FileRootSearchScope getAndroidTestExcludeClasspathScope() {
    if (myAndroidTestDependencyExcludeScope == null) {
      myAndroidTestDependencyExcludeScope = getExcludeClasspathScope(ARTIFACT_ANDROID_TEST);
    }
    return myAndroidTestDependencyExcludeScope;
  }

  @NotNull
  private FileRootSearchScope getUnitTestExcludeClasspathScope() {
    if (myUnitTestDependencyExcludeScope == null) {
      myUnitTestDependencyExcludeScope = getExcludeClasspathScope(ARTIFACT_UNIT_TEST);
    }
    return myUnitTestDependencyExcludeScope;
  }

  @NotNull
  private FileRootSearchScope getExcludeClasspathScope(@NotNull String artifactName) {
    if (getAndroidModel() == null) {
      return new FileRootSearchScope(myModule.getProject(), Collections.emptyList());
    }

    resolveDependencies();

    boolean isAndroidTest = ARTIFACT_ANDROID_TEST.equals(artifactName);
    Collection<File> excluded;
    synchronized (ourLock) {
      DependencySet dependenciesToInclude = isAndroidTest ? myAndroidTestDependencies : myUnitTestDependencies;
      DependencySet dependenciesToExclude = isAndroidTest ? myUnitTestDependencies : myAndroidTestDependencies;

      ExcludedModules excludedModules = new ExcludedModules(myModule);
      excludedModules.add(dependenciesToExclude);
      excludedModules.remove(dependenciesToInclude);
      excludedModules.remove(myMainDependencies);

      ExcludedRoots excludedRoots =
        new ExcludedRoots(excludedModules, dependenciesToExclude, dependenciesToInclude, isAndroidTest);
      excludedRoots.removeLibraryPaths(myMainDependencies);
      excluded = excludedRoots.get();
    }

    return new FileRootSearchScope(myModule.getProject(), excluded);
  }

  @VisibleForTesting
  void resolveDependencies() {
    AndroidModuleModel androidModel = getAndroidModel();
    synchronized (ourLock) {
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
    synchronized (ourLock) {
      if (myMainDependencies == null) {
        myMainDependencies = extractDependencies(COMPILE, androidModel.getMainArtifact());
      }
    }
  }

  private void extractUnitTestDependencies(@NotNull AndroidModuleModel androidModel) {
    synchronized (ourLock) {
      if (myUnitTestDependencies == null) {
        IdeBaseArtifact artifact = androidModel.getSelectedVariant().getUnitTestArtifact();
        myUnitTestDependencies = extractTestDependencies(artifact);
      }
    }
  }

  private void extractAndroidTestDependencies(@NotNull AndroidModuleModel androidModel) {
    synchronized (ourLock) {
      if (myAndroidTestDependencies == null) {
        IdeBaseArtifact artifact = androidModel.getSelectedVariant().getAndroidTestArtifact();
        myAndroidTestDependencies = extractTestDependencies(artifact);
      }
    }
  }

  @NotNull
  private DependencySet extractTestDependencies(@Nullable IdeBaseArtifact artifact) {
    return extractDependencies(TEST, artifact);
  }

  @NotNull
  private DependencySet extractDependencies(@NotNull DependencyScope scope, @Nullable IdeBaseArtifact artifact) {
    if (artifact != null) {
      ModuleFinder moduleFinder = ProjectStructure.getInstance(myModule.getProject()).getModuleFinder();
      return DependenciesExtractor.getInstance().extractFrom(artifact, scope, moduleFinder);
    }
    return DependencySet.EMPTY;
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
  private static void mergeSubmoduleDependencies(@NotNull DependencySet original,
                                                 @Nullable DependencySet toMergeMain,
                                                 @Nullable DependencySet toMergeAndroid,
                                                 @Nullable DependencySet toMergeUnit) {
    // We have to copy the collection because the Map where it comes from is modified inside the loop (see http://b.android.com/230391)
    Set<ModuleDependency> moduleDependencies = new HashSet<>(original.onModules());
    synchronized (ourLock) {
      for (ModuleDependency moduleDependency : moduleDependencies) {
        Module module = moduleDependency.getModule();
        if (module != null) {
          GradleTestArtifactSearchScopes moduleScope = getInstance(module);
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

  @VisibleForTesting
  @Nullable
  DependencySet getMainDependencies() {
    synchronized (ourLock) {
      return myMainDependencies;
    }
  }

  @VisibleForTesting
  @Nullable
  DependencySet getUnitTestDependencies() {
    synchronized (ourLock) {
      return myUnitTestDependencies;
    }
  }

  @VisibleForTesting
  @Nullable
  DependencySet getAndroidTestDependencies() {
    synchronized (ourLock) {
      return myAndroidTestDependencies;
    }
  }

  @Override
  public String toString() {
    return myModule.getName();
  }
}
