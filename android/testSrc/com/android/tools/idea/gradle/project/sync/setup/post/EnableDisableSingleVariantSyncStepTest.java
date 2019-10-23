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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.BUILDSRC_MODULE;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.ELIGIBLE;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.KOTLIN;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.PURE_JAVA;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.isEligibleForSingleVariantSync;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.setSingleVariantSyncState;
import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;
import static com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import java.io.File;

/**
 * Tests for {@link EnableDisableSingleVariantSyncStep}.
 */
public class EnableDisableSingleVariantSyncStepTest extends AndroidGradleTestCase {
  public void testIsEligible() throws Exception {
    loadSimpleApplication();
    assertEquals(ELIGIBLE, isEligibleForSingleVariantSync(getProject()));
  }

  public void testIsEligibleWithKotlinModule() throws Exception {
    loadProject(KOTLIN_GRADLE_DSL);
    assertEquals(KOTLIN, isEligibleForSingleVariantSync(getProject()));
  }

  public void testIsEligibleWithPureJavaProject() throws Exception {
    prepareProjectForImport(PURE_JAVA_PROJECT);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project));
    assertEquals(PURE_JAVA, isEligibleForSingleVariantSync(project));
  }

  public void testIsEligibleWithNativeProject() throws Exception {
    prepareProjectForImport(HELLO_JNI);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project));
    assertEquals(ELIGIBLE, isEligibleForSingleVariantSync(project));
  }

  public void testIsEligibleWithKotlinModuleWithNewSync() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    loadProject(KOTLIN_GRADLE_DSL);
    assertEquals(KOTLIN, isEligibleForSingleVariantSync(getProject()));
    StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
  }

  public void testSetSingleVariantSyncState() {
    Project project = getProject();
    // By default project is eligible.
    assertFalse(PropertiesComponent.getInstance(project).getBoolean(NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC));
    // Update eligibility state.
    setSingleVariantSyncState(project);
    // Verify the flag is updated to true.
    assertTrue(PropertiesComponent.getInstance(project).getBoolean(NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC));
  }

  public void testIsEligibleWithBuildSrcModule() throws Exception {
    prepareProjectForImport(SIMPLE_APPLICATION);
    // Create buildSrc folder under root project.
    Project project = getProject();
    File buildSrcDir = new File(project.getBasePath(), "buildSrc");
    File buildFile = new File(buildSrcDir, "build.gradle");
    writeToFile(buildFile, "repositories {}");
    importProject(project.getName(), getBaseDirPath(project));
    // Verify that project is not eligible due to buildSrc module.
    assertEquals(BUILDSRC_MODULE, isEligibleForSingleVariantSync(project));
  }
}
