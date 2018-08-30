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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.*;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.isEligibleForSingleVariantSync;
import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;
import static com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT;

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
    assertEquals(HAS_KOTLIN, isEligibleForSingleVariantSync(getProject()));
  }

  public void testIsEligibleWithPureJavaProject() throws Exception {
    prepareProjectForImport(PURE_JAVA_PROJECT);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);
    assertEquals(PURE_JAVA, isEligibleForSingleVariantSync(project));
  }

  public void testIsEligibleWithNativeProject() throws Exception {
    prepareProjectForImport(HELLO_JNI);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);
    assertEquals(ELIGIBLE, isEligibleForSingleVariantSync(project));
  }

  public void testIsEligibleWithKotlinModuleWithNewSync() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    loadProject(KOTLIN_GRADLE_DSL);
    assertEquals(HAS_KOTLIN, isEligibleForSingleVariantSync(getProject()));
    StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
  }
}
