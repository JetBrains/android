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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;
import java.util.List;

import static com.android.testutils.TestUtils.getKotlinVersionForTests;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.util.io.FileUtil.*;

public class SingleVariantSyncIntegrationTest extends NewGradleSyncIntegrationTest {

  @Override
  protected boolean useSingleVariantSyncInfrastructure() {
    return true;
  }

  @Override
  public void testSyncIssueWithNonMatchingVariantAttributes() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());

    // DEPENDENT_MODULES project has two modules, app and lib, app module has dependency on lib module.
    loadProject(DEPENDENT_MODULES);

    // Define new buildType qa in app module.
    // This causes sync issues, because app depends on lib module, but lib module doesn't have buildType qa.
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid.buildTypes { qa { } }\n");

    // Make paidQa the selected variant, because only the selected variant is requested in Single-Variant sync.
    Module appModule = getModule("app");
    AndroidFacet facet = AndroidFacet.getInstance(appModule);
    facet.getProperties().SELECTED_BUILD_VARIANT = "paidQa";

    try {
      requestSyncAndWait();
    }
    catch (AssertionError expected) {
      // Sync issues are expected.
    }

    // Verify sync issues are reported properly.
    List<NotificationData> messages = syncMessages.getNotifications();
    assertThat(messages).hasSize(2);
    NotificationData message = messages.get(0);

    assertEquals(ERROR, message.getNotificationCategory());
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains(
      "Unable to resolve dependency for ':app@paidQa/compileClasspath': Could not resolve project :lib.\nAffected Modules:");
  }

  public void testSingleVariantSyncAfterFailedIdeaSync() throws Exception {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(false);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false;
    // Write empty CMakeLists file to force empty variants models from AGP.
    prepareProjectForImport(HELLO_JNI);
    File cmakeFile = new File(getProjectFolderPath(), join("app", "src", "main", "cpp", "CMakeLists.txt"));
    writeToFile(cmakeFile, "");
    importProject(getProject().getName(), getBaseDirPath(getProject()), null);

    // Verify Ndk model only contains one dummy variant.
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(getModule("app"));
    assertThat(ndkModuleModel.getVariants()).hasSize(1);
    assertThat(ndkModuleModel.getNdkVariantNames()).contains("-----");
    assertThat(ndkModuleModel.getVariantName("-----")).isEqualTo("---");

    // Switch to single-variant sync, and verify sync is succeeded.
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = true;
    requestSyncAndWait();
    ndkModuleModel = NdkModuleModel.get(getModule("app"));
    // Verify Single-variant sync is able to retrieve variant names with empty CMakeList.
    assertThat(ndkModuleModel.getNdkVariantNames().size()).isGreaterThan(1);
    assertThat(ndkModuleModel.getNdkVariantNames()).doesNotContain("-----");
  }

  public void testAddKotlinPluginToNonKotlinProject() throws Exception {
    loadSimpleApplication();
    // Verify that project is eligible for single-variant.
    assertFalse(PropertiesComponent.getInstance(getProject()).getBoolean((NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC)));
    assertTrue(NewGradleSync.isSingleVariantSync(getProject()));

    // Add kotlin-android plugin to top-level build file, and app module.
    ProjectBuildModel buildModel = ProjectBuildModel.get(getProject());
    buildModel.getProjectBuildModel().buildscript().dependencies()
              .addArtifact("classpath", "org.jetbrains.kotlin:kotlin-gradle-plugin:" + getKotlinVersionForTests());
    buildModel.getModuleBuildModel(getModule("app")).applyPlugin("kotlin-android");
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Request Gradle sync.
    requestSyncAndWait();

    // Verify that project is set as non-eligible for single-variant.
    assertTrue(PropertiesComponent.getInstance(getProject()).getBoolean((NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC)));
    assertFalse(NewGradleSync.isSingleVariantSync(getProject()));
  }
}
