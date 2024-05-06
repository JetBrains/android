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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.resources.ResourceType
import org.jetbrains.annotations.TestOnly
import kotlin.properties.Delegates

/**
 * Options to filter the resources shown in the resource explorer
 */
class FilterOptions internal constructor(
  private val refreshResourcesCallback: () -> Unit = {},
  private val searchStringChanged: (String) -> Unit = {},
  moduleDependenciesInitialValue: Boolean,
  librariesInitialValue: Boolean,
  val isShowSampleData: Boolean,
  showAndroidResources: Boolean,
  showThemeAttributes: Boolean) {

  /**
   * Model for filters by type. Eg: To filter Drawables by 'vector-drawables' files.
   *
   * @see TypeFilter
   */
  val typeFiltersModel = TypeFiltersModel().apply { valueChangedCallback = refreshResourcesCallback }

  /**
   * If true, the resources from the dependent modules will be shown.
   */
  var isShowModuleDependencies: Boolean
    by Delegates.observable(moduleDependenciesInitialValue) { _, old, new -> if (new != old) refreshResourcesCallback() }

  /**
   * If true, the resources from the dependent libraries will be shown.
   */
  var isShowLibraries: Boolean
    by Delegates.observable(librariesInitialValue) { _, old, new -> if (new != old) refreshResourcesCallback() }

  /**
   * If true, the Android Framework resources will be shown.
   */
  var isShowFramework: Boolean
    by Delegates.observable(showAndroidResources) { _, old, new -> if (new != old) refreshResourcesCallback() }

  /**
   * If true, Theme Attributes resources will be shown.
   */
  var isShowThemeAttributes: Boolean
    by Delegates.observable(showThemeAttributes) { _, old, new -> if (new != old) refreshResourcesCallback() }

  /**
   * The search string to use to filter resources
   */
  var searchString: String by Delegates.observable("") { _, old, new -> if (new != old) searchStringChanged(new) }

  companion object {
    /** Helper function to instantiate [FilterOptions] with an initial state. */
    fun create(isShowResourcesChanged: () -> Unit,
               searchStringChanged: (String) -> Unit,
               initialParams: FilterOptionsParams)
      = FilterOptions(isShowResourcesChanged,
                      searchStringChanged,
                      moduleDependenciesInitialValue =  initialParams.moduleDependenciesInitialValue,
                      librariesInitialValue = initialParams.librariesInitialValue,
                      isShowSampleData = initialParams.showSampleData,
                      showAndroidResources = initialParams.androidResourcesInitialValue,
                      showThemeAttributes = initialParams.themeAttributesInitialValue)

    /** Instantiate [FilterOptions] with no callback implementation and with all values initialized as false. */
    @TestOnly
    fun createDefault() = FilterOptions(moduleDependenciesInitialValue = false,
                                        librariesInitialValue = false,
                                        isShowSampleData = false,
                                        showAndroidResources = false,
                                        showThemeAttributes = false)
  }
}

/** Params to define the initial state of [FilterOptions]. */
data class FilterOptionsParams(
  val moduleDependenciesInitialValue: Boolean,
  val librariesInitialValue: Boolean,
  val showSampleData: Boolean,
  val androidResourcesInitialValue: Boolean,
  val themeAttributesInitialValue: Boolean
)

/**
 * Describes the filter options available for a particular [ResourceType]. The state of each filter option can be set as disabled or enabled
 * and it will be preserved for the duration of the instantiated model. Every filter option is initialized to 'false/disabled' by default.
 *
 * Set the callback function [valueChangedCallback] to react to changes made to the model.
 */
class TypeFiltersModel {

  /** Callback function, called when a filter options changes value. */
  var valueChangedCallback: () -> Unit = {}

  private val resourceTypeToFilter = getTypeFiltersMap()

  /**
   * For the given [type], returns a list of the supported [TypeFilter]s.
   */
  fun getSupportedFilters(type: ResourceType) = resourceTypeToFilter[type]?.keys?.toList() ?: emptyList<TypeFilter>()

  /**
   * For the given [type], returns the list of [TypeFilter] which are currently enabled (set to true).
   */
  fun getActiveFilters(type: ResourceType) = resourceTypeToFilter[type]?.filter { it.value }?.keys?.toList() ?: emptyList()

  /**
   * The current state for the given [TypeFilter], returns false if it doesn't exist for the given [ResourceType].
   */
  fun isEnabled(type: ResourceType, filter: TypeFilter): Boolean = resourceTypeToFilter[type]?.get(filter) ?: false

  /**
   * Set the state for a given [TypeFilter] if it exists for the given [ResourceType]. If it results in value change Eg: false ->
   * true. Triggers the [valueChangedCallback] function.
   */
  fun setEnabled(type: ResourceType, filter: TypeFilter, value: Boolean) {
    resourceTypeToFilter[type]?.let { filters ->
      filters[filter]?.let { old ->
        if (old != value) {
          filters[filter] = value
          valueChangedCallback()
        }
      }
    }
  }

