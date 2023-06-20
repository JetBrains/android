/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.idea.ui.resourcemanager.model.MatcherEntry
import com.android.tools.idea.ui.resourcemanager.model.StaticStringMapper
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger


private const val delimiter = ",,"
private val defaultConfiguration = QualifierMatcherConfiguration(null, listOf())

/**
 * Manager to save and load the importation settings.
 *
 * This class can be used to save a custom configuration to map a file to a qualifier.
 *
 */
@State(
  name = "ImportConfigurationManager",
  storages = [Storage(value = "design_importer.xml")]
)
class ImportConfigurationManager : PersistentStateComponent<QualifierMatcherConfiguration> {
  // For now, the class is not used and was part of an experiment where user could
  // edit how to map some token on a file path a qualifier via the UI.
  //
  // The class is kept around because it might be a good idea to let users customize how
  // they want their assets to be imported (e.g maybe their designer use some custom file structure
  // for organizing the densities of their icons). This can also be modified to read the configuration from
  // a file in a separate folder along with the design resource that might be living outside the project.

  private var configuration: QualifierMatcherConfiguration = defaultConfiguration

  override fun getState(): QualifierMatcherConfiguration? = configuration

  override fun loadState(state: QualifierMatcherConfiguration) {
    this.configuration = state
  }

  fun saveMappers(mappers: Set<StaticStringMapper>) {
    configuration.serializedMatchers =
        mappers
          .flatMap { mapper ->
            mapper.matchers
              .map { (matchingString, resourceQualifier) -> serializeMatcher(resourceQualifier, matchingString) }
              .toList()
          }
  }

  fun loadMappers(): Set<StaticStringMapper> {
    return try {
      configuration.serializedMatchers
        .mapNotNull { deserializeMatcher(it) }
        .groupBy({ (qualifier, _) -> qualifier.name }, { (_, matcherEntry) -> matcherEntry })
        .map { (_, matcherEntries) -> StaticStringMapper(matcherEntries) }
        .toSet()
    } catch (ex: Exception) {
      Logger.getInstance(this::class.java).error("Couldn't load import configuration. Using default one instead.", ex)
      emptySet()
    }
  }

  /**
   * Find the [ResourceQualifier] class corresponding to the given [qualifierString] and
   * associate it to a [MatcherEntry]
   * built with the value of the [ResourceQualifier] and the [mapperString].
   *
   * @see FolderConfiguration.getConfigFromQualifiers
   */
  private fun qualifierToMatcherEntry(
    qualifierString: String,
    mapperString: String
  ): Pair<ResourceQualifier, MatcherEntry>? {
    val qualifiers = FolderConfiguration.getConfigFromQualifiers(listOf(qualifierString))?.qualifiers ?: return null
    return if (qualifiers.size == 1) {
      val qualifier = qualifiers[0]
      qualifier to MatcherEntry(mapperString, qualifier)
    } else null
  }

  /**
   * Serialize a [MatcherEntry] as a string in the form of "qualifier,,matchingString".
   *
   * The qualifier part is retrieved using [ResourceQualifier.getFolderSegment].
   */
  private fun serializeMatcher(resourceQualifier: ResourceQualifier, matchingString: String): String =
    "${resourceQualifier.folderSegment}$delimiter$matchingString"

  /**
   * Opposite of [serializedMatcher]. Take a string in the form of "qualifier,,matchingString" where
   * qualifier is the string returned by [ResourceQualifier.getFolderSegment], and returns a
   */
  private fun deserializeMatcher(serializedMatcher: String): Pair<ResourceQualifier, MatcherEntry>? {
    val (qualifierString, mapperString) = serializedMatcher.split(delimiter, ignoreCase = true, limit = 2)
    return qualifierToMatcherEntry(qualifierString, mapperString)
  }
}

data class QualifierMatcherConfiguration(
  var designFolder: String? = null,
  var serializedMatchers: List<String> = mutableListOf()
  // Mutable list is needed because the intellij deserializer
  // tries to clear it and then populate it. It can't be null either
)