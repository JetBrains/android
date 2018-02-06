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

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.resources.Density
import com.android.resources.KeyboardState
import com.android.resources.NightMode
import com.android.tools.idea.resourceExplorer.model.StaticStringMapper
import com.google.common.collect.testing.Helpers
import org.junit.Test
import kotlin.test.assertEquals

class ImportConfigurationManagerTest {

  @Test
  fun saveMappers() {
    val manager = ImportConfigurationManager()
    val mappers = setOf(
      StaticStringMapper(
        matchers = mapOf(
          "@2x" to DensityQualifier(Density.XXXHIGH),
          "" to DensityQualifier(Density.MEDIUM)
        )
      ),
      StaticStringMapper(
        matchers = mapOf(
          "_dark" to NightModeQualifier(NightMode.NIGHT)
        )
      ),
      StaticStringMapper(
        matchers = mapOf(
          "-inline" to KeyboardStateQualifier(KeyboardState.EXPOSED)
        )
      )
    )
    manager.saveMappers(mappers)
    val segmentsToStrings = manager.state!!.serializedMatchers
    Helpers.assertContentsInOrder(segmentsToStrings, "xxxhdpi,,@2x", "mdpi,,", "night,,_dark", "keysexposed,,-inline")
  }

  @Test
  fun loadMappers() {
    val manager = ImportConfigurationManager()
    manager.loadState(QualifierMatcherConfiguration("", listOf("xxxhdpi,,@2x", "mdpi,,", "night,,_dark", "keysexposed,,-inline")))
    val mappers = manager.loadMappers()
    val (densityMapper, nightModeMapper, keyboardMapper) = mappers.toList()
    assertEquals(DensityQualifier(), densityMapper.defaultQualifier)
    assertEquals(NightModeQualifier(NightMode.NIGHT), nightModeMapper.getQualifier("_dark"))
    assertEquals(KeyboardStateQualifier(KeyboardState.EXPOSED), keyboardMapper.getQualifier("-inline"))

    assertEquals(
      mapOf(
        "@2x" to DensityQualifier(Density.XXXHIGH),
        "" to DensityQualifier(Density.MEDIUM)
      ),
      densityMapper.matchers
    )
    assertEquals(mapOf("_dark" to NightModeQualifier(NightMode.NIGHT)), nightModeMapper.matchers)
    assertEquals(mapOf("-inline" to KeyboardStateQualifier(KeyboardState.EXPOSED)), keyboardMapper.matchers)
  }
}