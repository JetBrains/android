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

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.HighDynamicRangeQualifier
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.NavigationMethodQualifier
import com.android.ide.common.resources.configuration.NavigationStateQualifier
import com.android.ide.common.resources.configuration.NetworkCodeQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier
import com.android.ide.common.resources.configuration.ScreenHeightQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.ide.common.resources.configuration.ScreenRatioQualifier
import com.android.ide.common.resources.configuration.ScreenRoundQualifier
import com.android.ide.common.resources.configuration.ScreenSizeQualifier
import com.android.ide.common.resources.configuration.ScreenWidthQualifier
import com.android.ide.common.resources.configuration.SmallestScreenWidthQualifier
import com.android.ide.common.resources.configuration.TextInputMethodQualifier
import com.android.ide.common.resources.configuration.TouchScreenQualifier
import com.android.ide.common.resources.configuration.UiModeQualifier
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.ide.common.resources.configuration.WideGamutColorQualifier
import com.android.resources.Density
import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.CollectionParam
import com.android.tools.idea.resourceExplorer.IntParam
import com.android.tools.idea.resourceExplorer.TextParam
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QualifierConfigurationViewModelTest {

  private val qualifiersUnderTest = listOf(
    DensityQualifier(),
    HighDynamicRangeQualifier(),
    KeyboardStateQualifier(),
    LayoutDirectionQualifier(),
    NavigationMethodQualifier(),
    NavigationStateQualifier(),
    NetworkCodeQualifier(),
    NightModeQualifier(),
    ScreenDimensionQualifier(),
    ScreenOrientationQualifier(),
    ScreenRatioQualifier(),
    ScreenRoundQualifier(),
    ScreenSizeQualifier(),
    ScreenWidthQualifier(),
    ScreenWidthQualifier(),
    ScreenHeightQualifier(),
    SmallestScreenWidthQualifier(),
    TextInputMethodQualifier(),
    TouchScreenQualifier(),
    UiModeQualifier(),
    VersionQualifier(),
    WideGamutColorQualifier()
  )

  @Test
  fun constructAllQualifiers() {
    val fileImportRowViewModel = QualifierConfigurationViewModel()
    qualifiersUnderTest.forEach { qualifier ->
      val qualifierConfiguration = fileImportRowViewModel.getQualifierConfiguration(qualifier)
      assertNotNull(qualifierConfiguration)
      qualifierConfiguration!!.parameters.forEach { qualifierParam ->
        when (qualifierParam) {
          is IntParam -> qualifierParam.paramValue = qualifierParam.range!!.first
          is CollectionParam<*> -> (qualifierParam as CollectionParam<Any?>).paramValue = qualifierParam.values.first()!!
          is TextParam -> qualifierParam.paramValue = "a"
        }
      }
      val actual = qualifierConfiguration.buildQualifier()
      assertNotNull(actual, qualifier.name)
      assertNotEquals(qualifier, actual)
    }
  }

  @Test
  fun createCustomConfiguration() {
    val folderConfiguration = FolderConfiguration.createDefault()
    val viewModel = QualifierConfigurationViewModel(folderConfiguration)
    val availableQualifiers = viewModel.getAvailableQualifiers()
    assertEquals(folderConfiguration.qualifiers.size, availableQualifiers.size)
    val qualifierConfiguration = viewModel.getQualifierConfiguration(availableQualifiers.first { it is DensityQualifier })
    assertNotNull(qualifierConfiguration)
    assertEquals(1, qualifierConfiguration!!.parameters.size)
    assertTrue { qualifierConfiguration.parameters[0] is CollectionParam<*> }
    val listParam = qualifierConfiguration.parameters[0] as CollectionParam<Density>
    val density = listParam.values.first { it == Density.DPI_260 }
    listParam.paramValue = density
    assertEquals(listParam.paramValue, density)
    assertEquals(DensityQualifier(Density.DPI_260), qualifierConfiguration.buildQualifier())
  }

  @Test
  fun applyToConfiguration() {
    val folderConfiguration = FolderConfiguration()
    val viewModel = QualifierConfigurationViewModel(folderConfiguration)
    var callbackCalled = false
    viewModel.onConfigurationUpdated = { callbackCalled = true }
    assertFalse { viewModel.canAddQualifier() }

    // No qualifier selected yet, we can't add another one
    val availableQualifiers = viewModel.getAvailableQualifiers()
    assertFalse { viewModel.canAddQualifier() }

    // We now request the configuration for DensityQualifier and we should be able to request a new one
    val qualifierConfiguration = viewModel.getQualifierConfiguration(availableQualifiers.first { it is DensityQualifier })
    assertTrue { viewModel.canAddQualifier() }

    val localQualifierConfiguration = viewModel.getQualifierConfiguration(
      viewModel.getAvailableQualifiers().first { it is LocaleQualifier })
    val language = localQualifierConfiguration!!.parameters[0] as CollectionParam<String?>
    val region = localQualifierConfiguration.parameters[1] as CollectionParam<String?>
    language.paramValue = language.values.first { it == "fr" }
    assertThat(region.values.toList()).containsExactly(null, "FR", "BI", "BE", "BJ", "BF", "BL", "CF", "CA", "CH", "CI", "CM",
                                                       "CD", "CG", "KM", "DJ", "DZ", "GA", "GN", "GP", "GQ", "GF", "HT", "LU",
                                                       "MF", "MA", "MC", "MG", "ML", "MR", "MQ", "MU", "YT", "NC", "NE", "PF",
                                                       "RE", "RW", "SN", "PM", "SC", "SY", "TD", "TG", "TN", "VU", "WF")
    assertTrue { viewModel.canAddQualifier() }

    region.paramValue = region.values.first { it == "BE" }
    val listParam = qualifierConfiguration!!.parameters[0] as CollectionParam<Density>
    val density = listParam.values.first { it == Density.DPI_260 }
    listParam.paramValue = density
    val folderConfiguration1 = viewModel.applyConfiguration()
    assertEquals(folderConfiguration, folderConfiguration1)
    assertTrue { callbackCalled }
    assertEquals("drawable-fr-rBE-260dpi", folderConfiguration.getFolderName(ResourceFolderType.DRAWABLE))
  }

  @Test
  fun defaults() {
    val folderConfiguration = FolderConfiguration()

    val densityInit = DensityQualifier(Density.DPI_260)
    val localeInit = LocaleQualifier(null, "fr", "US", null)
    val networkInit = NetworkCodeQualifier(123)
    val screenDimensionInit = ScreenDimensionQualifier(12, 34)

    folderConfiguration.addQualifier(densityInit)
    folderConfiguration.addQualifier(localeInit)
    folderConfiguration.addQualifier(networkInit)
    folderConfiguration.addQualifier(screenDimensionInit)

    val viewModel = QualifierConfigurationViewModel(folderConfiguration)
    val densityConfiguration = viewModel.getQualifierConfiguration(densityInit)
    val localeConfiguration = viewModel.getQualifierConfiguration(localeInit)
    val networkConfiguration = viewModel.getQualifierConfiguration(networkInit)
    val screenSizeConfiguration = viewModel.getQualifierConfiguration(screenDimensionInit)

    assertEquals(Density.DPI_260, densityConfiguration!!.parameters[0].paramValue)

    assertEquals("fr", localeConfiguration!!.parameters[0].paramValue)
    assertEquals("US", localeConfiguration.parameters[1].paramValue)

    assertEquals(123, networkConfiguration!!.parameters[0].paramValue)

    assertEquals(12, screenSizeConfiguration!!.parameters[0].paramValue)
    assertEquals(34, screenSizeConfiguration.parameters[1].paramValue)
  }
}
