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
package com.android.tools.idea.resourceExplorer.sketchImporter

import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.SketchParser
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.SketchImporterPresenter
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.SketchImporterView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.AndroidTestBase
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.awt.event.ItemEvent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresenterTest {

  companion object {
    @ClassRule
    @JvmField
    val projectRule = AndroidProjectRule.onDisk()
  }

  @Before
  fun setUp() {
    val dir = projectRule.fixture.tempDirFixture.findOrCreateDir("res")
    dir.children.forEach { it.delete(this) }
  }

  @Test
  fun importFiles() {
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/presenter.sketch")!!
    val view = SketchImporterView()
    val presenter = SketchImporterPresenter(view, sketchFile, DesignAssetImporter(), projectRule.module.androidFacet!!)
    presenter.filterExportable(ItemEvent.DESELECTED)
    presenter.importFilesIntoProject()

    val resourceFolder = projectRule.fixture.tempDirFixture.findOrCreateDir("res").findChild("drawable-anydpi")
    val items = resourceFolder!!.children

    var content = String(items[0].contentsToByteArray())
    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"958.0dp\" android:width=\"958.0dp\" android:viewportHeight=\"958.0\" android:viewportWidth=\"958.0\"><path android:pathData=\"M483,529 L427.75,558.05 L438.3,496.52 L393.6,452.95 L455.37,443.98 L483,388 L510.63,443.98 L572.4,452.95 L527.7,496.52 L538.25,558.05 C538.25,558.05 483,529 483,529 \" android:strokeColor=\"#ff5c0000\" android:strokeWidth=\"1\" android:fillColor=\"#ffff0000\"/></vector>",
      content)

    content = String(items[1].contentsToByteArray())
    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"702.0dp\" android:width=\"702.0dp\" android:viewportHeight=\"702.0\" android:viewportWidth=\"702.0\"><path android:pathData=\"M360.5,461.25 L251.47,518.57 L272.29,397.16 L184.08,311.18 L305.98,293.46 L360.5,183 L415.02,293.46 L536.92,311.18 L448.71,397.16 L469.53,518.57 C469.53,518.57 360.5,461.25 360.5,461.25 \" android:strokeColor=\"#ff827901\" android:strokeWidth=\"1\" android:fillColor=\"#fff8e71c\"/></vector>",
      content)

    content = String(items[2].contentsToByteArray())
    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"730.0dp\" android:width=\"730.0dp\" android:viewportHeight=\"730.0\" android:viewportWidth=\"730.0\"><path android:pathData=\"M398.79,151.66 L398.79,151.66 L398.79,151.66 zM379.84,270 L388.34,292.43 L408.4,279.28 L409.56,303.24 L432.7,296.94 L426.4,320.08 L450.35,321.23 L437.21,341.3 L459.64,349.8 L440.93,364.82 L459.64,379.84 L437.21,388.34 L450.35,408.4 L426.4,409.56 L432.7,432.7 L432.7,432.7 L409.56,426.4 L408.4,450.35 L388.34,437.21 L379.84,459.64 C379.84,459.64 364.82,440.93 364.82,440.93 L349.8,459.64 L341.3,437.21 L321.23,450.35 L320.08,426.4 L296.94,432.7 L296.94,432.7 L303.24,409.56 L279.28,408.4 L292.43,388.34 L270,379.84 L288.7,364.82 L270,349.8 L292.43,341.3 L279.28,321.23 L303.24,320.08 L296.94,296.94 L320.08,303.24 L321.23,279.28 L341.3,292.43 L349.8,270 L364.82,288.7 L379.84,270 zM331.21,151.66 L312.08,202.12 L266.94,172.54 L264.34,226.45 L212.26,212.26 L226.45,264.34 L172.54,266.94 L202.12,312.08 L151.66,331.21 L193.74,365 L151.66,398.79 L202.12,417.92 L172.54,463.06 L226.45,465.66 L212.26,517.74 L264.34,503.55 L266.94,557.46 L312.08,527.88 L331.21,578.34 L365,536.26 C365,536.26 398.79,578.34 398.79,578.34 L417.92,527.88 L463.06,557.46 L465.66,503.55 L517.74,517.74 L517.74,517.74 L503.55,465.66 L557.46,463.06 L527.88,417.92 L578.34,398.79 L536.26,365 L578.34,331.21 L527.88,312.08 L557.46,266.94 L503.55,264.34 L517.74,212.26 L517.74,212.26 L465.66,226.45 L463.06,172.54 L417.92,202.12 L398.79,151.66 L365,193.74 L331.21,151.66 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff7ed321\"/></vector>",
      content)

    Disposer.dispose(view)
  }

  @Test
  fun noExportableFiles() {
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/presenter.sketch")!!
    val view = SketchImporterView()
    val presenter = SketchImporterPresenter(view, sketchFile, DesignAssetImporter(), projectRule.module.androidFacet!!)
    presenter.filterExportable(ItemEvent.SELECTED)
    presenter.importFilesIntoProject()

    val resourceFolder = projectRule.fixture.tempDirFixture.findOrCreateDir("res").findChild("drawable-anydpi")
    val items = resourceFolder?.children

    assertTrue(items?.isEmpty() ?: true)

    Disposer.dispose(view)
  }
}