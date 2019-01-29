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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragTarget
import java.awt.Point

/**
 * TODO: support auto-connection.
 */
class ConstraintPlaceholder(host: SceneComponent) : Placeholder(host) {

  private val delegator = ViewGroupPlaceholder(host)

  override val region = delegator.region

  override fun snap(left: Int, top: Int, right: Int, bottom: Int, retPoint: Point) = delegator.snap(left, top, right, bottom, retPoint)

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) =
    ConstraintDragTarget.ConstraintDropHandler(sceneComponent)
      .updateAttributes(attributes, host, sceneComponent.drawX, sceneComponent.drawY)
}
