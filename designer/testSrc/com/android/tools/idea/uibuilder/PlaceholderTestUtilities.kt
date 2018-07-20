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
package com.android.tools.idea.uibuilder

import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.SceneComponent
import com.intellij.openapi.command.WriteCommandAction

fun applyPlaceholderToSceneComponent(component: SceneComponent, placeholder: Placeholder): Boolean {
  val parent = placeholder.host.authoritativeNlComponent
  val nlComponent = component.authoritativeNlComponent
  val model = nlComponent.model
  val componentsToAdd = listOf(nlComponent)
  val anchor = placeholder.nextComponent?.nlComponent

  if (model.canAddComponents(componentsToAdd, parent, anchor)) {
    val attributes = nlComponent.startAttributeTransaction()
    placeholder.updateAttribute(component, attributes)
    WriteCommandAction.runWriteCommandAction(nlComponent.model.project) { attributes.commit() }
    model.addComponents(componentsToAdd, parent, anchor, InsertType.MOVE_WITHIN, component.scene.designSurface)
    return true
  }
  return false
}
