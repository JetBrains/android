/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.*
import com.android.tools.idea.uibuilder.graphics.NlGraphics
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragDndTarget
import com.android.tools.idea.uibuilder.model.*
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.target.Target
import com.google.common.collect.ImmutableList

/**
 * CoordinatorLayout drag handler
 */
class CoordinatorDragHandler(editor: ViewEditor, handler: ViewGroupHandler,
                             layout: SceneComponent,
                             components: List<NlComponent>,
                             type: DragType) : DragHandler(editor, handler, layout, components, type) {
  private var myComponent: SceneComponent
  private val myDragged: NlComponent?

  init {
    assert(components.size == 1)
    myDragged = components[0]
    myComponent = TemporarySceneComponent(layout.scene, myDragged)
    myComponent.setSize(editor.pxToDp(myDragged.w), editor.pxToDp(myDragged.h), false)
    myComponent.setTargetProvider({ sceneComponent, isParent -> ImmutableList.of<Target>(ConstraintDragDndTarget()) }, false)
    myComponent.drawState = SceneComponent.DrawState.DRAG
    layout.addChild(myComponent)
  }

  override fun start(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int) {
    super.start(x, y, modifiers)
  }

  override fun update(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int): String? {
    val result = super.update(x, y, modifiers)
    return result
  }

  override fun cancel() {
    val scene = (editor as ViewEditorImpl).sceneView.scene
    scene.removeComponent(myComponent)
  }

  override fun commit(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int, modifiers: Int, insertType: InsertType) {
    val scene = (editor as ViewEditorImpl).sceneView.scene
    scene.removeComponent(myComponent)
  }

  override fun paint(gc: NlGraphics) {
  }
}