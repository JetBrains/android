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

import com.android.ide.common.resources.LocaleManager
import com.android.ide.common.resources.configuration.CountryCodeQualifier
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.EnumBasedResourceQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.HighDynamicRangeQualifier
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.NavigationMethodQualifier
import com.android.ide.common.resources.configuration.NavigationStateQualifier
import com.android.ide.common.resources.configuration.NetworkCodeQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ResourceQualifier
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
import com.android.resources.ResourceEnum
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.resourceExplorer.CollectionParam
import com.android.tools.idea.resourceExplorer.InputParam
import com.android.tools.idea.resourceExplorer.IntParam
import java.util.EnumSet


/**
 * Valid range for screen sizes.
 * We don't have devices with more than 100 000 pixels (yet).
 */
private val SCREEN_SIZE_RANGE = 1..100_000

/**
 * Valid range for the CountryCodeQualifier
 */
private val COUNTRY_CODE_RANGE = 101..999

/**
 * Valid range for the NetworkCodeQualifier.
 */
private val NETWORK_CODE_RANGE = 0..1000

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.QualifierConfigurationPanel].
 */
class QualifierConfigurationViewModel(private val folderConfiguration: FolderConfiguration = FolderConfiguration()) {

  var onConfigurationUpdated: ((FolderConfiguration) -> Unit)? = null
  private val availableQualifiers = FolderConfiguration.createDefault().qualifiers.toMutableSet() // Cannot use a set because of the hashCode() implementation of some qualifiers
  private val usedQualifiers = mutableMapOf<ResourceQualifier, QualifierConfiguration?>()
  private var lastRequestedQualifier: Pair<ResourceQualifier, QualifierConfiguration?>? = null

  fun canAddQualifier() = lastRequestedQualifier != null && availableQualifiers.isNotEmpty()

  fun applyConfiguration(): FolderConfiguration {
    usedQualifiers.values
      .filterNotNull()
      .map(QualifierConfiguration::buildQualifier)
      .forEach(folderConfiguration::addQualifier)

    lastRequestedQualifier?.let { (_, configuration) ->
      configuration?.buildQualifier()?.let(folderConfiguration::addQualifier)
    }

    onConfigurationUpdated?.invoke(folderConfiguration)
    return folderConfiguration
  }

  fun getAvailableQualifiers(): List<ResourceQualifier> {
    lastRequestedQualifier?.let { (qualifier, configuration) ->
      availableQualifiers.remove(qualifier)
      usedQualifiers[qualifier] = configuration
      lastRequestedQualifier = null
    }
    return availableQualifiers.toList()
  }

  /**
   * Returns the suitable [QualifierConfiguration] for the provided [qualifier].
   */
  fun getQualifierConfiguration(qualifier: ResourceQualifier): QualifierConfiguration? {
    val qualifierConfiguration: QualifierConfiguration? = when (qualifier) {
      is LocaleQualifier -> LocaleQualifierConfiguration()
      is CountryCodeQualifier -> IntConfiguration(::CountryCodeQualifier, COUNTRY_CODE_RANGE)
      is DensityQualifier -> enumConfiguration(::DensityQualifier)
      is HighDynamicRangeQualifier -> enumConfiguration(::HighDynamicRangeQualifier)
      is KeyboardStateQualifier -> enumConfiguration(::KeyboardStateQualifier)
      is LayoutDirectionQualifier -> enumConfiguration(::LayoutDirectionQualifier)
      is NavigationMethodQualifier -> enumConfiguration(::NavigationMethodQualifier)
      is NavigationStateQualifier -> enumConfiguration(::NavigationStateQualifier)
      is NetworkCodeQualifier -> IntConfiguration(::NetworkCodeQualifier, NETWORK_CODE_RANGE)
      is NightModeQualifier -> enumConfiguration(::NightModeQualifier)
      is ScreenDimensionQualifier -> ScreenDimensionConfiguration()
      is ScreenHeightQualifier -> IntConfiguration(::ScreenWidthQualifier, SCREEN_SIZE_RANGE)
      is ScreenOrientationQualifier -> enumConfiguration(::ScreenOrientationQualifier)
      is ScreenRatioQualifier -> enumConfiguration(::ScreenRatioQualifier)
      is ScreenRoundQualifier -> enumConfiguration(::ScreenRoundQualifier)
      is ScreenSizeQualifier -> enumConfiguration(::ScreenSizeQualifier)
      is ScreenWidthQualifier -> IntConfiguration(::ScreenWidthQualifier, SCREEN_SIZE_RANGE)
      is SmallestScreenWidthQualifier -> IntConfiguration(::SmallestScreenWidthQualifier, SCREEN_SIZE_RANGE)
      is TextInputMethodQualifier -> enumConfiguration(::TextInputMethodQualifier)
      is TouchScreenQualifier -> enumConfiguration(::TouchScreenQualifier)
      is UiModeQualifier -> enumConfiguration(::UiModeQualifier)
      is VersionQualifier -> VersionQualifierConfiguration()
      is WideGamutColorQualifier -> enumConfiguration(::WideGamutColorQualifier)
      else -> null
    }
    lastRequestedQualifier = qualifier to qualifierConfiguration
    return qualifierConfiguration
  }
}

