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
package com.android.tools.idea.ui.resourcemanager.qualifiers

import com.android.ide.common.resources.LocaleManager
import com.android.ide.common.resources.configuration.CountryCodeQualifier
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.EnumBasedResourceQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.GrammaticalGenderQualifier
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
import com.android.tools.idea.ui.resourcemanager.CollectionParam
import com.android.tools.idea.ui.resourcemanager.InputParam
import com.android.tools.idea.ui.resourcemanager.IntParam
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

private typealias QualifierConfigurationPair = Pair<ResourceQualifier, QualifierConfiguration?>

/**
 * ViewModel for [com.android.tools.idea.ui.resourcemanager.qualifiers.QualifierConfigurationPanel].
 */
class QualifierConfigurationViewModel(private val folderConfiguration: FolderConfiguration = FolderConfiguration()) {

  var onConfigurationUpdated: ((FolderConfiguration) -> Unit)? = null
  private val availableQualifiers = FolderConfiguration.createDefault().qualifiers.toMutableSet() // Cannot use a set because of the hashCode() implementation of some qualifiers
  private val usedQualifiers = mutableMapOf<ResourceQualifier, QualifierConfiguration?>()

  init {
    folderConfiguration.qualifiers.map { qualifier ->
      availableQualifiers.removeIf { qualifier.name == it.name }
      val configuration = createQualifierConfiguration(qualifier)
      usedQualifiers[qualifier] = configuration
    }
  }

  /**
   * Return true if no qualifier is currently being configured and there is still some qualifier
   * available.
   */
  fun canAddQualifier() = availableQualifiers.isNotEmpty()

  fun applyConfiguration(): FolderConfiguration {
    folderConfiguration.reset()
    usedQualifiers.values
      .filterNotNull()
      .map(QualifierConfiguration::buildQualifier)
      .forEach { folderConfiguration.addQualifier(it) }

    onConfigurationUpdated?.invoke(folderConfiguration)
    return folderConfiguration
  }

  fun getAvailableQualifiers() = availableQualifiers.sortedBy(ResourceQualifier::getName)

  /**
   * Return a list of pairs of [ResourceQualifier] to a [QualifierConfiguration] corresponding
   * the [FolderConfiguration].
   *
   * What this means is that for each qualifier already set in the [FolderConfiguration], a new [QualifierConfiguration]
   * is returned and the qualifier is removed from the list returned by [getAvailableQualifiers].
   */
  fun getCurrentConfigurations(): List<QualifierConfigurationPair> = usedQualifiers.map { it.toPair() }

  /**
   * Return the suitable [QualifierConfiguration] for the provided [qualifier] or null if the [qualifier]
   * is not supported.
   */
  private fun createQualifierConfiguration(qualifier: ResourceQualifier): QualifierConfiguration? {
    return when (qualifier) {
      is LocaleQualifier -> LocaleQualifierConfiguration(qualifier.language, qualifier.region)
      is CountryCodeQualifier -> IntConfiguration(::CountryCodeQualifier, COUNTRY_CODE_RANGE, qualifier.code)
      is DensityQualifier -> enumConfiguration(::DensityQualifier, qualifier.value)
      is HighDynamicRangeQualifier -> enumConfiguration(::HighDynamicRangeQualifier, qualifier.value)
      is KeyboardStateQualifier -> enumConfiguration(::KeyboardStateQualifier, qualifier.value)
      is LayoutDirectionQualifier -> enumConfiguration(::LayoutDirectionQualifier, qualifier.value)
      is NavigationMethodQualifier -> enumConfiguration(::NavigationMethodQualifier, qualifier.value)
      is NavigationStateQualifier -> enumConfiguration(::NavigationStateQualifier, qualifier.value)
      is NetworkCodeQualifier -> IntConfiguration(::NetworkCodeQualifier, NETWORK_CODE_RANGE, qualifier.code)
      is NightModeQualifier -> enumConfiguration(::NightModeQualifier, qualifier.value)
      is ScreenDimensionQualifier -> ScreenDimensionConfiguration(qualifier.value1, qualifier.value2)
      is ScreenHeightQualifier -> IntConfiguration(::ScreenWidthQualifier, SCREEN_SIZE_RANGE, qualifier.value)
      is ScreenOrientationQualifier -> enumConfiguration(::ScreenOrientationQualifier, qualifier.value)
      is ScreenRatioQualifier -> enumConfiguration(::ScreenRatioQualifier, qualifier.value)
      is ScreenRoundQualifier -> enumConfiguration(::ScreenRoundQualifier, qualifier.value)
      is ScreenSizeQualifier -> enumConfiguration(::ScreenSizeQualifier, qualifier.value)
      is ScreenWidthQualifier -> IntConfiguration(::ScreenWidthQualifier, SCREEN_SIZE_RANGE, qualifier.value)
      is SmallestScreenWidthQualifier -> IntConfiguration(::SmallestScreenWidthQualifier, SCREEN_SIZE_RANGE, qualifier.value)
      is TextInputMethodQualifier -> enumConfiguration(::TextInputMethodQualifier, qualifier.value)
      is TouchScreenQualifier -> enumConfiguration(::TouchScreenQualifier, qualifier.value)
      is UiModeQualifier -> enumConfiguration(::UiModeQualifier, qualifier.value)
      is VersionQualifier -> VersionQualifierConfiguration(qualifier.version)
      is WideGamutColorQualifier -> enumConfiguration(::WideGamutColorQualifier, qualifier.value)
      is GrammaticalGenderQualifier -> enumConfiguration(::GrammaticalGenderQualifier, qualifier.value)
      else -> null
    }
  }

