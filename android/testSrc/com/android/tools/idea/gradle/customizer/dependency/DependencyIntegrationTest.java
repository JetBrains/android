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
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Integration tests for {@link Dependency}.
 */
public class DependencyIntegrationTest extends AndroidGradleTestCase {
  // See: https://code.google.com/p/android/issues/detail?id=210172
  public void testTransitiveDependenciesFromJavaModule() throws Throwable {
    loadProject("projects/transitiveDependencies");

    Module appModule = ModuleManager.getInstance(getProject()).findModuleByName("app");
    assertNotNull(appModule);

    AndroidGradleModel gradleModel = AndroidGradleModel.get(appModule);
    assertNotNull(gradleModel);
    assertTrue(gradleModel.supportsDependencyGraph());

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

    // 'app' module should have 'guava' as dependency.
    // 'guava' -> 'lib' -> 'guava'
    String libraryName = "guava-17.0";
    if (!hasLibraryDependency(appModule, libraryName)) {
      List<String> allLibraries = Lists.newArrayList();
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(appModule).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry) {
          LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
          String name = libraryEntry.getLibraryName();
          allLibraries.add(name);
        }
      }
      fail("Unable to find library dependency '" + libraryName + "' " + allLibraries);
    }
  }

  private static boolean hasLibraryDependency(@NotNull Module appModule, @NotNull String libraryName) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(appModule).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
        String name = libraryEntry.getLibraryName();
        if (libraryName.equals(name)) {
          return true;
        }
      }
    }
    return false;
  }
}