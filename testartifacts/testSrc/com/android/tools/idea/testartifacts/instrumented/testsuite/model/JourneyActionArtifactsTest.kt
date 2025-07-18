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

import com.android.tools.journeys.proto.Artifact
import com.android.tools.journeys.proto.ArtifactType
import com.android.tools.journeys.proto.Interaction
import com.android.tools.journeys.proto.Result
import com.android.tools.journeys.proto.Status
import com.android.tools.journeys.proto.Step
import com.android.tools.journeys.proto.Turn
import java.util.Base64
import junit.framework.TestCase.assertTrue
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
    val step = Step.newBuilder()
      .setInitialization(
        Step.Initialization.newBuilder()
          .setPromptText("Prompt")
          .build()
      )
      .addAllTurns(
        listOf(
          buildTurn(
            "Description 1",
            "Reasoning 1",
            listOf(
              buildArtifact(ArtifactType.SCREENSHOT, "path/to/screenshot/1")
            ),
            listOf(
              buildInteraction("Click", Status.SUCCEEDED),
              buildInteraction("Swipe", Status.SUCCEEDED)
            )
          ),
          buildTurn(
            "Description 2",
            "Reasoning 2",
            listOf(
              buildArtifact(ArtifactType.SCREENSHOT, "path/to/screenshot/2")
            ),
            listOf(
              buildInteraction("Click", Status.SUCCEEDED)
            )
          ),
          buildTurn(
            "Description 3",
            "Reasoning 3",
            listOf(
              buildArtifact(ArtifactType.SCREENSHOT, "path/to/screenshot/3")
            ),
            listOf(
              buildInteraction("Click", Status.FAILED)
            )
          )
        )
      )
      .build()

    val additionalTestArtifacts = mapOf(
      "Journeys.Step" to encodeStep(step),
    )

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assertEquals(3, artifacts.size)
    assertEquals("path/to/screenshot/1", artifacts[0].screenshotImage)
    assertEquals("Description 1", artifacts[0].description)
    assertEquals("Reasoning 1", artifacts[0].reasoning)
    assertEquals(listOf("Click (SUCCEEDED)", "Swipe (SUCCEEDED)"), artifacts[0].interactions)

    assertEquals("path/to/screenshot/2", artifacts[1].screenshotImage)
    assertEquals("Description 2", artifacts[1].description)
    assertEquals("Reasoning 2", artifacts[1].reasoning)
    assertEquals(listOf("Click (SUCCEEDED)"), artifacts[1].interactions)

    assertEquals("path/to/screenshot/3", artifacts[2].screenshotImage)
    assertEquals("Description 3", artifacts[2].description)
    assertEquals("Reasoning 3", artifacts[2].reasoning)
    assertEquals(listOf("Click (FAILED)"), artifacts[2].interactions)
  }

  @Test
  fun parseFromAdditionalTestArtifacts_missingFieldsAllowed() {
    val step = Step.newBuilder()
      .setInitialization(
        Step.Initialization.newBuilder()
          .setPromptText("Prompt")
          .build()
      )
      .addAllTurns(
        listOf(
          buildTurn(
            "Description 1",
            null,
            emptyList(),
            emptyList()
          ),
          buildTurn(
            null,
            "Reasoning 2",
            listOf(
              buildArtifact(ArtifactType.SCREENSHOT, "path/to/screenshot/2")
            ),
            emptyList()
          )
        )
      )
      .build()

    val additionalTestArtifacts = mapOf(
      "Journeys.Step" to encodeStep(step),
    )

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assertEquals(2, artifacts.size)

    assertEquals("Description 1", artifacts[0].description)
    assertNull(artifacts[0].reasoning)
    assertNull(artifacts[0].screenshotImage)
    assertTrue(artifacts[0].interactions.isEmpty())

    assertNull(artifacts[1].description)
    assertEquals("Reasoning 2", artifacts[1].reasoning)
    assertEquals("path/to/screenshot/2", artifacts[1].screenshotImage)
    assertTrue(artifacts[1].interactions.isEmpty())
  }

  @Test
  fun parseFromAdditionalTestArtifacts_ignoresNonScreenshotArtifacts() {
    val step = Step.newBuilder()
      .setInitialization(
        Step.Initialization.newBuilder()
          .setPromptText("Prompt")
          .build()
      )
      .addAllTurns(
        listOf(
          buildTurn(
            "Description 1",
            "Reasoning 1",
            listOf(
              buildArtifact(ArtifactType.LOGCAT, "path/to/logcat")
            ),
            emptyList()
          )
        )
      )
      .build()

    val additionalTestArtifacts = mapOf(
      "Journeys.Step" to encodeStep(step),
    )

    val artifacts = JourneyActionArtifacts.parseFromAdditionalTestArtifacts(additionalTestArtifacts)

    assertEquals(1, artifacts.size)
    assertNull(artifacts[0].screenshotImage)
  }

  private fun buildTurn(
    description: String?,
    reasoning: String?,
    artifacts: List<Artifact>,
    interactions: List<Interaction>
  ): Turn {
    val turnBuilder = Turn.newBuilder()
    description?.let {
      turnBuilder.setDescription(it)
    }
    reasoning?.let {
      turnBuilder.setReasoning(reasoning)
    }
    if (artifacts.isNotEmpty()) {
      turnBuilder.addAllArtifactsBefore(artifacts)
    }
    if (interactions.isNotEmpty()) {
      turnBuilder.addAllInteractions(interactions)
    }
    return turnBuilder.build()
  }

  private fun buildArtifact(type: ArtifactType, uri: String): Artifact {
    return Artifact.newBuilder()
      .setType(type)
      .setUri(uri)
      .build()
  }

  private fun buildInteraction(command: String, status: Status): Interaction {
    return Interaction.newBuilder()
      .setCommand(command)
      .setResult(
        Result.newBuilder()
          .setStatus(status)
          .build(),
      )
      .build()
  }


  private fun encodeStep(step: Step): String {
    return Base64.getEncoder().encodeToString(step.toByteArray())
  }

}
