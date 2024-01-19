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
package com.android.tools.idea.uibuilder.actions

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.BUTTON
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import org.mockito.Mockito.mock

class ChainStyleViewActionTest : SceneTest() {

  fun testChainHorizontal() {
    ChainStyleViewActions.HORIZONTAL_CHAIN_STYLES.forEach {
      verifyHorizontalChainStyle(it as ChainStyleViewAction)
    }
  }

  fun testChainVertical() {
    ChainStyleViewActions.VERTICAL_CHAIN_STYLES.forEach {
      verifyVerticalChainStyle(it as ChainStyleViewAction)
    }
  }

  fun testChainApplicable() {
    val button0 = myScreen.get("@+id/button0").component
    assertNotNull(button0)
    val button1 = myScreen.get("@id/button1").component
    assertNotNull(button1)
    val button2 = myScreen.get("@id/button2").component
    assertNotNull(button2)
    val button3 = myScreen.get("@id/button3").component
    assertNotNull(button3)
    val button4 = myScreen.get("@+id/button4").component
    assertNotNull(button4)

    ChainStyleViewActions.HORIZONTAL_CHAIN_STYLES.forEach {
      verifyApplicable(listOf(button0), it as ChainStyleViewAction, true)
      verifyApplicable(listOf(button1), it, true)
      verifyApplicable(listOf(button2), it, true)
      verifyApplicable(listOf(button0, button1, button2), it, true)
      verifyApplicable(listOf(button1, button2), it, true)

      // Vertical styles categorized under different menu.
      verifyApplicable(listOf(button3), it, false)
      verifyApplicable(listOf(button0, button3), it, false)

      // All selected. For now this is not supported.
      verifyApplicable(listOf(button0, button1, button2, button3), it, false)

      // Button4 is not connected in chain.
      verifyApplicable(listOf(button4), it, false)
      verifyApplicable(listOf(button0, button4), it, false)
    }

    ChainStyleViewActions.VERTICAL_CHAIN_STYLES.forEach {
      verifyApplicable(listOf(button0), it as ChainStyleViewAction, true)
      verifyApplicable(listOf(button3), it, true)
      verifyApplicable(listOf(button0, button3), it, true)

      // Horizontal style categorized under different menu.
      verifyApplicable(listOf(button1), it, false)
      verifyApplicable(listOf(button2), it, false)
      verifyApplicable(listOf(button0, button2), it, false)

      // All selected. For now this is not supported.
      verifyApplicable(listOf(button0, button1, button2, button3), it, false)

      // Button4 is not connected in chain.
      verifyApplicable(listOf(button4), it, false)
      verifyApplicable(listOf(button0, button4), it, false)
    }
  }

  private fun verifyApplicable(
    list: List<NlComponent>,
    action: ChainStyleViewAction,
    expected: Boolean,
  ) {
    val mockViewEditor = mock(ViewEditor::class.java)
    whenever(mockViewEditor.scene).thenReturn(myScene)

    assertEquals(expected, action.isApplicable(mockViewEditor, list))
  }

  private fun verifyVerticalChainStyle(action: ChainStyleViewAction) {
    val button0 = myScreen.get("@+id/button0").component
    val button3 = myScreen.get("@id/button3").component

    val mockViewEditor = mock(ViewEditor::class.java)
    whenever(mockViewEditor.scene).thenReturn(myScene)
    val mockHandler = mock(ViewHandler::class.java)

    action.perform(mockViewEditor, mockHandler, button0, mutableListOf(button0, button3), 0)

    myScreen
      .get("@+id/button0")
      .expectXml(
        """<Button
        android:id="@+id/button0"
        android:layout_width="100dp"
        android:layout_height="20dp"
        app:layout_constraintBottom_toTopOf="@+id/button3"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toLeftOf="@+id/button1"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_chainStyle="${action.style}" />
        """
          .trimIndent()
      )
  }

  private fun verifyHorizontalChainStyle(action: ChainStyleViewAction) {
    val button0 = myScreen.get("@+id/button0").component
    val button1 = myScreen.get("@id/button1").component
    val button2 = myScreen.get("@id/button2").component

    val mockViewEditor = mock(ViewEditor::class.java)
    whenever(mockViewEditor.scene).thenReturn(myScene)
    val mockHandler = mock(ViewHandler::class.java)

    action.perform(
      mockViewEditor,
      mockHandler,
      button0,
      mutableListOf(button0, button1, button2),
      0,
    )

    myScreen
      .get("@+id/button0")
      .expectXml(
        """<Button
        android:id="@+id/button0"
        android:layout_width="100dp"
        android:layout_height="20dp"
        app:layout_constraintBottom_toTopOf="@+id/button3"
        app:layout_constraintHorizontal_chainStyle="${action.style}"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toLeftOf="@+id/button1"
        app:layout_constraintTop_toTopOf="parent" />
        """
          .trimIndent()
      )
  }

  /**
   * Create a model where button0 is both Horizontal && Vertical chain. 0, 1, 2 are in horizontal
   * chain 0, 3 are in vertical chain
   *
   * 4 is not in any chain
   *
   * No style is set in the chain.
   */
  override fun createModel(): ModelBuilder {
    return model(
      "constraint.xml",
      component(CONSTRAINT_LAYOUT.defaultName())
        .id("@id/root")
        .withBounds(0, 0, 1000, 1000)
        .width("1000dp")
        .height("1000dp")
        .withAttribute("android:padding", "20dp")
        .children(
          component(BUTTON)
            .id("@+id/button0")
            .withBounds(300, 200, 100, 20)
            .width("100dp")
            .height("20dp")
            .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
            .withAttribute("app:layout_constraintRight_toLeftOf", "@+id/button1")
            .withAttribute("app:layout_constraintBottom_toTopOf", "@+id/button3")
            .withAttribute("app:layout_constraintTop_toTopOf", "parent"),
          component(BUTTON)
            .id("@id/button1")
            .withBounds(300, 200, 100, 20)
            .width("100dp")
            .height("20dp")
            .withAttribute("app:layout_constraintLeft_toRightOf", "@id/button0")
            .withAttribute("app:layout_constraintRight_toLeftOf", "@+id/button2"),
          component(BUTTON)
            .id("@id/button2")
            .withBounds(300, 200, 100, 20)
            .width("100dp")
            .height("20dp")
            .withAttribute("app:layout_constraintLeft_toRightOf", "@id/button1")
            .withAttribute("app:layout_constraintRight_toRightOf", "parent"),
          component(BUTTON)
            .id("@id/button3")
            .withBounds(800, 300, 100, 20)
            .width("100dp")
            .height("20dp")
            .withAttribute("app:layout_constraintTop_toBottomOf", "@id/button0")
            .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
          component(BUTTON)
            .id("@+id/button4")
            .withBounds(800, 300, 100, 20)
            .width("100dp")
            .height("20dp")
            .withAttribute("app:layout_constraintTop_toTopOf", "parent")
            .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
            .withAttribute("app:layout_constraintLeft_toLeftOf", "parent"),
        ),
    )
  }
}
