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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.tools.idea.ui.resourcemanager.model.StaticStringMapper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test


class QualifierMatcherTest {

  @Test
  fun parsePathWithNoMapper() {
    val qualifierLexer = QualifierMatcher()
    val (resourceName, qualifiers) = qualifierLexer.parsePath("/test/Path/file.png")
    assertEquals("file", resourceName)
    assertEquals(0, qualifiers.size)
    checkResult(qualifierLexer.parsePath("/test/Path/file-en-rGB-land.png"), "file",
                LocaleQualifier("en-rGB", "en", "GB", null),
                ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE))
    checkResult(qualifierLexer.parsePath("/test/Path-en-rGB-land/file-not-qualifiers.png"), "file_not_qualifiers",
                LocaleQualifier("en-rGB", "en", "GB", null),
                ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE))
    checkResult(qualifierLexer.parsePath("/test/en-rGB-land/file-not-qualifiers.png"), "file_not_qualifiers",
                LocaleQualifier("en-rGB", "en", "GB", null),
                ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE))
    checkResult(qualifierLexer.parsePath("/test/Path-not-qualifiers/file-not-qualifiers.png"), "file_not_qualifiers")
    // svg is not a valid locale so it should not be returned.
    checkResult(qualifierLexer.parsePath("/test/Path/svg/file.svg"), "file")
    checkResult(qualifierLexer.parsePath("/test/Path/en/file.svg"), "file",
                LocaleQualifier("en", "en", null, null))
  }


  @Test
  fun parsePathWithFileNameMappers() {
    val mappers = setOf(
        StaticStringMapper(
          mapOf(
              "@2x" to DensityQualifier(Density.XHIGH),
              "@3x" to DensityQualifier(Density.XXHIGH)
          ),
          DensityQualifier(Density.MEDIUM))
    )
    val qualifierLexer = QualifierMatcher(mappers)
    checkResult(qualifierLexer.parsePath("icon@2x.png"), "icon", DensityQualifier(Density.XHIGH))
    checkResult(qualifierLexer.parsePath("icon@3x.png"), "icon", DensityQualifier(Density.XXHIGH))
    checkResult(qualifierLexer.parsePath("icon.png"), "icon", DensityQualifier(Density.MEDIUM))
    checkResult(qualifierLexer.parsePath("common/icon@2x.png"), "icon", DensityQualifier(Density.XHIGH))
    checkResult(qualifierLexer.parsePath("common\\icon@2x.png"), "icon", DensityQualifier(Density.XHIGH))
  }

  @Test
  fun parsePathWithIncompleteMapper() {
    val mappers = setOf(
        StaticStringMapper(
          mapOf(
              "@2x" to DensityQualifier(Density.XHIGH),
              "@3x" to DensityQualifier(Density.XXHIGH)
          ),
          DensityQualifier(Density.MEDIUM)),
        StaticStringMapper(mapOf(
            "_dark" to NightModeQualifier(NightMode.NIGHT)
        )))
    val qualifierLexer = QualifierMatcher(mappers)
    checkResult(qualifierLexer.parsePath("icon@2x_dark.png"), "icon", DensityQualifier(Density.XHIGH), NightModeQualifier(NightMode.NIGHT))
    checkResult(qualifierLexer.parsePath("icon_dark@2x.png"), "icon_dark", DensityQualifier(Density.XHIGH))
    checkResult(qualifierLexer.parsePath("icon_dark.png"), "icon", NightModeQualifier(NightMode.NIGHT), DensityQualifier(Density.MEDIUM))
  }

  @Test
  fun parsePathEmptyStringMapper() {
    val mappers = setOf(
        StaticStringMapper(mapOf(
                "@2x" to DensityQualifier(Density.XHIGH),
                "@3x" to DensityQualifier(Density.XXHIGH),
                "" to DensityQualifier(Density.MEDIUM))),
        StaticStringMapper(mapOf(
            "_dark" to NightModeQualifier(NightMode.NIGHT)
        )))
    val qualifierLexer = QualifierMatcher(mappers)
    checkResult(qualifierLexer.parsePath("icon@2x_dark.png"), "icon", DensityQualifier(Density.XHIGH), NightModeQualifier(NightMode.NIGHT))
    checkResult(qualifierLexer.parsePath("icon_dark@2x.png"), "icon_dark", DensityQualifier(Density.XHIGH))
    checkResult(qualifierLexer.parsePath("icon_dark.png"), "icon", NightModeQualifier(NightMode.NIGHT), DensityQualifier(Density.MEDIUM))
  }

  private fun checkResult(result: QualifierMatcher.Result, name: String, vararg qualifiers: ResourceQualifier) {
    val (resourceName, resultQualifiers) = result
    assertEquals(name, resourceName)
    assertArrayEquals(resultQualifiers.toTypedArray(), qualifiers)
  }
}