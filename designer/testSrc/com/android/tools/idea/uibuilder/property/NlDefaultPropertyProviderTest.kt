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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.component
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.createComponents
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.createPropertyItem
import com.android.tools.idea.uibuilder.property.testutils.MinApiRule
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class NlDefaultPropertyProviderTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule val chain = RuleChain.outerRule(projectRule).around(MinApiRule(projectRule))!!

  @Test
  fun testAttributeWithoutDefaultValue() {
    val components = createComponents(projectRule, component(TEXT_VIEW))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val defaultProvider =
      NlDefaultPropertyValueProvider(property.model.surface!!.focusedSceneView!!.sceneManager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  @Test
  fun testAttributeWithDefaultValue() {
    val components = createComponents(projectRule, component(TEXT_VIEW))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceSmall",
    )
    val defaultProvider = NlDefaultPropertyValueProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isEqualTo("@android:style/TextAppearance.Material.Small")
  }

  @Test
  fun testMultipleComponentsWithDifferentDefaultValues() {
    val components = createComponents(projectRule, component(TEXT_VIEW), component(BUTTON))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceSmall",
    )
    manager.putDefaultPropertyValue(
      components[1],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceLarge",
    )
    val defaultProvider = NlDefaultPropertyValueProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  @Test
  fun testMultipleComponentsWithSomeMissingDefaultValues() {
    val components = createComponents(projectRule, component(TEXT_VIEW), component(BUTTON))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceSmall",
    )
    val defaultProvider = NlDefaultPropertyValueProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  @Test
  fun testMultipleComponentsWithIdenticalDefaultValues() {
    val components = createComponents(projectRule, component(TEXT_VIEW), component(BUTTON))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceLarge",
    )
    manager.putDefaultPropertyValue(
      components[1],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceLarge",
    )
    val defaultProvider = NlDefaultPropertyValueProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isEqualTo("@android:style/TextAppearance.Material.Large")
  }

  @Test
  fun testMultipleComponentsWithOneMissingSnapshot() {
    val components = createComponents(projectRule, component(TEXT_VIEW), component(BUTTON))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceLarge",
    )
    components[1].snapshot = null
    val defaultProvider = NlDefaultPropertyValueProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  @Test
  fun testDefaultChanged() {
    val components = createComponents(projectRule, component(TEXT_VIEW))
    val property =
      createPropertyItem(
        projectRule,
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        components,
      )
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceSmall",
    )
    val defaultProvider = NlDefaultPropertyValueProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isEqualTo("@android:style/TextAppearance.Material.Small")
    manager.putDefaultPropertyValue(
      components[0],
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceLarge",
    )
    assertThat(defaultProvider.hasDefaultValuesChanged()).isTrue()
  }

  private fun getSceneManager(property: NlPropertyItem): SyncLayoutlibSceneManager {
    return property.model.surface!!.focusedSceneView!!.sceneManager as SyncLayoutlibSceneManager
  }
}
