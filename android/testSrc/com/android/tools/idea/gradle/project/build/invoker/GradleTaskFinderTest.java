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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.Collections;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder.isCompositeBuild;
import static com.android.tools.idea.gradle.project.build.invoker.TestCompileType.UNIT_TESTS;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.android.tools.idea.testing.Facets.*;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
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

  private Module[] myModules;
  private GradleTaskFinder myTaskFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myModules = asArray(getModule());
    myTaskFinder = GradleTaskFinder.getInstance();
  }

  public void testCreateBuildTaskWithTopLevelModule() {
    String task = myTaskFinder.createBuildTask(":", "assemble");
    assertEquals(":assemble", task);
  }

  public void testFindTasksToExecuteWhenAssemblingAndroidProjectAndLastSyncFailed() {
    GradleSyncState syncState = mock(GradleSyncState.class);
    IdeComponents.replaceService(getProject(), GradleSyncState.class, syncState);
    when(syncState.lastSyncFailed()).thenReturn(true);

    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    assertThat(tasks.values()).containsExactly("assemble");
  }

  public void testFindTasksWithBuildSrcModule() {
    Module module = createModule("buildSrc");
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(asArray(module), ASSEMBLE, myTestCompileType);
    assertThat(tasks).isEmpty();
  }

  @NotNull
  private static Module[] asArray(@NotNull Module... modules) {
    return modules;
  }

  public void testFindTasksWithNonGradleModule() {
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    assertThat(tasks).isEmpty();
  }

  public void testFindTasksWithEmptyGradlePath() {
    createAndAddGradleFacet(getModule());

    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);
    assertThat(tasks).isEmpty();
  }

  public void testFindTasksToExecuteWhenCleaningAndroidProject() {
    setUpModuleAsAndroidModule();
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, CLEAN, myTestCompileType);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask2",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask2");
  }

  public void testFindTasksToExecuteForSourceGenerationInAndroidProject() {
    setUpModuleAsAndroidModule();
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, SOURCE_GEN, myTestCompileType);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask1",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask2",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask1",
                                      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask2");
  }

  public void testFindTasksToExecuteForAssemblingAndroidProject() {
    setUpModuleAsAndroidModule();
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask1",
                                      ":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask2");
  }

  public void testFindTasksToExecuteForRebuildingAndroidProject() {
    setUpModuleAsAndroidModule();
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, REBUILD, myTestCompileType);

    assertThat(tasks.values()).containsExactly("clean",
                                      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask1",
                                      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask2");
  }

  public void testFindTasksToExecuteForCompilingAndroidProject() {
    setUpModuleAsAndroidModule();
    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, myTestCompileType);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteForCompilingAndroidProject:compileTask1",
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

    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, ASSEMBLE, myTestCompileType);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteForAssemblingJavaModule:assemble");
  }

  public void testFindTasksToExecuteForCompilingJavaModule() {
    setUpModuleAsJavaModule();

    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, myTestCompileType);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteForCompilingJavaModule:compileJava");
  }

  public void testFindTasksToExecuteForCompilingJavaModuleAndTests() {
    setUpModuleAsJavaModule();

    ListMultimap<Path, String> tasks = myTaskFinder.findTasksToExecute(myModules, COMPILE_JAVA, UNIT_TESTS);

    assertThat(tasks.values()).containsExactly(":testFindTasksToExecuteForCompilingJavaModuleAndTests:compileJava",
                                      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:testClasses");
  }

  private void setUpModuleAsJavaModule() {
    setUpModuleAsGradleModule();

    JavaFacet javaFacet = createAndAddJavaFacet(getModule());
    javaFacet.getConfiguration().BUILDABLE = true;
  }

  private void setUpModuleAsGradleModule() {
    Module module = getModule();

    ExternalSystemModulePropertyManager modulePropertyManager = new ExternalSystemModulePropertyManager(module);
    String projectPath = module.getProject().getBasePath();
    String gradlePath = ":";
    String moduleId = isEmpty(gradlePath) || ":".equals(gradlePath) ? module.getName() : gradlePath;
    ProjectSystemId owner = GradleConstants.SYSTEM_ID;
    String typeId = StdModuleTypes.JAVA.getId();
    ModuleData moduleData = new ModuleData(moduleId, owner, typeId, module.getName(), projectPath, projectPath);
    ProjectData projectData = new ProjectData(owner, module.getName(), projectPath, projectPath);
    modulePropertyManager.setExternalOptions(GradleUtil.GRADLE_SYSTEM_ID, moduleData, projectData);

    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR + module.getName();
  }

  public void testIsCompositeBuildWithoutCompositeModule() {
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setExternalProjectPath(myProject.getBaseDir().getPath());
    // Populate projectSettings with empty composite build.
    projectSettings.setCompositeBuild(new CompositeBuild());
    GradleSettings.getInstance(myProject).setLinkedProjectsSettings(Collections.singletonList(projectSettings));

    assertFalse(isCompositeBuild(myModule));
  }

  public void testIsCompositeBuildWithCompositeModule() {
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setExternalProjectPath(myProject.getBaseDir().getPath());
    // Set current module as composite build.
    CompositeBuild compositeBuild = new CompositeBuild();
    BuildParticipant participant = new BuildParticipant();
    participant.setProjects(Collections.singleton(myModule.getModuleFile().getParent().getPath()));
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    projectSettings.setCompositeBuild(compositeBuild);
    GradleSettings.getInstance(myProject).setLinkedProjectsSettings(Collections.singletonList(projectSettings));

    assertTrue(isCompositeBuild(myModule));
  }
}