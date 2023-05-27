/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.plugin.LayoutRenderer
import com.android.tools.idea.util.androidFacet
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ui.ImageUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Language("XML")
private const val NAVIGATION_WITH_PREVIEW = """
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/my_nav_graph"
    app:startDestination="@id/placeholder">

    <fragment android:id="@+id/placeholder"
      tools:layout="@layout/my_layout"/>
</navigation>
"""

@Language("XML")
private const val NAVIGATION_NO_PREVIEW = """
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/my_nav_graph"
    app:startDestination="@id/placeholder">

    <fragment android:id="@+id/placeholder" />
</navigation>
"""

@Language("XML")
private const val LAYOUT_CONTENTS = """
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
</androidx.constraintlayout.widget.ConstraintLayout>
"""

class NavigationSlowPreviewProviderTest {

  @get:Rule
  val rule = AndroidProjectRule.onDisk()

  private lateinit var facet: AndroidFacet

  @Before
  fun setup() {
    facet = rule.module.androidFacet!!
  }

  @Test
  fun navigationPreview() {
    val layoutFile = rule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_CONTENTS)
    val navigationFile = rule.fixture.addFileToProject("res/navigation/my_graph.xml", NAVIGATION_WITH_PREVIEW)

    val configuration = ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(layoutFile.virtualFile)

    setupRenderer(layoutFile as XmlFile, configuration)

    val navigationPreviewProvider = NavigationSlowPreviewProvider(facet, configuration.resourceResolver)

    val navigationAsset = DesignAsset(navigationFile.virtualFile, emptyList(), ResourceType.NAVIGATION)

    val navigationPreview = navigationPreviewProvider.getSlowPreview(100, 100, navigationAsset)

    assertNotNull(navigationPreview)
    assertEquals(0xFFFF0000.toInt(), navigationPreview.getRGB(25, 50))
  }

  @Test
  fun noLayoutAttribute() {
    val navigationFile = rule.fixture.addFileToProject("res/navigation/my_graph.xml", NAVIGATION_NO_PREVIEW)

    val configuration = ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(navigationFile.virtualFile)

    val navigationPreviewProvider = NavigationSlowPreviewProvider(facet, configuration.resourceResolver)

    val navigationAsset = DesignAsset(navigationFile.virtualFile, emptyList(), ResourceType.NAVIGATION)

    val navigationPreview = navigationPreviewProvider.getSlowPreview(100, 100, navigationAsset)

    assertNotNull(navigationPreview)
    assertNotEquals(0xFFFF0000.toInt(), navigationPreview.getRGB(25, 50))
  }

  private fun setupRenderer(xmlFile: XmlFile, configuration: Configuration) {
    val mockRenderer = Mockito.mock(LayoutRenderer::class.java)
    val mockImage = ImageUtil.createImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      val g = createGraphics()
      g.color = Color.RED
      g.fillRect(0, 0, 100, 100)
      g.dispose()
    }
    whenever(mockRenderer.getLayoutRender(xmlFile, configuration)).thenReturn(CompletableFuture.completedFuture(mockImage))
    LayoutRenderer.setInstance(rule.module.androidFacet!!, mockRenderer)
  }
}