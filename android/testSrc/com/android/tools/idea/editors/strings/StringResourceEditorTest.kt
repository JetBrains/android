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
package com.android.tools.idea.editors.strings

import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.res.ResourceNotificationManager.ResourceVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLoadingPanelListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.awt.Font
import kotlin.test.fail


/** Tests for the [StringResourceEditor] class. */
@RunWith(JUnit4::class)
class StringResourceEditorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val myFlagRule = FlagRule(StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION)

  private val font = Font(Font.DIALOG, Font.PLAIN, 12)
  private val oldScale = JBUIScale.scale(1.0f)
  private val resourceVersion1: ResourceVersion = mock()
  private val resourceVersion2: ResourceVersion = mock()
  private val resourceVersion3: ResourceVersion = mock()
  private var currentResourceVersion: ResourceVersion = resourceVersion1
  private val listeners: MutableList<ResourceChangeListener> = mutableListOf()
  private lateinit var stringsVirtualFile: StringsVirtualFile
  private lateinit var facet: AndroidFacet
  private lateinit var editor: StringResourceEditor
  private lateinit var resourceNotificationManager: ResourceNotificationManager
  private var reloadsStarted = 0
  private var reloadsFinished = 0

  @Before
  fun setUp() {
    facet = projectRule.module.androidFacet!!
    resourceNotificationManager = projectRule.mockProjectService(ResourceNotificationManager::class.java)
    doAnswer {
      currentResourceVersion
    }.whenever(resourceNotificationManager).getCurrentVersion(any(), isNull(), isNull())

    doAnswer {
      listeners.add(it.getArgument(0))
      currentResourceVersion
    }.whenever(resourceNotificationManager).addListener(any(), eq(facet), isNull(), isNull())

    doAnswer {
      listeners.remove(it.getArgument(0))
    }.whenever(resourceNotificationManager).removeListener(any(), eq(facet) , isNull(), isNull())

    JBUIScale.setUserScaleFactor(2.0f)
    stringsVirtualFile = StringsVirtualFile.getStringsVirtualFile(projectRule.module)!!
    editor = StringResourceEditor(stringsVirtualFile)
    verify(resourceNotificationManager).getCurrentVersion(eq(facet), isNull(), isNull())
    Disposer.register(projectRule.fixture.testRootDisposable, editor);

    editor.panel.loadingPanel.addListener(
      object: JBLoadingPanelListener {
        override fun onLoadingStart() { reloadsStarted++ }
        override fun onLoadingFinish() { reloadsFinished++ }
      }
    )

  }

  @After
  fun tearDown() {
    JBUIScale.setUserScaleFactor(oldScale);
  }

  @Test
  fun file() {
    assertThat(editor.file).isEqualTo(stringsVirtualFile)
  }

  @Test
  fun panelCreated() {
    assertThat(editor.panel).isNotNull()
    assertThat(editor.panel.facet).isEqualTo(facet)
  }

  @Test
  fun component() {
    assertThat(editor.component).isEqualTo(editor.panel.loadingPanel)
  }

  @Test
  fun preferredFocusedComponent() {
    assertThat(editor.preferredFocusedComponent).isEqualTo(editor.panel.preferredFocusedComponent)
  }

  @Test
  fun state() {
    FileEditorStateLevel.values().forEach {
      assertThat(editor.getState(it)).isSameAs(FileEditorState.INSTANCE)
    }

    // Setting the state should do nothing.
    editor.setState { _, _ -> fail("Should never be called") }

    FileEditorStateLevel.values().forEach {
      assertThat(editor.getState(it)).isSameAs(FileEditorState.INSTANCE)
    }
  }

  @Test
  fun isModified() {
    assertThat(editor.isModified).isFalse()
  }

  @Test
  fun isValid() {
    assertThat(editor.isValid).isTrue()
  }

  @Test
  fun backgroundHighlighter() {
    assertThat(editor.backgroundHighlighter).isNull()
  }

  @Test
  fun currentLocation() {
    assertThat(editor.currentLocation).isNull()
  }

  @Test
  fun structureViewBuilder() {
    assertThat(editor.structureViewBuilder).isNull()
  }
  @Test
  fun toStringCorrect() {
    assertThat(editor.toString())
      .isEqualTo("StringResourceEditor ${facet} ${System.identityHashCode(editor)}")
  }

  @Test
  fun listenerNotAddedIfFlagOff() {
    StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION.override(false);
    editor.selectNotify()  // Should not add the listener

    assertThat(listeners).isEmpty()
    verify(resourceNotificationManager, never()).addListener(any(), eq(facet), isNull(), isNull())

    // Should not have reloaded the panel
    assertThat(reloadsStarted).isEqualTo(0)
    assertThat(reloadsFinished).isEqualTo(0)
  }

  @Test
  fun listenerAddedOnTransition() {
    StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION.override(true);
    editor.selectNotify()  // Should add the listener

    assertThat(listeners).hasSize(1)
    verify(resourceNotificationManager).addListener(eq(listeners[0]), eq(facet), isNull(), isNull())

    editor.selectNotify()  // Should do nothing

    assertThat(listeners).hasSize(1)

    verifyNoMoreInteractions(resourceNotificationManager)

    // Should not have reloaded the panel, since nothing has changed since we first loaded
    assertThat(reloadsStarted).isEqualTo(0)
    assertThat(reloadsFinished).isEqualTo(0)
  }

  @Test
  fun listenerRemovedOnTransition() {
    StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION.override(true);
    editor.selectNotify()  // Should add the listener

    assertThat(listeners).hasSize(1)
    val listener = listeners[0]
    verify(resourceNotificationManager).addListener(eq(listener), eq(facet), isNull(), isNull())

    // Should remove listener irrespective of flag status.
    StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION.override(false);

    editor.deselectNotify()  // Should remove the listener

    assertThat(listeners).hasSize(0)
    verify(resourceNotificationManager).removeListener(eq(listener), eq(facet), isNull(), isNull())
    verify(resourceNotificationManager, times(2)).getCurrentVersion(eq(facet), isNull(), isNull())

    editor.deselectNotify()  // Should do nothing

    verifyNoMoreInteractions(resourceNotificationManager)
  }

  @Test
  fun panelRefreshedWhenOutOfDate() {
    StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION.override(true);
    currentResourceVersion = resourceVersion2

    editor.selectNotify()  // Should add the listener and reload the panel

    assertThat(reloadsStarted).isEqualTo(1)
    assertThat(reloadsFinished).isEqualTo(1)

    currentResourceVersion = resourceVersion3

    editor.selectNotify()  // Should do nothing

    assertThat(reloadsStarted).isEqualTo(1)
    assertThat(reloadsFinished).isEqualTo(1)

    editor.deselectNotify()  // Stores the version
    editor.selectNotify()  // No updates because nothing changed since we were deselected

    assertThat(reloadsStarted).isEqualTo(1)
    assertThat(reloadsFinished).isEqualTo(1)

    editor.deselectNotify() // Stores the version
    currentResourceVersion = resourceVersion1
    editor.selectNotify()  // Now something has changed since we were deselected, so should reload

    assertThat(reloadsStarted).isEqualTo(2)
    assertThat(reloadsFinished).isEqualTo(2)
  }


  @Test
  fun iconIsCorrect() {
    assertThat(StringResourceEditor.ICON).isEqualTo(StudioIcons.LayoutEditor.Toolbar.LANGUAGE)
  }

  @Test
  fun getFontScalesFonts() {
    assertThat(StringResourceEditor.getFont(font).size).isEqualTo(24)
  }

  @Test
  fun getFontDoesNotScaleJBFonts() {
    assertThat(StringResourceEditor.getFont(JBFont.create(font, false)).size).isEqualTo(12)
  }
}
