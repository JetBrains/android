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
package com.android.tools.idea.uibuilder.property.support

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceValueMap
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DerivedStyleFinderTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private val android = ResourceNamespace.ANDROID
  private val auto = ResourceNamespace.RES_AUTO
  private var finder: DerivedStyleFinder? = null
  private val theme =
    ResourceUrl.parseStyleParentReference("AppTheme")!!.resolve(
      ResourceNamespace.TODO(),
      ResourceNamespace.Resolver.EMPTY_RESOLVER,
    )
  private var resolver: ResourceResolver? =
    ResourceResolver.create(
      mapOf(
        auto to
          mapOf(
            mkResourceValueMapPair(
              mkStyleResourceValue(auto, "TextAppearance", "TextAppearance.AppCompat.Body1", ""),
              mkStyleResourceValue(auto, "Text34", "android:TextAppearance.Material", ""),
              mkStyleResourceValue(auto, "Text2", "Text34", ""),
              mkStyleResourceValue(auto, "AppTheme", "Theme.AppCompat", ""),
              mkStyleResourceValue(auto, "Theme.AppCompat", "android:Theme", "appcompat"),
              mkStyleResourceValue(
                auto,
                "Base.TextAppearance.AppCompat",
                "android:TextAppearance.Material",
                "appcompat",
              ),
              mkStyleResourceValue(
                auto,
                "TextAppearance.AppCompat",
                "android:TextAppearance.Material",
                "appcompat",
              ),
              mkStyleResourceValue(
                auto,
                "TextAppearance.AppCompat.Body1",
                "TextAppearance.AppCompat",
                "appcompat",
              ),
              mkStyleResourceValue(
                auto,
                "TextAppearance.AppCompat.Body2",
                "TextAppearance.AppCompat",
                "appcompat",
              ),
              mkStyleResourceValue(
                auto,
                "Widget.AppCompat.TextView",
                "android:Widget.Material.TextView",
                "appcompat",
              ),
              mkStyleResourceValue(
                auto,
                "Widget.AppCompat.TextView.SpinnerItem",
                "Widget.AppCompat.TextView",
                "appcompat",
              ),
              mkStyleResourceValue(auto, "Theme.Design", "Theme.AppCompat", "design"),
              mkStyleResourceValue(auto, "Theme.Design.NoActionBar", "Theme.AppCompat", "design"),
            )
          ),
        android to
          mapOf(
            mkResourceValueMapPair(
              mkStyleResourceValue(android, "Theme", "", ""),
              mkStyleResourceValue(android, "Widget.TextView", "", ""),
              mkStyleResourceValue(
                android,
                "Widget.Material.TextView",
                "android:Widget.TextView",
                "",
              ),
              mkStyleResourceValue(android, "TextAppearance", "", ""),
              mkStyleResourceValue(
                android,
                "internal.textappearance",
                "android:TextAppearance",
                "",
              ),
              mkStyleResourceValue(
                android,
                "TextAppearance.DeviceDefault",
                "android:TextAppearance",
                "",
              ),
              mkStyleResourceValue(
                android,
                "TextAppearance.Material",
                "android:TextAppearance",
                "",
              ),
              mkStyleResourceValue(
                android,
                "TextAppearance.Material.Body1",
                "android:TextAppearance.Material",
                "",
              ),
              mkStyleResourceValue(
                android,
                "TextAppearance.Material.Body2",
                "android:TextAppearance.Material",
                "",
              ),
            )
          ),
      ),
      theme,
    )

  @Before
  fun setUp() {
    finder = DerivedStyleFinder(AndroidFacet.getInstance(projectRule.module)!!, resolver)
  }

  @After
  fun tearDown() {
    finder = null
    resolver = null
  }

  @Test
  fun testTextAppearances() {
    val textAppearanceStyle = resolve(android, "TextAppearance")
    val styles =
      finder?.find(textAppearanceStyle, { true }, { style -> style.name })
        ?: failure("Missing filter")
    Truth.assertThat(styles)
      .named(dump(styles))
      .containsExactly(
        resolve(auto, "Text2"),
        resolve(auto, "Text34"),
        resolve(auto, "TextAppearance"),
        resolve(auto, "TextAppearance.AppCompat"),
        resolve(auto, "TextAppearance.AppCompat.Body1"),
        resolve(auto, "TextAppearance.AppCompat.Body2"),
        resolve(android, "TextAppearance"),
        resolve(android, "TextAppearance.DeviceDefault"),
        resolve(android, "TextAppearance.Material"),
        resolve(android, "TextAppearance.Material.Body1"),
        resolve(android, "TextAppearance.Material.Body2"),
      )
      .inOrder()
  }

  @Test
  fun testAppCompatThemes() {
    val appCompat = resolve(auto, "Theme.AppCompat")
    val styles =
      finder?.find(appCompat, { true }, { style -> style.name }) ?: failure("Missing filter")
    Truth.assertThat(styles)
      .named(dump(styles))
      .containsExactly(
        resolve(auto, "AppTheme"),
        resolve(auto, "Theme.AppCompat"),
        resolve(auto, "Theme.Design"),
        resolve(auto, "Theme.Design.NoActionBar"),
      )
      .inOrder()
  }

  private fun dump(styles: List<StyleResourceValue>): String {
    val sb = StringBuilder("order is as specified. Actual order:\n")
    for (index in styles.indices) {
      val name = styles[index].name
      val ns = if (styles[index].namespace == ResourceNamespace.ANDROID) "android" else "auto"
      sb.append("    resolve($ns, \"$name\")\n")
    }
    sb.append("\n")
    return sb.toString()
  }

  private fun resolve(namespace: ResourceNamespace, name: String): StyleResourceValue {
    val reference = ResourceReference(namespace, ResourceType.STYLE, name)
    return resolver?.getStyle(reference) ?: failure("$name not found!")
  }

  private fun failure(message: String): Nothing {
    throw RuntimeException(message)
  }

  private fun mkResourceValueMapPair(
    vararg styles: StyleResourceValue
  ): Pair<ResourceType, ResourceValueMap> {
    return Pair(
      ResourceType.STYLE,
      ResourceValueMap.create().apply {
        for (style in styles) {
          put(style)
        }
      },
    )
  }

  private fun mkStyleResourceValue(
    namespace: ResourceNamespace,
    name: String,
    parentStyle: String,
    library: String,
  ): StyleResourceValue {
    return StyleResourceValueImpl(
      ResourceReference(namespace, ResourceType.STYLE, name),
      parentStyle,
      library,
    )
  }
}
