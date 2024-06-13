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
package com.android.tools.idea.uibuilder.analytics

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.type.DefaultDesignerFileType
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class NlAnalyticsManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var analyticsManager: NlAnalyticsManager

  private lateinit var surface: NlDesignSurface

  @Before
  fun setUp() {
    surface = mock(NlDesignSurface::class.java)
    whenever(surface.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER_AND_BLUEPRINT)
    analyticsManager = NlAnalyticsManager(surface)
  }

  @Test
  fun testBasicTracking() {
    analyticsManager.trackAlign()
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent)
      .isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ALIGN)

    analyticsManager.trackToggleAutoConnect(true)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent)
      .isEqualTo(LayoutEditorEvent.LayoutEditorEventType.TURN_ON_AUTOCONNECT)

    analyticsManager.trackToggleAutoConnect(false)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent)
      .isEqualTo(LayoutEditorEvent.LayoutEditorEventType.TURN_OFF_AUTOCONNECT)
  }

  @Test
  fun testLayoutType() {
    whenever(surface.layoutType).thenReturn(DefaultDesignerFileType)
    assertThat(analyticsManager.layoutType)
      .isEqualTo(LayoutEditorState.Type.UNKNOWN_TYPE) // By default, we don't infer any types

    whenever(surface.layoutType).thenReturn(LayoutFileType)
    assertThat(analyticsManager.layoutType).isEqualTo(LayoutEditorState.Type.LAYOUT)

    whenever(surface.layoutType).thenReturn(AnimatedVectorFileType)
    assertThat(analyticsManager.layoutType).isEqualTo(LayoutEditorState.Type.DRAWABLE)
  }

  @Test
  fun testSurfaceType() {
    assertThat(analyticsManager.surfaceType)
      .isEqualTo(LayoutEditorState.Surfaces.BOTH) // Set in setup

    whenever(surface.screenViewProvider).thenReturn(NlScreenViewProvider.BLUEPRINT)
    assertThat(analyticsManager.surfaceType).isEqualTo(LayoutEditorState.Surfaces.BLUEPRINT_SURFACE)
  }

  @Test
  fun testSurfaceMode() {
    analyticsManager.setEditorModeWithoutTracking(DesignerEditorPanel.State.FULL)
    assertThat(analyticsManager.editorMode).isEqualTo(LayoutEditorState.Mode.DESIGN_MODE)

    analyticsManager.setEditorModeWithoutTracking(DesignerEditorPanel.State.SPLIT)
    // Split mode is mapped to PREVIEW_MODE when using the split editor
    assertThat(analyticsManager.editorMode).isEqualTo(LayoutEditorState.Mode.PREVIEW_MODE)
  }

  @Test
  fun testEditorFileTypeKotlin() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val kotlinFile =
      fixture.addFileToProject(
        "Test.kt",
        // language=kotlin
        """
        fun someFun() {
        }
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(kotlinFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.KOTLIN)
  }

  @Test
  fun testEditorFileTypeKotlinCompose() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    val kotlinFile =
      fixture.addFileToProject(
        "Test.kt",
        // language=kotlin
        """
        fun someFun() {
        }
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(kotlinFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.KOTLIN_COMPOSE)
  }

  @Test
  fun testEditorFileTypeJava() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val javaFile =
      fixture.addFileToProject(
        "Test.java",
        // language=java
        """
        class Test {
        }
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(javaFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.JAVA)
  }

  @Test
  fun testEditorFileTypeLayout() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val layoutFile =
      fixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
        <LinearLayout />
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(layoutFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.XML_RES_LAYOUT)
  }

  @Test
  fun testEditorFileTypeDrawable() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val drawableFile =
      fixture.addFileToProject(
        "res/drawable/drawable.xml",
        // language=XML
        """
        <vector />
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(drawableFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.XML_RES_DRAWABLE)
  }

  @Test
  fun testEditorFileTypeDrawableWithExtraIdentifier() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val drawableFile =
      fixture.addFileToProject(
        "res/drawable-hd/drawable.xml",
        // language=XML
        """
        <vector />
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(drawableFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.XML_RES_DRAWABLE)
  }

  @Test
  fun testEditorFileTypeAnim() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val animFile =
      fixture.addFileToProject(
        "res/anim/animated_vector.xml",
        // language=XML
        """
        <animated-vector />
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(animFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.XML_RES_ANIM)
  }

  @Test
  fun testEditorFileTypeFont() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val fontFile =
      fixture.addFileToProject(
        "res/font/some_font.xml",
        // language=XML
        """
        <font-family />
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(fontFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.XML_RES_FONT)
  }

  @Test
  fun testEditorFileTypeRaw() {
    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.UNKNOWN)

    val rawFile =
      fixture.addFileToProject(
        "res/raw/some_font.xml",
        // language=XML
        """
        <item />
      """
          .trimIndent(),
      )
    analyticsManager.setEditorFileTypeWithoutTracking(rawFile.virtualFile, project)

    assertThat(analyticsManager.editorFileType).isEqualTo(EditorFileType.XML_RES_RAW)
  }
}
