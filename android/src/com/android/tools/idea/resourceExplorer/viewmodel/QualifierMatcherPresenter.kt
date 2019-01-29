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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.ide.common.resources.configuration.*
import com.android.resources.*
import com.android.tools.idea.resourceExplorer.importer.ImportConfigurationManager
import com.android.tools.idea.resourceExplorer.importer.QualifierMatcher
import com.android.tools.idea.resourceExplorer.model.StaticStringMapper
import com.android.tools.idea.resourceExplorer.model.MatcherEntry

/**
 * Presenter class to interact with the [QualifierMatcher]
 *
 * @param matcherConsumer function that will be called with new [QualifierMatcher] when it will been created.
 * @param configurationManager [ImportConfigurationManager] to handle saving and loading the state of this
 * [QualifierMatcherPresenter]
 */
class QualifierMatcherPresenter(
  private val matcherConsumer: (QualifierMatcher) -> Unit,
  private val configurationManager: ImportConfigurationManager
) {

  private val supportedQualifiers = mapOf<ResourceQualifier, List<ResourceQualifier>>(
    qualifierToParameters<DensityQualifier, Density>(),
    qualifierToParameters<NightModeQualifier, NightMode>(),
    qualifierToParameters<ScreenOrientationQualifier, ScreenOrientation>(),
    qualifierToParameters<HighDynamicRangeQualifier, HighDynamicRange>(),
    qualifierToParameters<KeyboardStateQualifier, KeyboardState>(),
    qualifierToParameters<LayoutDirectionQualifier, LayoutDirection>()
  )

  private inline fun <reified R : ResourceQualifier, reified V : Enum<V>> qualifierToParameters(): Pair<R, List<R>> {
    val enumValues = enumValues<V>()
    val constructor = R::class.java.getConstructor(V::class.java)
    return constructor.newInstance(enumValues[0]) to enumValues.map { constructor.newInstance(it) }
  }

  fun getAvailableQualifiers(): Set<ResourceQualifier> = supportedQualifiers.keys

  fun getValuesForQualifier(qualifier: ResourceQualifier) = supportedQualifiers[qualifier]

  fun setMatcherEntries(entries: List<Pair<ResourceQualifier, List<MatcherEntry>>>) {
    val mappers = entries
      .map { (_, matcherEntry) ->
        StaticStringMapper(
          matchers = matcherEntry.associate { (string, resourceEnum) -> string to resourceEnum }
        )
      }
      .toSet()
    matcherConsumer(QualifierMatcher(mappers))
    saveConfiguration(mappers)
  }

  private fun saveConfiguration(entries: Set<StaticStringMapper>) {
    configurationManager.saveMappers(entries)
  }

  fun getConfiguration(): Set<StaticStringMapper> {
    return configurationManager.loadMappers()
  }
}
