/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette

import com.android.tools.idea.common.model.NlLayoutType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import icons.StudioIcons
import org.jetbrains.android.AndroidTestCase

import javax.swing.*
import javax.xml.ws.Holder

import com.android.SdkConstants.*
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.util.addDependencies
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.extensions.Extensions
import com.intellij.testFramework.PlatformTestUtil
import org.mockito.Mockito.*

class DependencyManagerTest : AndroidTestCase() {
  private var myPanel: JComponent? = null
  private var myDisposable: Disposable? = null
  private var myPalette: Palette? = null
  private var myManager: DependencyManager? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    PlatformTestUtil.registerExtension<AndroidProjectSystemProvider>(Extensions.getArea(project), EP_NAME,
        TestProjectSystem(), testRootDisposable)

    myPanel = mock(JComponent::class.java)
    myDisposable = mock(Disposable::class.java)
    myPalette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT)
    myManager = DependencyManager(project, myPanel!!, myDisposable!!)
    myManager!!.setPalette(myPalette!!, myModule)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      Disposer.dispose(myDisposable!!)
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myPalette = null
      myPanel = null
      myManager = null
      myDisposable = null
    }
    finally {
      super.tearDown()
    }
  }

  fun testNeedsLibraryLoad() {
    assertThat(myManager!!.needsLibraryLoad(findItem(TEXT_VIEW))).isFalse()
    assertThat(myManager!!.needsLibraryLoad(findItem(FLOATING_ACTION_BUTTON))).isTrue()
  }

  fun testCreateItemIconOfTextView() {
    val icon = myManager!!.createItemIcon(findItem(TEXT_VIEW), myPanel!!)
    assertThat(icon).isSameAs(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
  }

  fun testCreateItemIconOfFloatingActionButton() {
    val icon = myManager!!.createItemIcon(findItem(FLOATING_ACTION_BUTTON), myPanel!!)
    // The FloatingActionButton is in support library which is not used. Thus FloatingActionButton should show an icons with download badge.
    assertThat(icon).isNotSameAs(StudioIcons.LayoutEditor.Palette.FLOATING_ACTION_BUTTON)
  }

  fun testCreateLargeItemIconOfTextView() {
    val icon = myManager!!.createLargeItemIcon(findItem(TEXT_VIEW), myPanel!!)
    assertThat(icon).isSameAs(StudioIcons.LayoutEditor.Palette.TEXT_VIEW_LARGE)
  }

  fun testProjectSynchronization() {
    val (floatingActionButton, recyclerView, cardView) = listOf(FLOATING_ACTION_BUTTON, RECYCLER_VIEW, CARD_VIEW).map(this::findItem)

    assertThat(myManager!!.needsLibraryLoad(floatingActionButton)).isTrue()
    assertThat(myManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(myManager!!.needsLibraryLoad(cardView)).isTrue()

    addDependenciesForItems(floatingActionButton, cardView)
    simulateProjectSync()

    assertThat(myManager!!.needsLibraryLoad(floatingActionButton)).isFalse()
    assertThat(myManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(myManager!!.needsLibraryLoad(cardView)).isFalse()
  }

  fun testRegisterDependencyUpdates() {
    simulateProjectSync()
    verify(myPanel, never())!!.repaint()

    addDependenciesForItems(findItem(FLOATING_ACTION_BUTTON))
    simulateProjectSync()
    verify(myPanel)!!.repaint()
  }

  fun testDisposeStopsProjectSyncListening() {
    Disposer.dispose(myDisposable!!)

    addDependenciesForItems(findItem(FLOATING_ACTION_BUTTON))
    simulateProjectSync()
    verify(myPanel, never())!!.repaint()
  }

  private fun simulateProjectSync() {
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
  }

  private fun addDependenciesForItems(vararg items: Palette.Item) {
    val artifactIds = items.asSequence()
        .mapNotNull { item -> GradleCoordinate.parseCoordinateString(item.gradleCoordinateId + ":+") }
        .mapNotNull(GoogleMavenArtifactId.Companion::forCoordinate)
        .distinct()
        .toList()

    myModule.addDependencies(artifactIds, false)
  }

  private fun findItem(tagName: String): Palette.Item {
    val found = Holder<Palette.Item>()
    myPalette!!.accept { item ->
      if (item.tagName == tagName) {
        found.value = item
      }
    }
    if (found.value == null) {
      throw RuntimeException("The item: $tagName was not found on the palette.")
    }
    return found.value
  }
}