/**
 * Represent the parameter needed to build a given [ResourceQualifier] type.
 * The view is responsible to generate the UI component that will allow the configuration of this object.
 */
interface QualifierConfiguration {

  /**
   * The [InputParam] needed to build a new instance of the desired [ResourceQualifier]
   */
  val parameters: List<InputParam<*>>

  /**
   * Returns a new instance of a [ResourceQualifier] using [parameters] if needed.
   */
  fun buildQualifier(): ResourceQualifier?
}

/**
 * [QualifierConfiguration] to build a [LocaleQualifier]
 */
internal class LocaleQualifierConfiguration : QualifierConfiguration {

  private val languageList = CollectionParam(LocaleManager.getLanguageCodes(true), "Language").apply {
    // Add an observer to update the region list each time the language list is updated
    addObserver { _, selectedLanguage -> regionList.values = getAvailableRegion(selectedLanguage as String?) }
    parser = { code -> code?.let { LocaleManager.getLanguageName(it) } }
  }

  /**
   * List of the available region for the selected language
   */
  private val regionList = CollectionParam<String?>(listOf(null), "Any region")
  override val parameters: List<InputParam<String?>> = listOf(languageList, regionList)

  /**
   * Returns a new [LocaleQualifier] using the selected language and the optionally selected region
   */
  override fun buildQualifier(): LocaleQualifier? {
    val language = languageList.paramValue ?: return null
    val region = regionList.paramValue
    return LocaleQualifier(null, language, region, null)
  }

  private fun getAvailableRegion(language: String?) =
    listOf(null) +
    (language?.let { LocaleManager.getRelevantRegions(it) } ?: LocaleManager.getRegionCodes(true))
}

/**
 * Utility method to build an [EnumBasedResourceQualifier]
 */
private inline fun <Qualifier : EnumBasedResourceQualifier, reified E> enumConfiguration(
  noinline factory: (E) -> Qualifier
): EnumQualifierConfiguration<E, Qualifier> where E : ResourceEnum, E : Enum<E> =
  EnumQualifierConfiguration(EnumSet.allOf(E::class.java), factory)

/**
 * Configuration to build all subclass of [EnumBasedResourceQualifier].
 */
internal class EnumQualifierConfiguration<E : ResourceEnum, out Qualifier : EnumBasedResourceQualifier>(
  enumSet: Collection<E>,
  private val qualifierFactory: (E) -> Qualifier
) : QualifierConfiguration {
  override val parameters = listOf(CollectionParam(enumSet, null) { enum -> enum?.longDisplayValue })
  override fun buildQualifier(): Qualifier? = parameters.first().paramValue?.let { qualifierFactory(it) }
}

/**
 * A [QualifierConfiguration] for [ResourceQualifier] that can be build with a single int
 */
internal class IntConfiguration(
  private val qualifierFactory: (Int) -> ResourceQualifier,
  range: IntRange
) : QualifierConfiguration {
  override val parameters: List<IntParam> = listOf(IntParam(range))
  override fun buildQualifier(): ResourceQualifier? = parameters.first().paramValue?.let { qualifierFactory(it) }
}

/**
 * A [QualifierConfiguration] to build a [VersionQualifier]. The available versions are provided using a
 * [CollectionParam] which contains Api version from [SdkVersionInfo.LOWEST_ACTIVE_API] to [SdkVersionInfo.HIGHEST_KNOWN_API]
 */
internal class VersionQualifierConfiguration : QualifierConfiguration {
  override val parameters = listOf(
    CollectionParam((SdkVersionInfo.LOWEST_ACTIVE_API..SdkVersionInfo.HIGHEST_KNOWN_API)
                      .toSortedSet()
                      .reversed()))

  override fun buildQualifier(): VersionQualifier? = parameters.first().paramValue?.let { VersionQualifier(it) }
}

/**
 * A [QualifierConfiguration] to build a [ScreenDimensionConfiguration] if both width and height are provided.
 */
internal class ScreenDimensionConfiguration : QualifierConfiguration {
  private val widthParam = IntParam(SCREEN_SIZE_RANGE)
  private val heightParam = IntParam(SCREEN_SIZE_RANGE)
  override val parameters = listOf(widthParam, heightParam)
  override fun buildQualifier(): ScreenDimensionQualifier? {
    val width = widthParam.paramValue ?: return null
    val height = heightParam.paramValue ?: return null
    return ScreenDimensionQualifier(width, height)
  }
}
