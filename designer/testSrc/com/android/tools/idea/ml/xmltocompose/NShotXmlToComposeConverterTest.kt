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

import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.LlmService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ApplicationServiceRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NShotXmlToComposeConverterTest {

  private val studioBot =
    object : StudioBot.StubStudioBot() {
      override fun model() =
        object : LlmService.StubLlmService() {
          override suspend fun sendQuery(
            request: AiExcludeService.ValidatedQuery,
            source: StudioBot.RequestSource,
          ): Flow<String> {
            return flowOf("CITATIONS: Some citations here", "```kotlin\n${simpleKotlinCode()}\n```")
          }
        }
    }

  private val applicationServiceRule = ApplicationServiceRule(StudioBot::class.java, studioBot)

  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val rule = RuleChain(projectRule, applicationServiceRule)

  @Test
  fun testNShotConversion() {
    val nShotXmlToComposeConverter = NShotXmlToComposeConverter.Builder(projectRule.project).build()
    val expectedQuery =
      """
      What's the Jetpack Compose equivalent of the following Android XML layout? Include imports in your answer. Add a @Preview function. Don't use ConstraintLayout. Use material3, not material.

      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">
       </LinearLayout>
      """
        .trimIndent()
    assertEquals(expectedQuery, nShotXmlToComposeConverter.getQuery(simpleXmlLayout()))
  }

  @Test
  fun testViewModel() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project).useViewModel(true).build()
    val query = nShotXmlToComposeConverter.getQuery(simpleXmlLayout())
    assertTrue(
      query.contains("Create a subclass of androidx.lifecycle.ViewModel to store the states.")
    )
  }

  @Test
  fun testDataTypes() {
    val nShotXmlToComposeConverter =
      NShotXmlToComposeConverter.Builder(projectRule.project)
        .useViewModel(true)
        .withDataType(ComposeConverterDataType.LIVE_DATA)
        .build()
    val query = nShotXmlToComposeConverter.getQuery(simpleXmlLayout())
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
    val query = nShotXmlToComposeConverter.getQuery(simpleXmlLayout())
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
    val query = nShotXmlToComposeConverter.getQuery(simpleXmlLayout())
    // If data type is specified as unknown, we shouldn't specify it in the query.
    assertFalse(query.contains("The ViewModel must store data using objects of type"))
  }

  @Test
  fun testStudioBotResponse() {
    val nShotXmlToComposeConverter = NShotXmlToComposeConverter.Builder(projectRule.project).build()
    val response = runBlocking { nShotXmlToComposeConverter.convertToCompose(simpleXmlLayout()) }
    assertEquals(simpleKotlinCode(), response)
  }

  // language=kotlin
  private fun simpleKotlinCode() =
    """
      import androidx.compose.runtime.Composable
      @Composable
      fun Greeting() {
        val a = 35
      }
      """
      .trimIndent()

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
