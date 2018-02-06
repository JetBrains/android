/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD;
import static com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD_ROOT_PROJECT;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * Integration test with composite build.
 *
 * The dependencies from the root app looks like this:
 * :app:debugCompileClasspath
 * +--- project :lib
 * +--- com.test.composite1:lib:1.0 -> project :TestCompositeLib1:lib
 * |    +--- com.test.composite2:composite2:1.0 -> project :composite2
 * |    \--- com.test.composite3:lib:1.0 -> project :TestCompositeLib3:lib
 * |         \--- com.test.composite4:composite4:1.0 -> project :composite4
 * \--- com.test.composite4:composite4:1.0 -> project :composite4
 *
 * The modules in included project are of the following types:
 * TestCompositeLib1 :app        -> android app
 * TestCompositeLib1 :lib        -> android lib
 * TestCompositeLib2 :           -> java
 * TestCompositeLib3 :app        -> android app
 * TestCompositeLib3 :lib        -> android lib
 * TestCompositeLib4 :           -> java
 */
public class GradleSyncWithCompositeBuildTest extends GradleSyncIntegrationTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    prepareCompositeProject();
  }

  // Copy included projects, update wrapper and gradle files for included projects.
  private void prepareCompositeProject() throws IOException {
    File testDataRoot = new File(getTestDataPath(), toSystemDependentName(COMPOSITE_BUILD));
    File projectRoot = virtualToIoFile(myFixture.getProject().getBaseDir());

    List<String> includedProjects = asList("TestCompositeLib1", "TestCompositeLib2", "TestCompositeLib3", "TestCompositeLib4");
    for (String includedProject : includedProjects) {
      File srcRoot = new File(testDataRoot, includedProject);
      File includedProjectRoot = new File(projectRoot, includedProject);
      prepareProjectForImport(srcRoot, includedProjectRoot);
    }
  }

  @Override
  protected boolean useNewSyncInfrastructure() {
    return false;
  }

  public void testModulesCreatedForIncludedProjects() throws Exception {
    loadProject(COMPOSITE_BUILD_ROOT_PROJECT);
    List<Module> modules = new ArrayList<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      modules.addAll(asList(ModuleManager.getInstance(getProject()).getModules()));
    });
    // Verify that modules are created for root project as well as included projects.
    assertThat(modules).hasSize(11);
    // Verify module names.
    List<String> moduleNames = modules.stream().map(Module::getName).collect(toList());
    String projectName = getProject().getName();
    List<String> expectedModuleNames = asList(projectName, projectName + "-app", projectName + "-lib",
                                              "TestCompositeLib1", "TestCompositeLib1-app", "TestCompositeLib1-lib",
                                              "TestCompositeLib3", "TestCompositeLib3-app", "TestCompositeLib3-lib",
                                              "composite2", "composite4");
    assertThat(moduleNames).containsExactlyElementsIn(expectedModuleNames);
  }

  public void testModuleDependenciesWithRootAppModule() throws Exception {
    loadProject(COMPOSITE_BUILD_ROOT_PROJECT);
    String projectName = getProject().getName();
    String rootAppModuleName = projectName + "-app";
    Module rootAppModule = myModules.getModule(rootAppModuleName);
    // Verify that app module has dependency on direct and transitive lib modules.
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency(projectName + "-lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency("TestCompositeLib1-lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency("composite2", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency("TestCompositeLib3-lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency("composite4", COMPILE, false);
  }

  public void testModuleDependenciesWithIncludedAppModule() throws Exception {
    loadProject(COMPOSITE_BUILD_ROOT_PROJECT);
    String appModuleName = "TestCompositeLib1-app";
    Module appModule = myModules.getModule(appModuleName);
    // Verify that app module has dependency on direct and transitive lib modules.
    assertAbout(moduleDependencies()).that(appModule).hasDependency("TestCompositeLib1-lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency("composite2", COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency("TestCompositeLib3-lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency("composite4", COMPILE, false);
  }
}
