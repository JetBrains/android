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
package com.android.tools.idea.npw.project;

import static com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion;
import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS;
import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.determineKotlinVersion;
import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.getContainingModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.requestSyncAndWait;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion;
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersionKt;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@RunsInEdt
public class AndroidGradleModuleUtilsTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule(getAgpVersion());
  @Rule
  public RuleChain rule = RuleChain.outerRule(projectRule).around(new EdtRule());

  @Test
  public void testGetContainingModule() {
    projectRule.loadProject(IMPORTING);
    Project project = projectRule.getProject();
    File archiveToImport = new File(project.getBasePath(), join("simple", "lib", "library.jar"));

    assertThat(getContainingModule(archiveToImport, project)).isEqualTo(projectRule.getModule("simple"));
  }

  @Test
  public void testGetContainingModuleNested() {
    projectRule.loadProject(IMPORTING);
    Project project = projectRule.getProject();
    File archiveToImport = new File(project.getBasePath(), join("nested", "sourcemodule", "lib", "library.jar"));

    assertThat(getContainingModule(archiveToImport, project)).isEqualTo(projectRule.getModule("sourcemodule"));
  }

  @Test
  public void testGetContainingModuleNotInModule() {
    File file = new File(projectRule.getFixture().getTestDataPath(), join(IMPORTING, "simple", "lib", "library.jar"));
    Module module = getContainingModule(file, projectRule.getProject());
    assertThat(module).isNull();
  }

  /**
   * This test checks that the default Kotlin Gradle plugin version is equal to or more recent than
   * the version bundled in the current Android Gradle plugin.
   */
  @Test
  public void defaultKotlinVersionMatchesAgpBundledVersion() {
    // Configure the project to use the latest AGP version
    projectRule.loadProject(
      TestProjectPaths.SIMPLE_APPLICATION,
      getAgpVersion()
    );
    requestSyncAndWait(projectRule.getProject());

    // Get the resolved Kotlin version from the project model - this should equal the version
    // of the kotlin-gradle-plugin used by AGP
    KotlinGradlePluginVersion resolvedKotlinVersion =
      determineKotlinVersion(projectRule.getProject());
    assertThat(resolvedKotlinVersion).isNotNull();

    // Assert that the default Kotlin version for new projects is at least as recent as the resolved
    // Kotlin version
    assertThat(KotlinGradlePluginVersionKt.compareTo(resolvedKotlinVersion, DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS)).isNotEqualTo(1);
  }
}
