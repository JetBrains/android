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

import com.android.ddmlib.testing.FakeAdbRule
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.PseudoLocalesToken
import com.android.tools.idea.projectsystem.gradle.GradlePseudoLocalesToken
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

  private val adbRule = FakeAdbRule()

  private val pseudoLocalesToken = object : PseudoLocalesToken {
    override fun isPseudoLocalesEnabled(applicationProjectContext: ApplicationProjectContext): PseudoLocalesToken.PseudoLocalesState =
      when (applicationProjectContext.applicationId) {
        "com.example.one" -> PseudoLocalesToken.PseudoLocalesState.DISABLED
        "com.example.two" -> PseudoLocalesToken.PseudoLocalesState.ENABLED
        else -> PseudoLocalesToken.PseudoLocalesState.UNKNOWN
      }

    override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean = true
  }

  @get:Rule
  val ruleChain = RuleChain(projectRule, adbRule, EdtRule())

  private val serialNumber = "42"

  @Before
  fun before() {
    projectRule.fixture.addFileToProject("one/res/values/strings.xml", createStringsFile("Hello"))
    projectRule.fixture.addFileToProject("one/res/values-da/strings.xml", createStringsFile("Hallo"))
    projectRule.fixture.addFileToProject("two/res/values/strings.xml", createStringsFile("Hello"))
    projectRule.fixture.addFileToProject("two/res/values-ru/strings.xml", createStringsFile("Привет"))
    val extensionPoint = projectRule.project.extensionArea.getExtensionPoint(PseudoLocalesToken.EP_NAME)
    extensionPoint.unregisterExtension(GradlePseudoLocalesToken::class.java)
    extensionPoint.registerExtension(pseudoLocalesToken, projectRule.testRootDisposable)
    val state = adbRule.attachDevice(serialNumber, "Google", "Pixel6", "versionX", "33")
    state.startClient(12, 24, "com.example.one", isWaiting = true)
    state.startClient(14, 28, "com.example.two", isWaiting = true)
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
    assertThat(services.getAppLanguageInfo(serialNumber, "com.example.one")).isEqualTo(
      AppLanguageInfo("com.example.one", setOf(LocaleQualifier("da")))
    )
    assertThat(services.getAppLanguageInfo(serialNumber, "com.example.two")).isEqualTo(
      AppLanguageInfo("com.example.two", setOf(
        LocaleQualifier("ru"),
        LocaleQualifier(null, "en", "XA", null),
        LocaleQualifier(null, "ar", "XB", null))
      ),
    )
  }

  private fun createApp(applicationId: String) =
    AndroidProjectBuilder(
      projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
      mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
      applicationIdFor = { applicationId },
    )
}
