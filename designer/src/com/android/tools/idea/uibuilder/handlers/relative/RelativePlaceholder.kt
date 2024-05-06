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
package com.android.tools.idea.uibuilder.handlers.relative

import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeDropHandler
import java.awt.Point

class RelativePlaceholder(host: SceneComponent) : Placeholder(host) {

  private val delegator = ViewGroupPlaceholder(host)

  override val dominate = false

  override val region = delegator.region

  override fun snap(info: SnappingInfo, retPoint: Point) = delegator.snap(info, retPoint)

  /** TODO: implement the auto connection */
  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) {
    val dropHandler = RelativeDropHandler(sceneComponent)
    dropHandler.updateAttributes(attributes, sceneComponent.drawX, sceneComponent.drawY)
  }
}
