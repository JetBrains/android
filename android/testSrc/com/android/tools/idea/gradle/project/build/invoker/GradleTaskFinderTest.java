/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.nio.file.Paths;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.build.invoker.TestCompileType.UNIT_TESTS;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.android.tools.idea.testing.Facets.*;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleTaskFinder}.
 */
public class GradleTaskFinderTest extends PlatformTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myIdeAndroidProject;
  @Mock private IdeVariant myIdeVariant;
  @Mock private IdeAndroidArtifact myMainArtifact;
  @Mock private IdeBaseArtifact myArtifact;
  @Mock private TestCompileType myTestCompileType;
  @Mock private GradleRootPathFinder myRootPathFinder;
  @Mock private AndroidModuleModel myAndroidModel2;
  @Mock private IdeAndroidProject myIdeAndroidProject2;

  private Module[] myModules;
  private GradleTaskFinder myTaskFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    String projectRootPath = getBaseDirPath(project).getPath();
    when(myRootPathFinder.getProjectRootPath(getModule())).thenReturn(Paths.get(projectRootPath));

    myModules = asArray(getModule());
    myTaskFinder = new GradleTaskFinder(myRootPathFinder);
  }

  public void testCreateBuildTaskWithTopLevelModule() {
    String task = myTaskFinder.createBuildTask(":", "assemble");
    assertEquals(":assemble", task);
  }

  public void testFindTasksToExecuteWhenLastSyncFailed() {
    GradleSyncState syncState = mock(GradleSyncState.class);
    new IdeComponents(getProject()).replaceProjectService(GradleSyncState.class, syncState);
    when(syncState.lastSyncFailed()).thenReturn(true);

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);

    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).containsExactly("assemble");
  }

  public void testFindTasksWithBuildSrcModule() {
    Module module = createModule("buildSrc");

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(asArray(module), ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).isEmpty();
  }

  @NotNull
  private static Module[] asArray(@NotNull Module... modules) {
    return modules;
  }

  public void testFindTasksWithNonGradleModule() {
    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).isEmpty();
  }

  public void testFindTasksWithEmptyGradlePath() {
    createAndAddGradleFacet(getModule());

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).isEmpty();
  }

  public void testFindTasksToExecuteWhenCleaningAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, CLEAN, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask2",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask2");
  }

  public void testFindTasksToExecuteForSourceGenerationInAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, SOURCE_GEN, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask2",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask2");
  }

  public void testFindTasksToExecuteForAssemblingAndroidProject() {
    setUpModuleAsAndroidModule();
    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask1",
                                      ":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask2");
  }

  public void testFindTasksToExecuteForRebuildingAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, REBUILD, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly("clean",
                                      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask1",
                                      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask2");
    // Make sure clean is the first task (b/78443416)
    assertThat(tasks.get(0)).isEqualTo("clean");
  }

  public void testFindTasksToExecuteForCompilingAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingAndroidProject:compileTask1",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:compileTask2",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:ideSetupTask2",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:afterSyncTask2");
  }

  public void testFindTasksToExecuteForCompilingDynamicApp() {
    setUpModuleAsAndroidModule();

    // Create and setup dynamic feature module
    Module featureModule = createModule("feature1");
    setUpModuleAsAndroidModule(featureModule, myAndroidModel2, myIdeAndroidProject2);
    when(myIdeAndroidProject.getDynamicFeatures()).thenReturn(ImmutableList.of(":feature1"));
    when(myIdeAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_DYNAMIC_FEATURE);
    String projectRootPath = getBaseDirPath(getProject()).getPath();
    when(myRootPathFinder.getProjectRootPath(featureModule)).thenReturn(Paths.get(projectRootPath));

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingDynamicApp:assembleTask1",
                                      ":testFindTasksToExecuteForCompilingDynamicApp:assembleTask2",
                                      ":feature1:assembleTask1",
                                      ":feature1:assembleTask2");
  }

  public void testFindTasksToExecuteForBundleTool() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, BUNDLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForBundleTool:bundleTask1");
  }

  public void testFindTasksToExecuteForApkFromBundle() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, APK_FROM_BUNDLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForApkFromBundle:apkFromBundleTask1");
  }

  private void setUpModuleAsAndroidModule() {
    setUpModuleAsAndroidModule(getModule(), myAndroidModel, myIdeAndroidProject);
  }

  private void setUpModuleAsAndroidModule(Module module, AndroidModuleModel androidModel, IdeAndroidProject ideAndroidProject) {
    setUpModuleAsGradleModule(module);

    when(androidModel.getSelectedVariant()).thenReturn(myIdeVariant);
    when(myTestCompileType.getArtifacts(myIdeVariant)).thenReturn(Collections.singleton(myArtifact));
    when(androidModel.getAndroidProject()).thenReturn(ideAndroidProject);

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModelFeatures.isTestedTargetVariantsSupported()).thenReturn(false);
    when(androidModel.getFeatures()).thenReturn(androidModelFeatures);
    when(myIdeVariant.getMainArtifact()).thenReturn(myMainArtifact);
    when(myMainArtifact.getBundleTaskName()).thenReturn("bundleTask1");
    when(myMainArtifact.getApkFromBundleTaskName()).thenReturn("apkFromBundleTask1");

    when(myArtifact.getAssembleTaskName()).thenReturn("assembleTask1");
    when(myArtifact.getCompileTaskName()).thenReturn("compileTask1");
    when(myArtifact.getIdeSetupTaskNames()).thenReturn(Sets.newHashSet("ideSetupTask1", "ideSetupTask2"));

    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    JpsAndroidModuleProperties state = androidFacet.getConfiguration().getState();
    assertNotNull(state);
    state.ASSEMBLE_TASK_NAME = "assembleTask2";
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2");
    state.COMPILE_JAVA_TASK_NAME = "compileTask2";

    androidFacet.getConfiguration().setModel(androidModel);
  }

  public void testFindTasksToExecuteForAssemblingJavaModule() {
    setUpModuleAsJavaModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForAssemblingJavaModule:assemble");
  }

  public void testFindTasksToExecuteForCompilingJavaModule() {
    setUpModuleAsJavaModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingJavaModule:compileJava");
  }

  public void testFindTasksToExecuteForCompilingJavaModuleAndTests() {
    setUpModuleAsJavaModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, UNIT_TESTS);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingJavaModuleAndTests:compileJava",
                                      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:testClasses");

    // check it also for TestCompileType.ALL
    tasksPerProject = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, TestCompileType.ALL);
    tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingJavaModuleAndTests:compileJava",
                                      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:testClasses");
  }

  private void setUpModuleAsJavaModule() {
    setUpModuleAsGradleModule();

    JavaFacet javaFacet = createAndAddJavaFacet(getModule());
    javaFacet.getConfiguration().BUILDABLE = true;
  }

  private void setUpModuleAsGradleModule() {
    Module module = getModule();
    setUpModuleAsGradleModule(module);
  }

  private static void setUpModuleAsGradleModule(@NotNull Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR + module.getName();

    String gradlePath = GRADLE_PATH_SEPARATOR + module.getName();
    GradleProject gradleProjectStub = new GradleProjectStub(emptyList(), gradlePath, getBaseDirPath(module.getProject()));
    GradleModuleModel model = new GradleModuleModel(module.getName(), gradleProjectStub, emptyList(), null, null, null, null);

    gradleFacet.setGradleModuleModel(model);
  }
}
