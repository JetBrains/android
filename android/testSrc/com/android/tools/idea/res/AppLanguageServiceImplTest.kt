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
package com.android.tools.idea.res

import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.project.modules
import com.intellij.testFramework.EdtRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class AppLanguageServiceImplTest {
  private val projectRule = AndroidProjectRule.withAndroidModels(
      prepareProjectSources = { dir ->
        File(dir, "one/res").mkdirs()
        File(dir, "two/res").mkdirs()
      },
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":one", "debug", createApp("com.example.one")),
      AndroidModuleModelBuilder(":two", "debug", createApp("com.example.two")),
    ).onEdt()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun before() {
    projectRule.fixture.addFileToProject("one/res/values/strings.xml", createStringsFile("Hello"))
    projectRule.fixture.addFileToProject("one/res/values-da/strings.xml", createStringsFile("Hallo"))
    projectRule.fixture.addFileToProject("two/res/values/strings.xml", createStringsFile("Hello"))
    projectRule.fixture.addFileToProject("two/res/values-ru/strings.xml", createStringsFile("Привет"))
  }

  private fun createStringsFile(helloTranslation: String): String {
    return """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="hello">$helloTranslation</string>
      </resources>
    """.trimIndent()
  }

  @Test
  fun testLanguageServices() {
    val services = AppLanguageService.getInstance(projectRule.project)
    assertThat(services.getAppLanguageInfo()).containsExactly(
      AppLanguageInfo("com.example.one", setOf(LocaleQualifier("da"))),
      AppLanguageInfo("com.example.two", setOf(LocaleQualifier("ru"))),
    )
  }

  private fun createApp(applicationId: String) =
    AndroidProjectBuilder(
      projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
      mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
      applicationIdFor = { applicationId },
    )

  private fun addConfig(name: String, moduleSuffix: String): RunnerAndConfigurationSettings {
    val project = projectRule.project
    val manager = RunManager.getInstance(project)
    val settings = manager.createConfiguration(name, AndroidRunConfigurationType::class.java)
    val config = settings.configuration as ModuleBasedConfiguration<*, *>
    val module = project.modules.find { it.isMainModule() && it.name.endsWith(moduleSuffix) }!!
    config.setModule(module)
    manager.addConfiguration(settings)
    return settings
  }
}
