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
@file:JvmName("SceneViewHelper")
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import org.intellij.lang.annotations.JdkConstants


/**
 * Selects the component under the given x,y coordinate, optionally
 * toggling or replacing the selection.
 *
 * @param x                       The mouse click x coordinate, in Swing coordinates.
 * @param y                       The mouse click y coordinate, in Swing coordinates.
 * @param allowToggle             If true, clicking an unselected component adds it to the selection,
 * and clicking a selected component removes it from the selection. If not,
 * the selection is replaced.
 * @param ignoreIfAlreadySelected If true, and the clicked component is already selected, leave the
 * selection (including possibly other selected components) alone
 */
fun SceneView.selectComponentAt(@SwingCoordinate x: Int,
                                @SwingCoordinate y: Int,
                                @JdkConstants.InputEventMask modifiersEx: Int,
                                allowToggle: Boolean,
                                ignoreIfAlreadySelected: Boolean)
  : NlComponent? {

  val xDip = Coordinates.getAndroidXDip(this, x)
  val yDip = Coordinates.getAndroidYDip(this, y)
  val clickedTarget = scene.findTarget(context, xDip, yDip, modifiersEx)
  val clicked: SceneComponent?
  if (clickedTarget != null) {
    clicked = clickedTarget.component
  }
  else {
    clicked = scene.findComponent(context, xDip, yDip)
  }
  var component: NlComponent? = null
  if (clicked != null) {
    component = clicked.nlComponent
  }

  val secondarySelector = Scene.getSecondarySelector(context, xDip, yDip)
  val useSecondarySelector = component != null && secondarySelector != null
  if (useSecondarySelector) {
    // Change clicked component to the secondary selection.
    component = secondarySelector!!.component
  }

  if (!allowToggle && useSecondarySelector) {
    selectionModel.setSecondarySelection(component, secondarySelector!!.constraint)
  }
  else {
    selectComponent(component, allowToggle, ignoreIfAlreadySelected)
  }

  return component
}

fun SceneView.selectComponent(component: NlComponent?,
                              allowToggle: Boolean,
                              ignoreIfAlreadySelected: Boolean) {
  if (ignoreIfAlreadySelected && component != null && selectionModel.isSelected(component)) {
    return
  }
  if (component == null) {
    selectionModel.clear()
  }
  else if (allowToggle) {
    selectionModel.toggle(component)
  }
  else {
    selectionModel.setSelection(listOf(component))
  }
}