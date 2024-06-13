/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent.createTestEvent
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

abstract class MigrateBuildConfigFromGradlePropertiesTest(
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  val propertyValue: Boolean?
) {

  private val projectRule = AndroidGradleProjectRule()

  @JvmField
  @Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  // TODO(b/289219116): Although this looks like a sensible test, and it is, it's not testing one aspect of the refactoring:
  //  that it works when starting from a project without generated sources.  Empirically it does work in production; I (xof) haven't
  //  managed to find the right combination of things to replace and events to trigger to make the indexing happen at the right time
  //  in tests.
  @Test
  @Ignore("b/289219116")
  @RunsInEdt
  fun testMigration() {
    projectRule.loadProject(TestProjectPaths.MIGRATE_BUILD_CONFIG, agpVersion = agpVersion)
    val project = projectRule.project
    MigrateBuildConfigFromGradlePropertiesHandler().invoke(project, null, null, null)

    val gradlePropertiesContent = VfsUtil.loadText(project.baseDir.findChild("gradle.properties")!!)
    when (propertyValue) {
      null -> Truth.assertThat(gradlePropertiesContent).doesNotContain("android.defaults.buildfeatures.buildconfig")
      else -> Truth.assertThat(gradlePropertiesContent).contains("android.defaults.buildfeatures.buildconfig=$propertyValue")
    }

    val appBuildGradleContent = VfsUtil.loadText(project.baseDir.findChild("app")!!.findChild("build.gradle")!!)
    Truth.assertThat(appBuildGradleContent).contains("buildFeatures")
    Truth.assertThat(appBuildGradleContent).contains("buildConfig true")
    Truth.assertThat(appBuildGradleContent).contains("buildConfigField")

    val libBuildGradleContent = VfsUtil.loadText(project.baseDir.findChild("lib")!!.findChild("build.gradle")!!)
    Truth.assertThat(libBuildGradleContent).doesNotContain("buildFeatures")
    Truth.assertThat(libBuildGradleContent).doesNotContain("buildConfig true")
    Truth.assertThat(libBuildGradleContent).doesNotContain("buildConfigField")

  }

  @Test
  @RunsInEdt
  fun testMigrationWithGeneratedSources() {
    // This test has the generated sources already present; it is a valuable test to keep around even if b/289219116 is solved.
    projectRule.loadProject(TestProjectPaths.MIGRATE_BUILD_CONFIG_WITH_GENERATED_SOURCES, agpVersion = agpVersion)
    val project = projectRule.project
    MigrateBuildConfigFromGradlePropertiesHandler().invoke(project, null, null, null)

    val gradlePropertiesContent = VfsUtil.loadText(project.baseDir.findChild("gradle.properties")!!)
    when (propertyValue) {
      null -> Truth.assertThat(gradlePropertiesContent).doesNotContain("android.defaults.buildfeatures.buildconfig")
      else -> Truth.assertThat(gradlePropertiesContent).contains("android.defaults.buildfeatures.buildconfig=$propertyValue")
    }

    val appBuildGradleContent = VfsUtil.loadText(project.baseDir.findChild("app")!!.findChild("build.gradle")!!)
    Truth.assertThat(appBuildGradleContent).contains("buildFeatures")
    Truth.assertThat(appBuildGradleContent).contains("buildConfig true")
    Truth.assertThat(appBuildGradleContent).contains("buildConfigField")

    val libBuildGradleContent = VfsUtil.loadText(project.baseDir.findChild("lib")!!.findChild("build.gradle")!!)
    Truth.assertThat(libBuildGradleContent).doesNotContain("buildFeatures")
    Truth.assertThat(libBuildGradleContent).doesNotContain("buildConfig true")
    Truth.assertThat(libBuildGradleContent).doesNotContain("buildConfigField")
  }

  @Test
  fun testActionIsEnabled() {
    projectRule.loadProject(TestProjectPaths.MIGRATE_BUILD_CONFIG, agpVersion = agpVersion)
    val project = projectRule.project
    ApplicationManager.getApplication().invokeAndWait {
      projectRule.fixture.openFileInEditor(project.baseDir.findChild("gradle.properties")!!)
    }
    val action = MigrateBuildConfigFromGradlePropertiesAction()
    val event = createTestEvent(action, DataManager.getInstance().getDataContext(projectRule.fixture.editor.component))

    ApplicationManager.getApplication().runReadAction {
      action.update(event)
    }

    Truth.assertThat(event.presentation.isEnabled).isTrue()
    Truth.assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun testActionIsEnabledWithGeneratedSources() {
    projectRule.loadProject(TestProjectPaths.MIGRATE_BUILD_CONFIG_WITH_GENERATED_SOURCES, agpVersion = agpVersion)
    val project = projectRule.project
    ApplicationManager.getApplication().invokeAndWait {
      projectRule.fixture.openFileInEditor(project.baseDir.findChild("gradle.properties")!!)
    }
    val action = MigrateBuildConfigFromGradlePropertiesAction()
    val event = createTestEvent(action, DataManager.getInstance().getDataContext(projectRule.fixture.editor.component))

    ApplicationManager.getApplication().runReadAction {
      action.update(event)
    }

    Truth.assertThat(event.presentation.isEnabled).isTrue()
    Truth.assertThat(event.presentation.isVisible).isTrue()
  }
}
