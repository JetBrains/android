/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.importer

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.idea.resourceExplorer.model.StaticStringMapper
import com.android.tools.idea.resourceExplorer.model.MatcherEntry
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger


private const val delimiter = ",,"
private val defaultConfiguration = QualifierMatcherConfiguration(null, listOf())

/**
 * Manager to save and load the importation settings.
 */
@State(
  name = "ImportConfigurationManager",
  storages = arrayOf(Storage(value = "design_importer.xml"))
)
class ImportConfigurationManager : PersistentStateComponent<QualifierMatcherConfiguration> {

  private var configuration: QualifierMatcherConfiguration = defaultConfiguration

  override fun getState(): QualifierMatcherConfiguration? = configuration

  override fun loadState(state: QualifierMatcherConfiguration?) {
    this.configuration = state ?: defaultConfiguration
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