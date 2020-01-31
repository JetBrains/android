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
package com.android.tools.idea.tests.gui.naveditor

import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.DestinationListFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import com.intellij.util.ui.UIUtil
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Point

@RunWith(GuiTestRemoteRunner::class)
class DestinationListTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  /**
   * Make sure the DestinationList is updated correctly when the nav file is updated outside studio.
   */
  @Test
  fun testExternalUpdate() {
    guiTest.importProject("Navigation").waitForGradleProjectSyncToFinish()
    // Open file as XML and switch to design tab, wait for successful render
    val editor = guiTest.ideFrame().editor
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
    val layout = editor.getLayoutEditor(true).waitForRenderToFinish()

    ApplicationManager.getApplication().invokeAndWait {
      UIUtil.dispatchAllInvocationEvents()
      ApplicationManager.getApplication().runWriteAction {
        editor.currentFile?.setBinaryContent("""
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto"
              xmlns:tools="http://schemas.android.com/tools"
              app:startDestination="@+id/main_activity">
            <fragment android:id="@+id/new_fragment"
                    android:name="google.navigation.NextFragment"
                    tools:layout="@layout/fragment_next">
            </fragment>
        </navigation>
        """.trimIndent().toByteArray())
        val project = layout.surface.target().project
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      UIUtil.dispatchAllInvocationEvents()
    }
    val destinationListFixture = DestinationListFixture.create(guiTest.robot())
    Wait.seconds(2).expecting("new navigation to be displayed").until {
      destinationListFixture.components.map { it.id } == listOf("new_fragment")
    }
  }

  @Test
  @Throws(Exception::class)
  fun testSelectComponent() {
    val frame = guiTest.importProject("Navigation")
    // Open file as XML and switch to design tab, wait for successful render
    val editor = frame
      .waitForGradleProjectSyncToFinish()
      .editor
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)

    editor
      .waitForRenderToFinish()
      .destinationList()
      .clickItem(1)

    // At this point the surface scrolls and zooms. There's nothing obvious to watch for, especially since bugs (110435862) caused us to
    // reach the target value and then zoom/scroll more, incorrectly. So, just wait for what seems like long enough.
    Thread.sleep(1000)

    val surface = editor.navSurface
    val surfaceSize = surface.target().extentSize

    val destination = surface.findDestination("first_screen")
    val distance = Point(surfaceSize.width / 2, surfaceSize.height / 2).distance(destination.midPoint)
    assertThat(distance).isLessThan(2.0)

    val destinationBounds = NavSceneManager.getBoundingBox(listOf(destination.sceneComponent))
    // We try to scale the destination to 100% (1.0), but in lower resolutions the destination might not fit the screen. We handle this case
    // by using the fit scale of the destination.
    val expectedScale = minOf(surface.target().getFitScale(destinationBounds.size, false), 1.0)
    assertThat(surface.scale).isWithin(0.01).of(expectedScale)
  }
}
