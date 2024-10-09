/*
 * Copyright (C) 2024 The Android Open Source Project
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

abstract class MigrateResValuesFromGradlePropertiesTest(
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  val propertyValue: Boolean?
) {

  private val projectRule = AndroidGradleProjectRule()

  @JvmField
  @Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Test
  @RunsInEdt
  fun testMigration() {
    projectRule.loadProject(TestProjectPaths.MIGRATE_RES_VALUES, agpVersion = agpVersion)
    val project = projectRule.project
    MigrateResValuesFromGradlePropertiesHandler().invoke(project, null, null, null)

    val gradlePropertiesContent = VfsUtil.loadText(project.baseDir.findChild("gradle.properties")!!)
    when (propertyValue) {
      null -> Truth.assertThat(gradlePropertiesContent).doesNotContain("android.defaults.buildfeatures.resvalues")
      else -> Truth.assertThat(gradlePropertiesContent).contains("android.defaults.buildfeatures.resvalues=$propertyValue")
    }

    val appBuildGradleContent = VfsUtil.loadText(project.baseDir.findChild("app")!!.findChild("build.gradle")!!)
    Truth.assertThat(appBuildGradleContent).contains("buildFeatures")
    Truth.assertThat(appBuildGradleContent).contains("resValues true")
    Truth.assertThat(appBuildGradleContent).contains("resValue(")

    val libBuildGradleContent = VfsUtil.loadText(project.baseDir.findChild("lib")!!.findChild("build.gradle")!!)
    Truth.assertThat(libBuildGradleContent).contains("buildFeatures")
    Truth.assertThat(libBuildGradleContent).contains("resValues true")
    Truth.assertThat(libBuildGradleContent).contains("resValue(")
  }

  @Test
  fun testActionIsEnabled() {
    projectRule.loadProject(TestProjectPaths.MIGRATE_RES_VALUES, agpVersion = agpVersion)
    val project = projectRule.project
    ApplicationManager.getApplication().invokeAndWait {
      projectRule.fixture.openFileInEditor(project.baseDir.findChild("gradle.properties")!!)
    }
    val action = MigrateResValuesFromGradlePropertiesAction()
    val event = createTestEvent(action, DataManager.getInstance().getDataContext(projectRule.fixture.editor.component))

    ApplicationManager.getApplication().runReadAction {
      action.update(event)
    }

    Truth.assertThat(event.presentation.isEnabled).isTrue()
    Truth.assertThat(event.presentation.isVisible).isTrue()
  }
}
