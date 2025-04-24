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

import java.util.regex.Pattern

data class JourneyActionArtifacts(
  val description: String?,
  val reasoning: String?,
  val screenshotImage: String?,
) {
  companion object {
    fun tryParseFromTestOutput(output: String): JourneyActionArtifacts? {
      if (!output.startsWith("[additionalTestArtifacts]Journeys")) {
        return null
      }

      // TODO: Implement a more robust way of encoding the artifact data (perhaps proto?)
      val regex =
        Regex(
          "^\\[additionalTestArtifacts]Journeys.screenshot=(.*) Journeys.action=(.*) Journeys.modelReasoning=(.*)$"
        )
      val match = regex.find(output)
      if (match == null || match.groups.size < 3) {
        return null
      }

      val screenshotPath = match.groupValues.getOrNull(1) ?: return null
      val action = match.groupValues.getOrNull(2) ?: return null
      val reason = match.groupValues.getOrNull(3) ?: return null
      return JourneyActionArtifacts(action, reason, screenshotPath)
    }

    /**
     * Extracts journey-related artifacts from a map of additional test artifacts.
     *
     * The expected key format for journey artifacts is:
     * `"Journeys.ActionPerformed.action{index}.{artifactType}"` or
     * `Journeys.PromptComplete.prompt{index}.{artifactType}"`, where:
     * - `index` is a positive integer representing the prompt/action's index (e.g., "1", "2").
     * - `artifactType` is one of the following:
     *     - `"screenshotPath"`: The path to a screenshot image.
     *     - `"description"`: A description of the user action.
     *     - `"modelReasoning"`: The model's reasoning for the action.
     *
     * Artifacts are grouped by their `index` and ordered by ascending index, with the
     * `ActionPerformed` artifacts placed ordered before the `PromptComplete` artifacts.
     *
     * @param additionalTestArtifacts A map of key-value pairs representing additional test
     *   artifacts.
     * @return A list of [JourneyActionArtifacts] parsed from the input map, or an empty list if no
     *   valid journey artifacts are found. The returned list is sorted by ascending action index.
     */
    fun parseFromAdditionalTestArtifacts(
      additionalTestArtifacts: Map<String, String>
    ): List<JourneyActionArtifacts> {
      val journeyArtifacts = additionalTestArtifacts.filter { it.key.startsWith("Journeys.") }

      // Helper function to parse artifacts for a specific journey type (ActionPerformed or
      // PromptComplete)
      fun extractGroupedArtifacts(
        sourceArtifacts: Map<String, String>,
        pattern: Pattern,
      ): List<JourneyActionArtifacts> {
        val groupedData = mutableMapOf<String, MutableMap<String, String>>()

        for ((key, value) in sourceArtifacts) {
          val matcher = pattern.matcher(key)
          if (matcher.find()) {
            val index = matcher.group(1)
            val artifactType = matcher.group(2)
            groupedData.computeIfAbsent(index) { mutableMapOf() }[artifactType] = value
          }
        }

        return groupedData.entries
          .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
          .mapNotNull { (_, dataMap) ->
            val description = dataMap["description"]
            val reasoning = dataMap["modelReasoning"]
            val screenshotImage = dataMap["screenshotPath"]

            // Create an artifact if at least one piece of information is present
            if (description != null || reasoning != null || screenshotImage != null) {
              JourneyActionArtifacts(
                description = description,
                reasoning = reasoning,
                screenshotImage = screenshotImage,
              )
            } else {
              null
            }
          }
      }

      val actionPerformedArtifacts =
        extractGroupedArtifacts(
          journeyArtifacts,
          Pattern.compile("""^Journeys\.ActionPerformed\.action(\d+)\.(\w+)$"""),
        )
      val promptCompleteArtifacts =
        extractGroupedArtifacts(
          journeyArtifacts,
          Pattern.compile("""^Journeys\.PromptComplete\.prompt(\d+)\.(\w+)$"""),
        )
      return actionPerformedArtifacts + promptCompleteArtifacts
    }
  }
}
