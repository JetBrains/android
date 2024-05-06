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
package com.android.tools.idea.layoutinspector.resource

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.packageNameHash
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.modules
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Rectangle
import java.io.File

class ModuleComposeResolverTest {

  private val projectRule =
    AndroidProjectRule.withAndroidModels(
        prepareProjectSources = { dir ->
          File(dir, "app/src").mkdirs()
          File(dir, "two/src").mkdirs()
        },
        JavaModuleModelBuilder.rootModuleBuilder,
        AndroidModuleModelBuilder(":app", "debug", createApp()),
        AndroidModuleModelBuilder(":two", "debug", createApp()),
      )
      .onEdt()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule.projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule).around(EdtRule())!!

  private lateinit var appConfig: RunnerAndConfigurationSettings
  private lateinit var twoConfig: RunnerAndConfigurationSettings

  @Before
  fun before() {
    projectRule.fixture.addFileToProject(
      "app/src/java/com/example/MainActivity.kt",
      createMainActivityFile("App")
    )
    projectRule.fixture.addFileToProject(
      "two/src/java/com/example/MainActivity.kt",
      createMainActivityFile("Two")
    )
    appConfig = addConfig("app-config", ".app.main")
    twoConfig = addConfig("two-config", ".two.main")
  }

  @RunsInEdt
  @Test
  fun testLookupWithAppRunConfiguration() {
    val project = projectRule.project
    val manager = RunManager.getInstance(project)
    manager.selectedConfiguration = appConfig
    val resolver = ComposeResolver(project)
    val navigatable = resolver.findComposableNavigatable(fakeComposeNode)
    navigatable!!.navigate(true)
    fileOpenCaptureRule.checkEditor("MainActivity.kt", 13, "Text(\"App\")")
  }

  @RunsInEdt
  @Test
  fun testLookupWithTwoRunConfiguration() {
    val project = projectRule.project
    val manager = RunManager.getInstance(project)
    manager.selectedConfiguration = twoConfig
    val resolver = ComposeResolver(project)
    val navigatable = resolver.findComposableNavigatable(fakeComposeNode)
    navigatable!!.navigate(true)
    fileOpenCaptureRule.checkEditor("MainActivity.kt", 13, "Text(\"Two\")")
  }

  private fun createApp() =
    AndroidProjectBuilder(
      projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
      mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
    )

  private val fakeComposeNode: ComposeViewNode =
    ComposeViewNode(
      -2L,
      "Text",
      null,
      Rectangle(20, 20, 600, 200),
      null,
      "",
      0,
      0,
      0,
      "MainActivity.kt",
      composePackageHash = packageNameHash("com.example"),
      composeOffset = 392,
      composeLineNumber = 12,
      0,
      0
    )

  private fun addConfig(name: String, moduleSuffix: String): RunnerAndConfigurationSettings {
    val project = projectRule.project
    val manager = RunManager.getInstance(project)
    val settings = manager.createConfiguration(name, AndroidRunConfigurationType::class.java)
    val config = settings.configuration as AndroidRunConfiguration
    val module = project.modules.find { it.isMainModule() && it.name.endsWith(moduleSuffix) }!!
    config.setModule(module)
    manager.addConfiguration(settings)
    return settings
  }

  private fun createMainActivityFile(module: String): String =
    """
      package com.example

      import android.os.Bundle
      import androidx.activity.ComponentActivity
      import androidx.activity.compose.setContent
      import androidx.compose.material3.Text
      import androidx.compose.runtime.Composable

      class MainActivity : ComponentActivity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              setContent {
                  Text("$module")
              }
          }
      }
    """
      .trimIndent()
}
