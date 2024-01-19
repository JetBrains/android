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
package com.android.tools.idea.actions

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ThemeStyleFilter
import com.android.tools.idea.editors.theme.ThemeResolver
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

class ThemeUtilsTest : AndroidTestCase() {

  fun testChangeTheme() {
    val project = myModule.project
    val facet = AndroidFacet.getInstance(myModule)
    assertNotNull(facet)
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotNull(manager)
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule))

    val configuration = Configuration.create(manager, FolderConfiguration())
    assertNotNull(configuration)

    val recentlyUsedThemes1 = getRecentlyUsedThemes(project)
    assertSize(0, recentlyUsedThemes1)

    addRecentlyUsedTheme(project, "@style/Theme1")

    val recentlyUsedThemes2 = getRecentlyUsedThemes(project)
    assertSize(1, recentlyUsedThemes2)
    assertEquals("@style/Theme1", recentlyUsedThemes2[0])

    // Check "do not add to recent used themes" case.
    configuration.startBulkEditing()
    configuration.setTheme("@style/Theme2")
    configuration.finishBulkEditing()

    val recentlyUsedThemes3 = getRecentlyUsedThemes(project)
    assertSize(1, recentlyUsedThemes3)
    assertEquals("@style/Theme1", recentlyUsedThemes3[0])

    // Check add another theme. The new theme should be the first one in the order.
    addRecentlyUsedTheme(project, "@style/Theme3")

    val recentlyUsedThemes4 = getRecentlyUsedThemes(project)
    assertSize(2, recentlyUsedThemes4)
    assertEquals("@style/Theme3", recentlyUsedThemes4[0])
    assertEquals("@style/Theme1", recentlyUsedThemes4[1])

    // Set the exist used theme should bring it to the first (The most recently used one)
    addRecentlyUsedTheme(project, "@style/Theme1")

    val recentUsedThemes5 = getRecentlyUsedThemes(project)
    assertSize(2, recentUsedThemes5)
    assertEquals("@style/Theme1", recentUsedThemes5[0])
    assertEquals("@style/Theme3", recentUsedThemes5[1])

    // Check the maximum size of recentUsedThemes is same as ThemeUtils.MAX_RECENTLY_USED_THEMES.
    // And the other older themes should be dropped.
    for (i in 0..4) {
      addRecentlyUsedTheme(project, "@style/NewTheme$i")
    }

    val recentUsedThemes6 = getRecentlyUsedThemes(project)
    assertSize(5, recentUsedThemes6)
    for (i in 0..4) {
      assertEquals("@style/NewTheme${4 - i}", recentUsedThemes6[i])
    }
  }

  fun testGetFrameworkThemesWithFilter() {
    val layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    val configuration =
      ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile)
    val themeResolver = ThemeResolver(configuration)
    val reference = ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Black")
    val style = configuration.resourceResolver.getStyle(reference)!!
    val filter: ThemeStyleFilter = createFilter(themeResolver, emptySet(), *Array(1) { style })
    val themes = getFrameworkThemeNames(themeResolver, filter)
    assertThat(themes)
      .containsExactly(
        "android:Theme.Black.NoTitleBar",
        "android:Theme.Black.NoTitleBar.Fullscreen",
      )
  }
}
