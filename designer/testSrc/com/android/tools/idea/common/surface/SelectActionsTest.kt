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
package com.android.tools.idea.common.surface

import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.actions.SelectAllAction
import com.android.tools.idea.uibuilder.actions.SelectNextAction
import com.android.tools.idea.uibuilder.actions.SelectPreviousAction
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.android.AndroidTestCase

class SelectActionsTest : LayoutTestCase() {
  fun testSelectNextAction() {
    val model =
      model(
          "model.xml",
          component(LINEAR_LAYOUT)
            .withBounds(0, 0, 100, 100)
            .id("@+id/outer")
            .children(
              component(BUTTON).withBounds(0, 0, 10, 10).id("@+id/button"),
              component(LINEAR_LAYOUT)
                .withBounds(10, 0, 90, 100)
                .id("@+id/inner")
                .children(component(TEXT_VIEW).withBounds(10, 0, 10, 10).id("@+id/textView1")),
              component(TEXT_VIEW).withBounds(20, 0, 10, 10).id("@+id/textView2"),
            ),
        )
        .build()

    val surface = NlSurfaceBuilder.build(project, project)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model))
    surface.selectionModel.setSelection(ImmutableList.of())

    val action = SelectNextAction()

    performAction(action, surface, "outer")
    performAction(action, surface, "button")
    performAction(action, surface, "inner")
    performAction(action, surface, "textView1")
    performAction(action, surface, "textView2")
    performAction(action, surface, "outer")
  }

  fun testSelectPreviousAction() {
    val model =
      model(
          "model.xml",
          component(LINEAR_LAYOUT)
            .withBounds(0, 0, 100, 100)
            .id("@+id/outer")
            .children(
              component(BUTTON).withBounds(0, 0, 10, 10).id("@+id/button"),
              component(LINEAR_LAYOUT)
                .withBounds(10, 0, 90, 100)
                .id("@+id/inner")
                .children(component(TEXT_VIEW).withBounds(10, 0, 10, 10).id("@+id/textView1")),
              component(TEXT_VIEW).withBounds(20, 0, 10, 10).id("@+id/textView2"),
            ),
        )
        .build()

    val surface = NlSurfaceBuilder.build(project, project)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model))
    surface.selectionModel.setSelection(ImmutableList.of())

    val action = SelectPreviousAction()

    performAction(action, surface, "textView2")
    performAction(action, surface, "textView1")
    performAction(action, surface, "inner")
    performAction(action, surface, "button")
    performAction(action, surface, "outer")
    performAction(action, surface, "textView2")
  }

  fun testSelectAllAction() {
    val model =
      model(
          "model.xml",
          component(LINEAR_LAYOUT)
            .withBounds(0, 0, 100, 100)
            .id("@+id/outer")
            .children(
              component(BUTTON).withBounds(0, 0, 10, 10).id("@+id/button"),
              component(LINEAR_LAYOUT)
                .withBounds(10, 0, 90, 100)
                .id("@+id/inner")
                .children(component(TEXT_VIEW).withBounds(10, 0, 10, 10).id("@+id/textView1")),
              component(TEXT_VIEW).withBounds(20, 0, 10, 10).id("@+id/textView2"),
            ),
        )
        .build()

    val surface = NlSurfaceBuilder.build(project, project)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model))
    surface.selectionModel.setSelection(ImmutableList.of())

    val outer = model.treeReader.find("outer")!!
    val button = model.treeReader.find("button")!!
    val inner = model.treeReader.find("inner")!!
    val textView1 = model.treeReader.find("textView1")!!
    val textView2 = model.treeReader.find("textView2")!!

    val action = SelectAllAction()

    action.actionPerformed(
      TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) surface else null }
    )
    AndroidTestCase.assertEquals(
      listOf(outer, button, inner, textView1, textView2),
      surface.selectionModel.selection,
    )
  }

  private fun performAction(action: AnAction, surface: DesignSurface<*>, id: String) {
    action.actionPerformed(
      TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) surface else null }
    )
    val component = surface.model?.treeReader?.find(id)!!
    assertEquals(listOf(component), surface.selectionModel.selection)
  }
}
