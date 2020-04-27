/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.pom.java.LanguageLevel

class EnableDexWithApiLevelQuickFixTest: AndroidGradleTestCase() {
  fun testAllNullApiLevel() {
    loadProject(PROJECT_WITH_APPAND_LIB)
    val quickfix = EnableDexWithApiLevelQuickFixAll(null)

    // Quickfix properties
    assertThat(quickfix.apiLevel).isNull()
    assertThat(quickfix.description).isEqualTo("Enable desugaring in all modules.")
    assertThat(quickfix.id).isEqualTo("enable.desugaring.all")

    verifyRun(null, quickfix, listOf(project.findAppModule(), project.findModule("lib")))
  }

  fun testAllApiLevel() {
    loadProject(PROJECT_WITH_APPAND_LIB)
    val apiLevel = "24"
    val quickfix = EnableDexWithApiLevelQuickFixAll(apiLevel)

    // Quickfix properties
    assertThat(quickfix.apiLevel).isEqualTo(apiLevel)
    assertThat(quickfix.description).isEqualTo("Enable desugaring and set minSdkVersion to $apiLevel in all modules.")
    assertThat(quickfix.id).isEqualTo("enable.desugaring.all.$apiLevel")

    verifyRun(apiLevel, quickfix, listOf(project.findAppModule(), project.findModule("lib")))
  }

  fun testModuleNullApiLevel() {
    loadProject(PROJECT_WITH_APPAND_LIB)
    val modulePath = ":app"
    val quickfix = EnableDexWithApiLevelQuickFixModule(modulePath, null)

    // Quickfix properties
    assertThat(quickfix.apiLevel).isNull()
    assertThat(quickfix.description).isEqualTo("Enable desugaring in module $modulePath.")
    assertThat(quickfix.id).isEqualTo("enable.desugaring.module")

    verifyRun(null, quickfix, listOf(project.findAppModule()))
  }

  fun testModuleApiLevel() {
    loadProject(PROJECT_WITH_APPAND_LIB)
    val apiLevel = "24"
    val modulePath = ":app"
    val quickfix = EnableDexWithApiLevelQuickFixModule(modulePath, apiLevel)

    // Quickfix properties
    assertThat(quickfix.apiLevel).isEqualTo(apiLevel)
    assertThat(quickfix.description).isEqualTo("Enable desugaring and set minSdkVersion to $apiLevel in module $modulePath.")
    assertThat(quickfix.id).isEqualTo("enable.desugaring.module.$apiLevel")

    verifyRun(apiLevel, quickfix, listOf(project.findAppModule()))
  }

  private fun verifyRun(apiLevel: String?, quickfix: AbstractEnableDexWithApiLevelQuickFix, modules: List<Module>) {
    // Expected build files
    verifyBuildFiles(quickfix, modules)

    // Prepare settings before applying quickfix
    prepareModuleSettings(modules)

    // Verify expected changes after applying quickfix
    quickfix.enableDexInBuildFiles(project)
    verifyModuleSettings(apiLevel, modules)
  }

  private fun prepareModuleSettings(modules: List<Module>) {
    val projectBuildModel = ProjectBuildModel.get(project)
    modules.forEach {
      val moduleModel = projectBuildModel.getModuleBuildModel(it)
      assertThat(moduleModel).isNotNull()
      val android = moduleModel!!.android()
      android.defaultConfig().minSdkVersion().setValue("16")
      val compileOptions = android.compileOptions()
      compileOptions.sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7)
      compileOptions.targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7)
    }

    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        projectBuildModel.applyChanges()
      }
    }
  }

  private fun verifyBuildFiles(quickfix: AbstractEnableDexWithApiLevelQuickFix, modules: List<Module>) {
    val expectedBuildFiles = modules.map { GradleUtil.getGradleBuildFile(it) }
    val buildFiles = quickfix.buildFilesToApply(project)
    assertThat(buildFiles).containsExactlyElementsIn(expectedBuildFiles)
  }

  private fun verifyModuleSettings(apiLevel: String?, modules: List<Module>) {
    val projectBuildModel = ProjectBuildModel.get(project)
    modules.forEach {
      val moduleModel = projectBuildModel.getModuleBuildModel(it)
      assertThat(moduleModel).isNotNull()
      val android = moduleModel!!.android()
      if (apiLevel != null) {
        assertThat(android.defaultConfig().minSdkVersion().getValue(INTEGER_TYPE)).isEqualTo(Integer(apiLevel))
      }
      val compileOptions = android.compileOptions()
      assertThat(compileOptions.sourceCompatibility().toLanguageLevel()).isEqualTo(LanguageLevel.JDK_1_8)
      assertThat(compileOptions.targetCompatibility().toLanguageLevel()).isEqualTo(LanguageLevel.JDK_1_8)
    }
  }
}