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

import com.android.tools.journeys.proto.ArtifactType
import com.android.tools.journeys.proto.Interaction
import com.android.tools.journeys.proto.Step
import com.android.tools.journeys.proto.Turn
import java.util.Base64

data class JourneyActionArtifacts(
  val description: String?,
  val reasoning: String?,
  val screenshotImage: String?,
  val interactions: List<String> = emptyList(),
) {
  companion object {
    /**
     * Extracts Journey-related artifacts from a map of additional test artifacts.
     *
     * This function looks for a key named "Journeys.Step" in the provided map. The value associated
     * with this key is expected to be a Base64 encoded string of a `Step` proto. This `Step` proto
     * contains a list of `Turn` objects, each representing an action within a test journey.
     * `JourneyActionArtifacts` objects are then created from the `Turn` objects within the `Step`
     * proto.
     *
     * @param additionalTestArtifacts A map of key-value pairs representing additional test
     *   artifacts.
     * @return A list of [JourneyActionArtifacts] parsed from the input map, or an empty list if no
     *   valid journey artifacts are found.
     *   The returned list is ordered by the sequence of turns in the `Step` proto.
     */
    fun parseFromAdditionalTestArtifacts(
      additionalTestArtifacts: Map<String, String>
    ): List<JourneyActionArtifacts> {
      val encodedJourneyStepResult = additionalTestArtifacts["Journeys.Step"] ?: return emptyList()
      val journeyStepResult = decodeStepResult(encodedJourneyStepResult)
      return journeyStepResult.turnsList.map {
        JourneyActionArtifacts(
          it.description?.nullIfEmpty(),
          it.reasoning?.nullIfEmpty(),
          getScreenshot(it),
          it.interactionsList.map { interaction -> formatInteraction(interaction) }
        )
      }
    }

    private fun decodeStepResult(base64String: String): Step {
      val decodedBytes = Base64.getDecoder().decode(base64String)
      return Step.parseFrom(decodedBytes)
    }

    private fun getScreenshot(turn: Turn): String? {
      return turn.artifactsBeforeList.find { it.type == ArtifactType.SCREENSHOT }?.uri
    }

    /**
     * Formats an [Interaction] proto into a human-readable string.
     *
     * The format is "command (status)". For example: "Click (SUCCEEDED)".
     */
    private fun formatInteraction(interaction: Interaction): String {
      return "${interaction.command} (${interaction.result.status.name})"
    }
  }
}

private fun String.nullIfEmpty(): String? = ifEmpty { null }
