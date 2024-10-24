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
package com.android.tools.idea.ml.xmltocompose

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.gemini.buildLlmPrompt
import com.android.tools.idea.gemini.formatForTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerExtension
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// language=kotlin
private val simpleKotlinCode =
  """
      import androidx.compose.runtime.Composable
      @Composable
      fun Greeting() {
        val a = 35
      }
      """
    .trimIndent()

class NShotXmlToComposeConverterTest {
  private class FakeGeminiPluginApi : GeminiPluginApi {
    override val MAX_QUERY_CHARS: Int = Int.MAX_VALUE
    var contextAllowed = true

    override fun isAvailable() = true

    override fun isContextAllowed(project: Project) = contextAllowed

    override fun isFileExcluded(project: Project, file: VirtualFile) = false

    override fun sendChatQuery(
      project: Project,
      prompt: LlmPrompt,
      displayText: String?,
      requestSource: GeminiPluginApi.RequestSource,
    ) {}

    override fun stageChatQuery(
      project: Project,
      prompt: String,
      requestSource: GeminiPluginApi.RequestSource,
    ) {}

    override fun generate(project: Project, prompt: LlmPrompt): Flow<String> {
      return flowOf(
        """
        |Here is your code
        |```kotlin
        |$simpleKotlinCode
        |```
      """
          .trimMargin()
      )
    }
  }

  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val rule = RuleChain(projectRule)

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setUp() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.contextAllowed = true

    ApplicationManager.getApplication()
      .registerExtension(
        GeminiPluginApi.EP_NAME,
        fakeGeminiPluginApi,
        projectRule.testRootDisposable,
      )
  }

  @Test
  fun testNShotConversion() {
    val nShotXmlToComposeConverter = NShotXmlToComposeConverter.Builder(projectRule.project).build()

    val expectedPrompt =
      buildLlmPrompt(projectRule.project) {
        userMessage {
          text(
            "What's the Jetpack Compose equivalent of the following Android XML layout? Include imports in your answer. Add a @Preview function. Don't use ConstraintLayout. Use material3, not material.",
            emptyList(),
          )
          code(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical">
             </LinearLayout>
          """
              .trimIndent(),
            XMLLanguage.INSTANCE,
            emptyList(),
          )
        }
      }
    assertEquals(
      expectedPrompt.formatForTests(),
      nShotXmlToComposeConverter.getPrompt(simpleXmlLayout()).formatForTests(),
    )
  }

  @Test
  fun testViewModel() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project).useViewModel(true).build()
    val query = nShotXmlToComposeConverter.getPrompt(simpleXmlLayout())
    assertTrue(
      query
        .formatForTests()
        .contains("Create a subclass of androidx.lifecycle.ViewModel to store the states.")
    )
  }

  @Test
  fun testDataTypes() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project)
        .useViewModel(true)
        .withDataType(ComposeConverterDataType.LIVE_DATA)
        .build()
    val query = nShotXmlToComposeConverter.getPrompt(simpleXmlLayout()).formatForTests()
    assertTrue(
      query.contains(
        "The ViewModel must store data using objects of type androidx.lifecycle.LiveData. " +
          "The Composable methods will use states derived from the data stored in the ViewModel."
      )
    )
    assertTrue(query.contains("Do not use androidx.compose.runtime.MutableState in the ViewModel."))
    assertTrue(query.contains("Do not use kotlinx.coroutines.flow.StateFlow in the ViewModel."))
  }

  @Test
  fun testDataTypesWithoutViewModel() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project)
        .useViewModel(false)
        .withDataType(ComposeConverterDataType.LIVE_DATA)
        .build()
    val query = nShotXmlToComposeConverter.getPrompt(simpleXmlLayout()).formatForTests()
    // If not querying about a ViewModel, it's pointless to include data types in the query.
    assertFalse(query.contains("The ViewModel must store data using objects of type"))
  }

  @Test
  fun testUnknownDataType() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project)
        .useViewModel(true)
        .withDataType(ComposeConverterDataType.UNKNOWN)
        .build()
    val query = nShotXmlToComposeConverter.getPrompt(simpleXmlLayout()).formatForTests()
    // If data type is specified as unknown, we shouldn't specify it in the query.
    assertFalse(query.contains("The ViewModel must store data using objects of type"))
  }

  @Test
  fun testDataTypesWithCustomViews() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project).useCustomView(true).build()
    val query = nShotXmlToComposeConverter.getPrompt(simpleXmlLayout()).formatForTests()
    assertTrue(query.contains("Wrap any Custom Views in an AndroidView composable."))
  }

  @Test
  fun testDataTypesWithoutCustomViews() {
    val nShotXmlToComposeConverter = NShotXmlToComposeConverter.Builder(projectRule.project).build()
    val query = nShotXmlToComposeConverter.getPrompt(simpleXmlLayout()).formatForTests()
    // If not querying about a Custom View, we shouldn't include an additional prompt about it in
    // the query.
    assertFalse(query.contains("Wrap any Custom Views in an AndroidView composable"))
  }

  @Test
  fun testStudioBotResponse() {
    val nShotXmlToComposeConverter = NShotXmlToComposeConverter.Builder(projectRule.project).build()
    val response = runBlocking { nShotXmlToComposeConverter.convertToCompose(simpleXmlLayout()) }
    assertEquals(ConversionResponse.Status.SUCCESS, response.status)
    assertEquals(simpleKotlinCode, response.generatedCode)
  }

  @Test
  fun testResponseIfContextIsNotEnabled() {
    fakeGeminiPluginApi.contextAllowed = false
    val nShotXmlToComposeConverter = NShotXmlToComposeConverter.Builder(projectRule.project).build()
    val response = runBlocking { nShotXmlToComposeConverter.convertToCompose(simpleXmlLayout()) }
    assertEquals(ConversionResponse.Status.ERROR, response.status)
    assertEquals(
      "Please follow the Gemini onboarding and enable context sharing if you want to use " +
        "this feature.",
      response.generatedCode,
    )
  }

  // language=xml
  private fun simpleXmlLayout() =
    """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">
       </LinearLayout>
      """
      .trimIndent()
}
