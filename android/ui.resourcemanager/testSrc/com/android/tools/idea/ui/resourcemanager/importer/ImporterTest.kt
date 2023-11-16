/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.tools.idea.ui.resourcemanager.densityMapper
import com.android.tools.idea.ui.resourcemanager.getExternalResourceDirectory
import com.android.tools.idea.ui.resourcemanager.model.Mapper
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.ui.resourcemanager.model.getAssetSets
import com.android.tools.idea.ui.resourcemanager.nightModeMapper
import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.regex.MatchResult
import java.util.regex.Pattern

@RunsInEdt
class ImporterTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @get:Rule
  val edtRule = EdtRule()

  private val supportedTypes = setOf("png", "jpg")

  @Test
  fun getCreateAsset() {
    val directory = runWriteAction {
      getExternalResourceDirectory(
        "icon.png",
        "icon@2x.png",
        "icon@3x.jpg",
        "image.jpg",
        "image@4x.jpg",
        "image@4x_dark.jpg"
      )
    }

    val assetSets = getAssetSets(
      directory,
      supportedTypes,
      QualifierMatcher(densityMapper, nightModeMapper)
    )
    assertEquals(2, assetSets.size)
    val iconAssetSets = assetSets[0]
    assertEquals(iconAssetSets.name, "icon")

    val iconAssetsList = iconAssetSets.designAssets.toList()
    assertEquals("icon.png", iconAssetsList[0].file.name)
    assertEquals(DensityQualifier(Density.MEDIUM), iconAssetsList[0].qualifiers[0])
    assertEquals("icon@2x.png", iconAssetsList[1].file.name)
    assertEquals(DensityQualifier(Density.XHIGH), iconAssetsList[1].qualifiers[0])
    assertEquals("icon@3x.jpg", iconAssetsList[2].file.name)
    assertEquals(DensityQualifier(Density.XXHIGH), iconAssetsList[2].qualifiers[0])

    val imageAssetSet = assetSets[1]
    assertEquals(imageAssetSet.name, "image")

    val imageAssetList = imageAssetSet.designAssets.toList()
    assertEquals("image.jpg", imageAssetList[0].file.name)
    assertEquals(DensityQualifier(Density.MEDIUM), imageAssetList[0].qualifiers[0])
    assertEquals("image@4x.jpg", imageAssetList[1].file.name)
    assertEquals(DensityQualifier(Density.XXXHIGH), imageAssetList[1].qualifiers[0])

    assertEquals("image@4x_dark.jpg", imageAssetList[2].file.name)
    assertEquals(DensityQualifier(Density.XXXHIGH), imageAssetList[2].qualifiers[0])
    assertEquals(NightModeQualifier(NightMode.NIGHT), imageAssetList[2].qualifiers[1])
  }

  @Test
  fun getCreateAssetMultiDir() {
    val directory = runWriteAction { getExternalResourceDirectory() }
    runWriteAction {
      with(directory.createChildDirectory(this, "fr")) {
        createChildData(this, "icon.png")
        createChildData(this, "icon@2x.png")
        createChildData(this, "image@4x.jpg")
      }
      with(directory.createChildDirectory(this, "en")) {
        createChildData(this, "image.jpg")
        createChildData(this, "icon@3x.jpg")
      }
    }

    val localeMapper = object : Mapper<LocaleQualifier> {
      override val pattern: Pattern = Pattern.compile("([a-z]{2}?)/")

      override val defaultQualifier: LocaleQualifier? = null

      override fun getQualifier(value: String?) = if (value != null) LocaleQualifier(value) else null

      override fun getValue(matcher: MatchResult): String? = matcher.group(1)
    }
    val assetSets = getAssetSets(
      directory, supportedTypes, QualifierMatcher(
        localeMapper,
        densityMapper
      )
    )
    assertEquals(assetSets.toString(), 2, assetSets.size)
    val iconAssetSets = assetSets[0]
    assertEquals(iconAssetSets.name, "icon")

    val iconAssetsList = iconAssetSets.designAssets.toList()
    assertEquals("icon.png", iconAssetsList[0].file.name)
    assertArrayEquals(arrayOf(LocaleQualifier("fr"), DensityQualifier(Density.MEDIUM)), iconAssetsList[0].qualifiers.toTypedArray())
    assertEquals("icon@2x.png", iconAssetsList[1].file.name)
    assertArrayEquals(arrayOf(LocaleQualifier("fr"), DensityQualifier(Density.XHIGH)), iconAssetsList[1].qualifiers.toTypedArray())
    assertEquals("icon@3x.jpg", iconAssetsList[2].file.name)
    assertArrayEquals(arrayOf(LocaleQualifier("en"), DensityQualifier(Density.XXHIGH)), iconAssetsList[2].qualifiers.toTypedArray())

    val imageAssetSet = assetSets[1]
    assertEquals(imageAssetSet.name, "image")

    val imageAssetList = imageAssetSet.designAssets.toList()
    assertEquals("image@4x.jpg", imageAssetList[0].file.name)
    assertArrayEquals(arrayOf(LocaleQualifier("fr"), DensityQualifier(Density.XXXHIGH)), imageAssetList[0].qualifiers.toTypedArray())
    assertEquals("image.jpg", imageAssetList[1].file.name)
    assertArrayEquals(arrayOf(LocaleQualifier("en"), DensityQualifier(Density.MEDIUM)), imageAssetList[1].qualifiers.toTypedArray())
  }
}