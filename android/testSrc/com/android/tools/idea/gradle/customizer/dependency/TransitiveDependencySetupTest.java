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
package com.android.tools.idea.gradle.customizer.dependency;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.testFramework.LeakHunter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Integration test that verifies that transitive dependencies are set up correctly.
 */
public class TransitiveDependencySetupTest extends AndroidGradleTestCase {

  @Override
  protected void runTest() throws Throwable {
    // Ignore this whole class. See http://b.android.com/221883.
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    LeakHunter.checkLeak(LeakHunter.allRoots(), AndroidGradleModel.class, null);
  }

  // See: https://code.google.com/p/android/issues/detail?id=210172
  public void testTransitiveDependenciesFromJavaModule() throws Throwable {
    loadProject("projects/transitiveDependencies");

    Module appModule = getAppModule();
    verifyDependenciesAreResolved(appModule);

    // 'app' module should have 'guava' as dependency.
    // 'app' -> 'lib' -> 'guava'
    assertThat(getLibraries(appModule, COMPILE)).contains("guava-17.0");
  }

  // See: https://code.google.com/p/android/issues/detail?id=212338
  public void testTransitiveDependenciesFromAndroidModule() throws Throwable {
    loadProject("projects/transitiveDependencies");

    Module appModule = getAppModule();
    verifyDependenciesAreResolved(appModule);

    // 'app' module should have 'javawriter' as dependency.
    // 'app' -> 'library2' -> 'library1' -> 'javawriter'
    assertThat(getLibraries(appModule, COMPILE)).contains("javawriter-2.5.0");
  }

  @NotNull
  private static List<String> getLibraries(@NotNull Module module, @NotNull DependencyScope scope) {
    List<String> allLibraries = Lists.newArrayList();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry && scope.equals(((LibraryOrderEntry)orderEntry).getScope())) {
        LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
        String name = libraryEntry.getLibraryName();
        allLibraries.add(name);
      }
    }
    return allLibraries;
  }

  // See: https://code.google.com/p/android/issues/detail?id=212557
  public void testTransitiveAndroidModuleDependency() throws Throwable {
    loadProject("projects/transitiveDependencies");

    Module appModule = getAppModule();
    verifyDependenciesAreResolved(appModule);

    // 'app' module should have 'library1' as module dependency.
    // 'app' -> 'library2' -> 'library1'
    assertThat(moduleDependencies(appModule, COMPILE)).contains("library1");
  }

  public void testJavaLibraryModuleDependencies() throws Throwable {
    loadProject("projects/transitiveDependencies");

    Module appModule = getAppModule();
    verifyDependenciesAreResolved(appModule);

    // 'app' module should have 'guava' as dependency.
    // 'app' -> 'lib' -> 'guava'
    assertThat(moduleDependencies(appModule, COMPILE)).contains("lib");
    assertThat(getLibraries(appModule, COMPILE)).excludes("lib");
  }

  public void testDependencySetUpInJavaModule() throws Throwable {
    loadProject("projects/transitiveDependencies");

    Module libModule = getModule("lib");
    assertThat(getLibraries(libModule, COMPILE)).excludes("lib.lib");
  }

  // See: https://code.google.com/p/android/issues/detail?id=213627
  // Disabled. It fails on CI. It passes when running locally.
  public void /*test*/JarsInLibsFolder() throws Throwable {
    loadProject("projects/transitiveDependencies");

    // 'fakelib' is in 'libs' directory in 'library2' module.
    Module library2Module = getModule("library2");
    verifyDependenciesAreResolved(library2Module);
    assertThat(getLibraries(library2Module, COMPILE)).contains("fakelib");

    // 'app' module should have 'fakelib' as dependency.
    // 'app' -> 'library2' -> 'fakelib'
    Module appModule = getAppModule();
    verifyDependenciesAreResolved(appModule);
    assertThat(getLibraries(appModule, COMPILE)).contains("fakelib");
  }

  @NotNull
  private Module getAppModule() {
    String name = "app";
    return getModule(name);
  }

  @NotNull
  private Module getModule(@NotNull String name) {
    Module module = ModuleManager.getInstance(getProject()).findModuleByName(name);
    assertNotNull(module);
    return module;
  }

  private static void verifyDependenciesAreResolved(@NotNull Module module) {
    AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
    assertNotNull(gradleModel);
    //assertTrue(gradleModel.getFeatures().isDependencyGraphSupported());

    AndroidProject androidProject = gradleModel.getAndroidProject();
    List<String> unresolvedDependencies = Lists.newArrayList();
    for (SyncIssue syncIssue : androidProject.getSyncIssues()) {
      if (syncIssue.getType() == SyncIssue.TYPE_UNRESOLVED_DEPENDENCY) {
        unresolvedDependencies.add(syncIssue.getData());
      }
    }
    if (!unresolvedDependencies.isEmpty()) {
      fail("Unresolved dependencies: " + unresolvedDependencies);
    }
  }

  @NotNull
  private static List<String> moduleDependencies(@NotNull Module module, @NotNull DependencyScope scope) {
    List<String> allLibraries = Lists.newArrayList();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof ModuleOrderEntry && scope.equals(((ModuleOrderEntry)orderEntry).getScope())) {
        ModuleOrderEntry entry = (ModuleOrderEntry)orderEntry;
        String name = entry.getModuleName();
        allLibraries.add(name);
      }
    }
    return allLibraries;
  }
}
