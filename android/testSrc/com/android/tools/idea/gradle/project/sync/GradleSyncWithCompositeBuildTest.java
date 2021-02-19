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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static java.util.Arrays.asList;

import com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration test with composite build.
 * <p>
 * The dependencies from the root app looks like this:
 * :app:debugCompileClasspath
 * +--- project :lib
 * +--- com.test.composite1:lib:1.0 -> project :TestCompositeLib1:lib
 * |    +--- com.test.composite2:composite2:1.0 -> project :composite2
 * |    \--- com.test.composite3:lib:1.0 -> project :TestCompositeLib3:lib
 * |         \--- com.test.composite4:composite4:1.0 -> project :composite4
 * \--- com.test.composite4:composite4:1.0 -> project :composite4
 * <p>
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
  protected boolean useSingleVariantSyncInfrastructure() {
    return false;
  }

  public void testModulesCreatedForIncludedProjects() throws Exception {
    loadProject(COMPOSITE_BUILD);
    List<Module> modules = new ArrayList<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      modules.addAll(asList(ModuleManager.getInstance(getProject()).getModules()));
    });
    // Verify that modules are created for root project as well as included projects.
    assertThat(modules).hasSize(11);
    // Verify module names.
    List<String> moduleNames = ContainerUtil.map(modules, Module::getName);
    String projectName = getProject().getName();
    List<String> expectedModuleNames = asList(projectName, projectName + ".app", projectName + ".lib",
                                              "TestCompositeLib1", "TestCompositeLib1.app", "TestCompositeLib1.lib",
                                              "TestCompositeLib3", "TestCompositeLib3.app", "TestCompositeLib3.lib",
                                              "composite2", "composite4");
    assertThat(moduleNames).containsExactlyElementsIn(expectedModuleNames);
  }

  public void testModuleDependenciesWithRootAppModule() throws Exception {
    loadProject(COMPOSITE_BUILD);
    String projectName = getProject().getName();
    String rootAppModuleName = projectName + ".app";
    Module rootAppModule = TestModuleUtil.findModule(getProject(), rootAppModuleName);
    // Verify that app module has dependency on direct and transitive lib modules.
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency(projectName + ".lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency("TestCompositeLib1.lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency(getMainSourceSet("composite2"), COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency("TestCompositeLib3.lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(rootAppModule).hasDependency(getMainSourceSet("composite4"), COMPILE, false);
  }

  public void testModuleDependenciesWithIncludedAppModule() throws Exception {
    loadProject(COMPOSITE_BUILD);
    String appModuleName = "TestCompositeLib1.app";
    Module appModule = TestModuleUtil.findModule(getProject(), appModuleName);
    // Verify that app module has dependency on direct and transitive lib modules.
    assertAbout(moduleDependencies()).that(appModule).hasDependency("TestCompositeLib1.lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency(getMainSourceSet("composite2"), COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency("TestCompositeLib3.lib", COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency(getMainSourceSet("composite4"), COMPILE, false);
  }

  public void testGetAssembleTasks() throws Exception {
    loadProject(COMPOSITE_BUILD);
    Module[] modules = new Module[]{
      TestModuleUtil.findModule(getProject(), "TestCompositeLib1.app"),
      TestModuleUtil.findModule(getProject(), "TestCompositeLib3.app"),
      TestModuleUtil.findModule(getProject(), getProject().getName() + ".app")};
    ListMultimap<Path, String> tasksPerProject = GradleTaskFinder.getInstance().findTasksToExecute(modules, ASSEMBLE, TestCompileType.ALL);
    // Verify that each included project has task list.
    assertThat(tasksPerProject.asMap()).hasSize(3);

    File rootProjectPath = getBaseDirPath(getProject());
    File compositeLib1Path = new File(rootProjectPath, "TestCompositeLib1");
    File compositeLib3Path = new File(rootProjectPath, "TestCompositeLib3");

    assertThat(tasksPerProject.get(rootProjectPath.toPath())).containsExactly(":app:assembleDebug");
    assertThat(tasksPerProject.get(compositeLib1Path.toPath())).containsExactly(":app:assembleDebug");
    assertThat(tasksPerProject.get(compositeLib3Path.toPath())).containsExactly(":app:assembleDebug");
  }

  public void testGetSourceGenerationTasks() throws Exception {
    loadProject(COMPOSITE_BUILD);
    Module[] modules = new Module[]{
      TestModuleUtil.findModule(getProject(), "TestCompositeLib1.app"),
      TestModuleUtil.findModule(getProject(), "composite2"),
      TestModuleUtil.findModule(getProject(), "TestCompositeLib3.lib"),
      TestModuleUtil.findModule(getProject(), "composite4"),
      TestModuleUtil.findModule(getProject(), getProject().getName() + ".app")};
    ListMultimap<Path, String> tasksPerProject =
      GradleTaskFinder.getInstance().findTasksToExecute(modules, SOURCE_GEN, TestCompileType.ALL);

    // Verify that each included project has task list.
    assertThat(tasksPerProject.asMap()).hasSize(5);

    File rootProjectPath = getBaseDirPath(getProject());
    File compositeLib1Path = new File(rootProjectPath, "TestCompositeLib1");
    File compositeLib2Path = new File(rootProjectPath, "TestCompositeLib2");
    File compositeLib3Path = new File(rootProjectPath, "TestCompositeLib3");
    File compositeLib4Path = new File(rootProjectPath, "TestCompositeLib4");

    assertThat(tasksPerProject.get(rootProjectPath.toPath()))
      .containsExactly(":app:generateDebugSources", ":app:generateDebugAndroidTestSources", ":app:createMockableJar");
    assertThat(tasksPerProject.get(compositeLib1Path.toPath()))
      .containsExactly(":app:generateDebugSources", ":app:generateDebugAndroidTestSources", ":app:createMockableJar");
    assertThat(tasksPerProject.get(compositeLib2Path.toPath())).containsExactly(":testClasses");
    assertThat(tasksPerProject.get(compositeLib3Path.toPath()))
      .containsExactly(":lib:generateDebugSources", ":lib:generateDebugAndroidTestSources", ":lib:createMockableJar");
    assertThat(tasksPerProject.get(compositeLib4Path.toPath())).containsExactly(":testClasses");
  }
}
