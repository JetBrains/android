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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants.ATTR_STYLE
import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.collect.HashBasedTable

/**
 * Provider of default property values for NlComponent attributes.
 *
 * Layoutlib will generate a map from each view object to the default value computed by each view
 * class. Currently this is only done for a few view classes example: TextView, but this map could
 * be more extensive in the future.
 */
class NlDefaultPropertyValueProvider(private val sceneManager: SceneManager) :
  DefaultPropertyValueProvider {
  // Store the default values requested so far. Use this to detect default value changes.
  @GuardedBy("lookupPerformed")
  private val lookupPerformed =
    HashBasedTable.create<NlComponent, ResourceReference, ResourceValue>()

  // Store the default style values requested so far. Use this to detect default value changes.
  @GuardedBy("styleLookupPerformed")
  private val styleLookupPerformed = mutableMapOf<NlComponent, ResourceReference>()

  override fun provideDefaultValue(property: NlPropertyItem): String? {
    val defValue = provideDefaultValueAsResourceValue(property) ?: return null
    return property.resolveDefaultValue(defValue)
  }

  /**
   * Given a [property] return the default value found by layoutlib.
   *
   * Or `null` if such a default value hasn't been reported.
   */
  private fun provideDefaultValueAsResourceValue(property: NlPropertyItem): ResourceValue? {
    if (property.namespace.isEmpty() && property.name == ATTR_STYLE) {
      return provideDefaultStyleValue(property)
    }
    val namespace = ResourceNamespace.fromNamespaceUri(property.namespace) ?: return null
    val reference = ResourceReference.attr(namespace, property.name)
    return property.components.map { lookup(it, reference) }.distinct().singleOrNull()
  }

  /**
   * Return true if any of the default values used so far has changed.
   *
   * Use this after layoutlib has finished rendering.
   */
  override fun hasDefaultValuesChanged(): Boolean {
    // This method is normally called from the Layoutlib Render thread which is the reason
    // we use synchronized blocks around every use of lookupPerformed and styleLookupPerformed.
    return hasDefaultPropertyValuesChanged() || hasDefaultStyleValuesChanged()
  }

  /** Clear the lookup tables used to detect default value changes. */
  override fun clearCache() {
    synchronized(lookupPerformed) { lookupPerformed.clear() }
    synchronized(styleLookupPerformed) { styleLookupPerformed.clear() }
  }

  private fun lookup(component: NlComponent, reference: ResourceReference): ResourceValue? {
    val valueMap =
      (sceneManager as? LayoutlibSceneManager)
        ?.renderResult
        ?.defaultProperties
        ?.get(component.snapshot) ?: return null
    val value = valueMap[reference] ?: return null
    synchronized(lookupPerformed) { lookupPerformed.put(component, reference, value) }
    return value
  }

  private fun hasDefaultPropertyValuesChanged(): Boolean {
    synchronized(lookupPerformed) {
      for (cell in lookupPerformed.cellSet()) {
        val valueMap =
          (sceneManager as? LayoutlibSceneManager)
            ?.renderResult
            ?.defaultProperties
            ?.get(cell.rowKey?.snapshot) ?: return true
        val value = valueMap[cell.columnKey] ?: return true
        if (cell.value?.equals(value) != true) {
          return true
        }
      }
      return false
    }
  }

  private fun provideDefaultStyleValue(property: NlPropertyItem): ResourceValue? {
    val style = property.components.map { styleLookup(it) }.distinct().singleOrNull() ?: return null
    val reference = ResourceReference.attr(style.namespace, ATTR_STYLE)
    return ResourceValueImpl(reference, "?" + style.name)
  }

  private fun hasDefaultStyleValuesChanged(): Boolean {
    synchronized(styleLookupPerformed) {
      for (entry in styleLookupPerformed) {
        val value =
          (sceneManager as? LayoutlibSceneManager)
            ?.renderResult
            ?.defaultStyles
            ?.get(entry.key.snapshot) ?: return true
        if (entry.value != value) {
          return true
        }
      }
      return false
    }
  }

  private fun styleLookup(component: NlComponent): ResourceReference? {
    val value =
      (sceneManager as? LayoutlibSceneManager)?.renderResult?.defaultStyles?.get(component.snapshot)
        ?: return null
    synchronized(styleLookupPerformed) { styleLookupPerformed[component] = value }
    return value
  }
}
