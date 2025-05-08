/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JourneyActionArtifactsTest {

  @Test
  fun parseFromAdditionalTestArtifacts_returnsEmptyListWhenNoJourneyArtifactsPresent() {
    val additionalTestArtifacts = mapOf<String, String>()

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assert(artifacts.isEmpty())
  }

  @Test
  fun parseFromAdditionalTestArtifacts_artifactsParsed() {
    val additionalTestArtifacts = mapOf(
      "Journeys.ActionPerformed.action1.screenshotPath" to "path/to/screenshot/1",
      "Journeys.ActionPerformed.action1.description" to "Description 1",
      "Journeys.ActionPerformed.action1.modelReasoning" to "Reasoning 1",
      "Journeys.ActionPerformed.action2.screenshotPath" to "path/to/screenshot/2",
      "Journeys.ActionPerformed.action2.description" to "Description 2",
      "Journeys.ActionPerformed.action2.modelReasoning" to "Reasoning 2",
      "Journeys.PromptComplete.prompt1.screenshotPath" to "path/to/screenshot/3",
      "Journeys.PromptComplete.prompt1.description" to "Description 3",
      "Journeys.PromptComplete.prompt1.modelReasoning" to "Reasoning 3",
    )

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assertEquals(3, artifacts.size)
    assertEquals("path/to/screenshot/1", artifacts[0].screenshotImage)
    assertEquals("Description 1", artifacts[0].description)
    assertEquals("Reasoning 1", artifacts[0].reasoning)
    assertEquals("path/to/screenshot/2", artifacts[1].screenshotImage)
    assertEquals("Description 2", artifacts[1].description)
    assertEquals("Reasoning 2", artifacts[1].reasoning)
    assertEquals("path/to/screenshot/3", artifacts[2].screenshotImage)
    assertEquals("Description 3", artifacts[2].description)
    assertEquals("Reasoning 3", artifacts[2].reasoning)
  }

  @Test
  fun parseFromAdditionalTestArtifacts_artifactsOrderedByScopeThenIndex() {
    val additionalTestArtifacts = mapOf(
      "Journeys.PromptComplete.prompt1.screenshotPath" to "path/to/screenshot/3",
      "Journeys.PromptComplete.prompt1.description" to "Description 3",
      "Journeys.PromptComplete.prompt1.modelReasoning" to "Reasoning 3",
      "Journeys.ActionPerformed.action2.screenshotPath" to "path/to/screenshot/2",
      "Journeys.ActionPerformed.action2.description" to "Description 2",
      "Journeys.ActionPerformed.action2.modelReasoning" to "Reasoning 2",
      "Journeys.ActionPerformed.action1.screenshotPath" to "path/to/screenshot/1",
      "Journeys.ActionPerformed.action1.description" to "Description 1",
      "Journeys.ActionPerformed.action1.modelReasoning" to "Reasoning 1",
    )

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assertEquals(3, artifacts.size)
    assertEquals("path/to/screenshot/1", artifacts[0].screenshotImage)
    assertEquals("Description 1", artifacts[0].description)
    assertEquals("Reasoning 1", artifacts[0].reasoning)
    assertEquals("path/to/screenshot/2", artifacts[1].screenshotImage)
    assertEquals("Description 2", artifacts[1].description)
    assertEquals("Reasoning 2", artifacts[1].reasoning)
    assertEquals("path/to/screenshot/3", artifacts[2].screenshotImage)
    assertEquals("Description 3", artifacts[2].description)
    assertEquals("Reasoning 3", artifacts[2].reasoning)
  }

  @Test
  fun parseFromAdditionalTestArtifacts_missingFieldsAllowed() {
    val additionalTestArtifacts = mapOf(
      "Journeys.ActionPerformed.action1.description" to "Description 1",
      "Journeys.ActionPerformed.action2.screenshotPath" to "path/to/screenshot/2",
      "Journeys.PromptComplete.prompt1.modelReasoning" to "Model reasoning 3",
    )

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assertEquals(3, artifacts.size)
    assertEquals("Description 1", artifacts[0].description)
    assertNull(artifacts[0].reasoning)
    assertNull(artifacts[0].screenshotImage)
    assertEquals("path/to/screenshot/2", artifacts[1].screenshotImage)
    assertNull(artifacts[1].description)
    assertNull(artifacts[1].reasoning)
    assertEquals("Model reasoning 3", artifacts[2].reasoning)
    assertNull(artifacts[2].screenshotImage)
    assertNull(artifacts[2].description)
  }
}
