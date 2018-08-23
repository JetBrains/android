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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.ATTR_STYLE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import com.android.tools.idea.common.scene.SceneManager

/**
 * Provider of default property values for NlComponent attributes.
 *
 * Layoutlib will generate a map from each view object to the
 * default value computed by each view class.
 * Currently this is only done for a few view classes example: TextView,
 * but this map could be more extensive in the future.
 */
class NeleDefaultPropertyProvider(sceneManager: SceneManager) {
  val properties: Map<Any?, Map<ResourceReference, ResourceValue>> = sceneManager.defaultProperties
  val styles: Map<Any?, String> = sceneManager.defaultStyles

  /**
   * Given a [property] return the default value found by layoutlib.
   *
   * Or `null` if such a default value hasn't been reported.
   */
  fun provideDefaultValue(property: NelePropertyItem): ResourceValue? {
    if (property.namespace.isEmpty() && property.name == ATTR_STYLE) {
      return provideDefaultStyleValue(property)
    }
    val namespace = ResourceNamespace.fromNamespaceUri(property.namespace) ?: return null
    val reference =  ResourceReference.attr(namespace, property.name)
    return property.components
      .map { properties[it.snapshot] }
      .map { it?.get(reference) }
      .distinct()
      .singleOrNull()
  }

  private fun provideDefaultStyleValue(property: NelePropertyItem): ResourceValue? {
    // TODO: Change the API of RenderResult.getDefaultStyles to return ResourceValues instead of Strings.
    val qualifiedStyle = property.components
                           .map { styles[it.snapshot] }
                           .distinct()
                           .singleOrNull() ?: return null
    val namespace = if (qualifiedStyle.startsWith("android:")) ResourceNamespace.ANDROID else ResourceNamespace.TODO()
    val style = "?" + qualifiedStyle.removePrefix("android:")
    val reference = ResourceReference.attr(namespace, ATTR_STYLE)
    return ResourceValueImpl(reference, style)
  }
}
