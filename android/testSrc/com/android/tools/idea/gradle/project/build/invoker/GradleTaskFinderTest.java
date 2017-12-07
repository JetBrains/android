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

import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder.isCompositeBuild;
import static com.android.tools.idea.gradle.project.build.invoker.TestCompileType.UNIT_TESTS;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.android.tools.idea.testing.Facets.*;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.module.Module.EMPTY_ARRAY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleTaskFinder}.
 */
public class GradleTaskFinderTest extends IdeaTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeVariant myIdeVariant;
  @Mock private IdeBaseArtifact myArtifact;
  @Mock private TestCompileType myTestCompileType;
  @Mock private GradleRootPathFinder myRootPathFinder;

  private Module[] myModules;
  private GradleProjectSettings myProjectSettings;
  private GradleTaskFinder myTaskFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myProjectSettings = new GradleProjectSettings();
    Project project = getProject();
    String projectRootPath = getBaseDirPath(project).getPath();
    myProjectSettings.setExternalProjectPath(projectRootPath);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(myProjectSettings));

    when(myRootPathFinder.getProjectRootPath(getModule())).thenReturn(projectRootPath);

    myModules = asArray(getModule());
    myTaskFinder = new GradleTaskFinder(myRootPathFinder);
  }

  public void testCreateBuildTaskWithTopLevelModule() {
    String task = myTaskFinder.createBuildTask(":", "assemble");
    assertEquals(":assemble", task);
  }

  public void testFindTasksToExecuteWhenLastSyncFailed() {
    GradleSyncState syncState = mock(GradleSyncState.class);
    IdeComponents.replaceService(getProject(), GradleSyncState.class, syncState);
    when(syncState.lastSyncFailed()).thenReturn(true);

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, myTestCompileType);

    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).containsExactly("assemble");
  }

  public void testFindTasksToExecuteWhenAssemblingEmptyModuleList() {
    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, EMPTY_ARRAY, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).containsExactly("assemble");
  }

  public void testFindTasksWithBuildSrcModule() {
    Module module = createModule("buildSrc");

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, asArray(module), ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).isEmpty();
  }

  @NotNull
  private static Module[] asArray(@NotNull Module... modules) {
    return modules;
  }

  public void testFindTasksWithNonGradleModule() {
    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).isEmpty();
  }

  public void testFindTasksWithEmptyGradlePath() {
    createAndAddGradleFacet(getModule());

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());
    assertThat(tasks).isEmpty();
  }

  public void testFindTasksToExecuteWhenCleaningAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, CLEAN, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask2",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask2");
  }

  public void testFindTasksToExecuteForSourceGenerationInAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, SOURCE_GEN, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask2",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask2");
  }

  public void testFindTasksToExecuteForAssemblingAndroidProject() {
    setUpModuleAsAndroidModule();
    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask1",
                                      ":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask2");
  }

  public void testFindTasksToExecuteForRebuildingAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, REBUILD, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly("clean",
                                      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask1",
                                      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask2");
  }

  public void testFindTasksToExecuteForCompilingAndroidProject() {
    setUpModuleAsAndroidModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, COMPILE_JAVA, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingAndroidProject:compileTask1",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:compileTask2",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:ideSetupTask2",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteForCompilingAndroidProject:afterSyncTask2");
  }

  private void setUpModuleAsAndroidModule() {
    setUpModuleAsGradleModule();

    when(myAndroidModel.getSelectedVariant()).thenReturn(myIdeVariant);
    when(myTestCompileType.getArtifacts(myIdeVariant)).thenReturn(Collections.singleton(myArtifact));

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModelFeatures.isTestedTargetVariantsSupported()).thenReturn(false);
    when(myAndroidModel.getFeatures()).thenReturn(androidModelFeatures);

    when(myArtifact.getAssembleTaskName()).thenReturn("assembleTask1");
    when(myArtifact.getCompileTaskName()).thenReturn("compileTask1");
    when(myArtifact.getIdeSetupTaskNames()).thenReturn(Sets.newHashSet("ideSetupTask1", "ideSetupTask2"));

    Module module = getModule();
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    JpsAndroidModuleProperties state = androidFacet.getConfiguration().getState();
    assertNotNull(state);
    state.ASSEMBLE_TASK_NAME = "assembleTask2";
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2");
    state.COMPILE_JAVA_TASK_NAME = "compileTask2";

    androidFacet.setAndroidModel(myAndroidModel);
  }

  public void testFindTasksToExecuteForAssemblingJavaModule() {
    setUpModuleAsJavaModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForAssemblingJavaModule:assemble");
  }

  public void testFindTasksToExecuteForCompilingJavaModule() {
    setUpModuleAsJavaModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, COMPILE_JAVA, myTestCompileType);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

    assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingJavaModule:compileJava");
  }

  public void testFindTasksToExecuteForCompilingJavaModuleAndTests() {
    setUpModuleAsJavaModule();

    File projectPath = getBaseDirPath(getProject());
    ListMultimap<Path, String> tasksPerProject = myTaskFinder.findTasksToExecute(projectPath, myModules, COMPILE_JAVA, UNIT_TESTS);
    List<String> tasks = tasksPerProject.get(projectPath.toPath());

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
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR + module.getName();
  }

  public void testIsCompositeBuildWithoutCompositeModule() {
    // Populate projectSettings with empty composite build.
    myProjectSettings.setCompositeBuild(new CompositeBuild());

    assertFalse(isCompositeBuild(myModule));
  }

  public void testIsCompositeBuildWithCompositeModule() {
    // Set current module as composite build.
    BuildParticipant participant = new BuildParticipant();
    participant.setProjects(Collections.singleton(myModule.getModuleFile().getParent().getPath()));

    CompositeBuild compositeBuild = new CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    assertTrue(isCompositeBuild(myModule));
  }
}