/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.quickFixes.AbstractSetLanguageLevel8QuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetLanguageLevel8AllQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetLanguageLevel8ModuleQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.pom.java.LanguageLevel.JDK_1_7
import com.intellij.pom.java.LanguageLevel.JDK_1_8
import com.intellij.pom.java.LanguageLevel.JDK_1_9
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SetLanguageLevel8QuickFixTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testAllJvmTargetTrue() {
    val setJvmTarget = true
    val quickfix = SetLanguageLevel8AllQuickFix(setJvmTarget)
    // Quickfix properties
    assertThat(quickfix.description).isEqualTo("Change Java language level and jvmTarget to 8 in all modules if using a lower level.")
    assertThat(quickfix.id).isEqualTo("set.java.level.8.all")

    // run method
    verifyQuickFix(
      quickfix,
      originalLevel = JDK_1_7,
      expectedLevel = JDK_1_8,
      setJvmTarget = setJvmTarget,
      modulesNames = listOf("app", "lib")
    )
  }

  @Test
  fun testAllJvmTargetFalse() {
    val setJvmTarget = false
    val quickfix = SetLanguageLevel8AllQuickFix(setJvmTarget)
    // Quickfix properties
    assertThat(quickfix.description).isEqualTo("Change Java language level to 8 in all modules if using a lower level.")
    assertThat(quickfix.id).isEqualTo("set.java.level.8.all")

    // run method
    verifyQuickFix(quickfix, originalLevel = JDK_1_7, expectedLevel = JDK_1_8, setJvmTarget = setJvmTarget, modulesNames = listOf("app", "lib"))
  }

  @Test
  fun testModuleOn7JvmTargetTrue() {
    val setJvmTarget = true
    val modulePath = ":app"
    val quickfix = SetLanguageLevel8ModuleQuickFix(modulePath, setJvmTarget)
    // Quickfix properties
    assertThat(quickfix.description).isEqualTo("Change Java language level and jvmTarget to 8 in module :app if using a lower level.")
    assertThat(quickfix.id).isEqualTo("set.java.level.8.module")

    // run method
    verifyQuickFix(quickfix, originalLevel = JDK_1_7, expectedLevel = JDK_1_8, setJvmTarget = setJvmTarget, modulesNames = listOf("app"))
  }

  @Test
  fun testModuleOn7JvmTargetFalse() {
    val setJvmTarget = true
    val modulePath = ":app"
    val quickfix = SetLanguageLevel8ModuleQuickFix(modulePath, setJvmTarget)
    // Quickfix properties
    assertThat(quickfix.description).isEqualTo("Change Java language level and jvmTarget to 8 in module :app if using a lower level.")
    assertThat(quickfix.id).isEqualTo("set.java.level.8.module")

    // run method
    verifyQuickFix(quickfix, originalLevel = JDK_1_7, expectedLevel = JDK_1_8, setJvmTarget = setJvmTarget, modulesNames = listOf("app"))
  }

  @Test
  fun testModuleOn8JvmTargetTrue() {
    val setJvmTarget = true
    val modulePath = ":app"
    val quickfix = SetLanguageLevel8ModuleQuickFix(modulePath, setJvmTarget)
    // Quickfix properties
    assertThat(quickfix.description).isEqualTo("Change Java language level and jvmTarget to 8 in module :app if using a lower level.")
    assertThat(quickfix.id).isEqualTo("set.java.level.8.module")

    // run method
    verifyQuickFix(quickfix, originalLevel = JDK_1_8, expectedLevel = JDK_1_8, setJvmTarget = setJvmTarget, modulesNames = listOf("app"))
  }


  @Test
  fun testModuleOn9JvmTargetTrue() {
    val setJvmTarget = true
    val modulePath = ":app"
    val quickfix = SetLanguageLevel8ModuleQuickFix(modulePath, setJvmTarget)
    // Quickfix properties
    assertThat(quickfix.description).isEqualTo("Change Java language level and jvmTarget to 8 in module :app if using a lower level.")
    assertThat(quickfix.id).isEqualTo("set.java.level.8.module")

    // run method
    verifyQuickFix(quickfix, originalLevel = JDK_1_9, expectedLevel = JDK_1_9, setJvmTarget = setJvmTarget, modulesNames = listOf("app"))
  }

  private fun verifyQuickFix(
    quickfix: AbstractSetLanguageLevel8QuickFix,
    originalLevel: LanguageLevel,
    expectedLevel: LanguageLevel,
    setJvmTarget: Boolean,
    modulesNames: List<String>
  ) {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    preparedProject.open { project ->
      val modules = modulesNames.map { project.findModule(it) }
      // Expected build files
      verifyBuildFiles(project, quickfix, modules)

      // Prepare settings before applying quickfix
      prepareModuleSettings(project, modules, originalLevel, setJvmTarget)

      // Verify expected changes after applying quickfix
      quickfix.setJavaLevel8InBuildFiles(project, setJvmTarget)
      verifyModuleSettings(project, modules, expectedLevel, setJvmTarget)
    }
  }

  private fun prepareModuleSettings(project: Project, modules: List<Module>, level: LanguageLevel, setJvmTarget: Boolean) {
    val projectBuildModel = ProjectBuildModel.get(project)
    modules.forEach {
      val moduleModel = projectBuildModel.getModuleBuildModel(it)
      assertThat(moduleModel).isNotNull()
      val android = moduleModel!!.android()
      val compileOptions = android.compileOptions()
      compileOptions.sourceCompatibility().setLanguageLevel(level)
      compileOptions.targetCompatibility().setLanguageLevel(level)
      if (level.isLessThan(JDK_1_8) || (!setJvmTarget)) {
        android.kotlinOptions().jvmTarget().delete()
      } else {
        android.kotlinOptions().jvmTarget().setLanguageLevel(level)
      }
    }

    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        projectBuildModel.applyChanges()
      }
    }
  }

  private fun verifyBuildFiles(project: Project, quickfix: AbstractSetLanguageLevel8QuickFix, modules: List<Module>) {
    val expectedBuildFiles = modules.map { GradleUtil.getGradleBuildFile(it) }
    val buildFiles = quickfix.buildFilesToApply(project)
    assertThat(buildFiles).containsExactlyElementsIn(expectedBuildFiles)
  }

  private fun verifyModuleSettings(project: Project, modules: List<Module>, expectedLevel: LanguageLevel, setJvmTarget: Boolean) {
    val projectBuildModel = ProjectBuildModel.get(project)
    modules.forEach {
      val moduleModel = projectBuildModel.getModuleBuildModel(it)
      assertThat(moduleModel).isNotNull()
      val android = moduleModel!!.android()
      val compileOptions = android.compileOptions()
      assertThat(compileOptions.sourceCompatibility().toLanguageLevel()).isEqualTo(expectedLevel)
      assertThat(compileOptions.targetCompatibility().toLanguageLevel()).isEqualTo(expectedLevel)
      if (setJvmTarget) {
        assertThat(android.kotlinOptions().jvmTarget().toLanguageLevel()).isEqualTo(expectedLevel)
      } else {
        assertThat(android.kotlinOptions().jvmTarget().toLanguageLevel()).isNull()
      }
    }
  }
}