  /**
   * Sets all the [TypeFilter]s under the given [ResourceType] to false (ie: disabled).
   */
  fun clearAll(type: ResourceType) {
    resourceTypeToFilter[type]?.let { filters ->
      filters.replaceAll { _, _ -> false }
      valueChangedCallback()
    }
  }
}

private const val BITMAP_FILTER_NAME = "Bitmap (webp, png, etc...)"
private const val NINE_PATCH_FILTER_NAME = "9-Patch"
private const val FONTFILE_FILTER_NAME = "Font File (ttf, ttc, otf)"
private const val CONSTRAINT_FILTER_NAME = "ConstraintLayout"
private const val MOTION_FILTER_NAME = "MotionLayout"

private fun getTypeFiltersMap(): Map<ResourceType, LinkedHashMap<TypeFilter, Boolean>> {
  return mapOf<ResourceType, LinkedHashMap<TypeFilter, Boolean>>(
    Pair(ResourceType.DRAWABLE, linkedMapOf(
      xmlFilterType(SdkConstants.TAG_VECTOR),
      xmlFilterType(SdkConstants.TAG_ANIMATED_VECTOR),
      xmlFilterType(SdkConstants.TAG_SELECTOR),
      xmlFilterType(SdkConstants.TAG_ANIMATED_SELECTOR),
      xmlFilterType(SdkConstants.TAG_SHAPE),
      fileFilterType(".compiled${SdkConstants.DOT_9PNG}", NINE_PATCH_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_9PNG, NINE_PATCH_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_WEBP, BITMAP_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_PNG, BITMAP_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_JPG, BITMAP_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_GIF, BITMAP_FILTER_NAME),
      xmlFilterType(SdkConstants.TAG_RIPPLE),
      xmlFilterType(SdkConstants.TAG_INSET),
      xmlFilterType(SdkConstants.TAG_LAYER_LIST),
      xmlFilterType(SdkConstants.TAG_LEVEL_LIST))),
    Pair(ResourceType.COLOR, linkedMapOf(xmlFilterType(SdkConstants.TAG_SELECTOR))),
    Pair(ResourceType.LAYOUT, linkedMapOf(
      xmlFilterType(SdkConstants.TAG_LAYOUT, "Data Binding"),
      xmlFilterType(AndroidXConstants.CONSTRAINT_LAYOUT.oldName(), CONSTRAINT_FILTER_NAME),
      xmlFilterType(AndroidXConstants.CONSTRAINT_LAYOUT.newName(), CONSTRAINT_FILTER_NAME),
      xmlFilterType(AndroidXConstants.MOTION_LAYOUT.oldName(), MOTION_FILTER_NAME),
      xmlFilterType(AndroidXConstants.MOTION_LAYOUT.newName(), MOTION_FILTER_NAME))),
    Pair(ResourceType.MIPMAP, linkedMapOf(
      xmlFilterType(SdkConstants.TAG_ADAPTIVE_ICON),
      fileFilterType(SdkConstants.DOT_WEBP, BITMAP_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_PNG, BITMAP_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_JPG, BITMAP_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_GIF, BITMAP_FILTER_NAME))),
    Pair(ResourceType.FONT, linkedMapOf(
      xmlFilterType(SdkConstants.TAG_FONT_FAMILY),
      fileFilterType(SdkConstants.DOT_TTF, FONTFILE_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_TTC, FONTFILE_FILTER_NAME),
      fileFilterType(SdkConstants.DOT_OTF, FONTFILE_FILTER_NAME)))
  )
}

private fun linkedMapOf(vararg typeFilterPairs: Pair<TypeFilter, Boolean>): LinkedHashMap<TypeFilter, Boolean> {
  return LinkedHashMap<TypeFilter, Boolean>().apply {
    putAll(typeFilterPairs)
  }
}

private fun xmlFilterType(value: String, displayName: String = value) = Pair(TypeFilter(TypeFilterKind.XML_TAG, value, displayName), false)
private fun fileFilterType(value: String, displayName: String = value) = Pair(TypeFilter(TypeFilterKind.FILE, value, displayName), false)

/**
 * Represents how a resource should be filtered and what value to use for filtering.
 *
 * Eg: A [TypeFilter] defined by [kind] = [TypeFilterKind.XML_TAG] and [value] = `vector` is a filter that looks for the
 * `vector` root tag in an XML resource file.
 */
data class TypeFilter(val kind: TypeFilterKind, val value: String, val displayName: String = value)

/**
 * Describes the type of filtering to apply to the resource.
 */
enum class TypeFilterKind {
  /** Filter by the resource's XML root tag. */
  XML_TAG,
  /** Filter by the resource's file extension. */
  FILE
}