  /**
   * Remove [resourceQualifier] from the [FolderConfiguration] and
   * mark it as unused.
   * @see selectQualifier
   */
  fun deselectQualifier(resourceQualifier: ResourceQualifier) {
    usedQualifiers.remove(resourceQualifier)
    availableQualifiers.add(resourceQualifier)
    applyConfiguration()
  }

  /**
   * Return the suitable [QualifierConfiguration] for the provided [resourceQualifier] and
   * mark it as used.
   * @see deselectQualifier
   */
  fun selectQualifier(resourceQualifier: ResourceQualifier): QualifierConfiguration? {
    val configuration = createQualifierConfiguration(resourceQualifier)
    usedQualifiers[resourceQualifier] = configuration
    availableQualifiers.remove(resourceQualifier)
    applyConfiguration()
    return configuration
  }
}

/**
 * Represent the parameter needed to build a given [ResourceQualifier] type.
 * The view is responsible to generate the UI component that will allow the configuration of this object.
 */
interface QualifierConfiguration {

  /**
   * The [InputParam] needed to build a new instance of the desired [ResourceQualifier].
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
internal class LocaleQualifierConfiguration(language: String?, region: String?) : QualifierConfiguration {

  /**
   * List of the available region for the selected language.
   */
  private val regionList = CollectionParam(listOf(region), "Any region").apply {
    paramValue = region
  }

  private val languageList = CollectionParam(LocaleManager.getLanguageCodes(true), "Language").apply {
    // Add an observer to update the region list each time the language list is updated
    addObserver { _, selectedLanguage -> regionList.values = getAvailableRegion(selectedLanguage as String?) }
    parser = { code -> code?.let { LocaleManager.getLanguageName(it) } }
    paramValue = language
  }

  override val parameters: List<InputParam<String?>> = listOf(languageList, regionList)

  /**
   * Returns a new [LocaleQualifier] using the selected language and the optionally selected region.
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
 * Utility method to build an [EnumBasedResourceQualifier].
 */
private inline fun <Qualifier : EnumBasedResourceQualifier, reified E> enumConfiguration(
  noinline factory: (E) -> Qualifier,
  default: E?
): EnumQualifierConfiguration<E, Qualifier> where E : ResourceEnum, E : Enum<E> =
  EnumQualifierConfiguration(EnumSet.allOf(E::class.java), factory, default)

/**
 * Configuration to build all subclass of [EnumBasedResourceQualifier].
 */
internal class EnumQualifierConfiguration<E : ResourceEnum, out Qualifier : EnumBasedResourceQualifier>(
  enumSet: Collection<E>,
  private val qualifierFactory: (E) -> Qualifier,
  default: E?
) : QualifierConfiguration {

  override val parameters = listOf(CollectionParam(enumSet) { enum -> enum?.longDisplayValue }.apply { paramValue = default })

  override fun buildQualifier(): Qualifier? = parameters.first().paramValue?.let { qualifierFactory(it) }
}

/**
 * A [QualifierConfiguration] for [ResourceQualifier] that can be build with a single int
 */
internal class IntConfiguration(
  private val qualifierFactory: (Int) -> ResourceQualifier,
  range: IntRange,
  default: Int?
) : QualifierConfiguration {
  override val parameters: List<IntParam> = listOf(IntParam(range).apply { paramValue = default })
  override fun buildQualifier(): ResourceQualifier? = parameters.first().paramValue?.let { qualifierFactory(it) }
}

/**
 * A [QualifierConfiguration] to build a [VersionQualifier]. The available versions are provided using a
 * [CollectionParam] which contains Api version from [SdkVersionInfo.LOWEST_ACTIVE_API] to [SdkVersionInfo.HIGHEST_KNOWN_API]
 */
internal class VersionQualifierConfiguration(version: Int) : QualifierConfiguration {
  override val parameters = listOf(
    CollectionParam((SdkVersionInfo.LOWEST_ACTIVE_API..SdkVersionInfo.HIGHEST_KNOWN_API)
                      .toSortedSet()
                      .reversed())
      .apply { paramValue = version }
  )

  override fun buildQualifier(): VersionQualifier? = parameters.first().paramValue?.let { VersionQualifier(it) }
}

/**
 * A [QualifierConfiguration] to build a [ScreenDimensionConfiguration] if both width and height are provided.
 */
internal class ScreenDimensionConfiguration(value1: Int, value2: Int) : QualifierConfiguration {
  private val widthParam = IntParam(SCREEN_SIZE_RANGE).apply { paramValue = value1 }
  private val heightParam = IntParam(SCREEN_SIZE_RANGE).apply { paramValue = value2 }
  override val parameters = listOf(widthParam, heightParam)
  override fun buildQualifier(): ScreenDimensionQualifier? {
    val width = widthParam.paramValue ?: return null
    val height = heightParam.paramValue ?: return null
    return ScreenDimensionQualifier(width, height)
  }
}
