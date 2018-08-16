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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TargetProvider
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.effectiveDestinationId
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.parentSequence

class NavActionTargetProvider : TargetProvider {
  override fun createTargets(sceneComponent: SceneComponent): List<Target> {
    val nlComponent = sceneComponent.nlComponent
    val rootSceneComponent = sceneComponent.scene.root ?: return listOf()
    val rootNlComponent = rootSceneComponent.nlComponent
    val actionType = nlComponent.getActionType(rootNlComponent)
    if (actionType === ActionType.SELF || actionType === ActionType.REGULAR || actionType === ActionType.EXIT_DESTINATION) {
      val sourceNlComponent = nlComponent.parentSequence().firstOrNull { it.parent == rootNlComponent } ?: return listOf()
      val destinationId = nlComponent.effectiveDestinationId ?: return listOf()
      val sourceSceneComponent = rootSceneComponent.getSceneComponent(sourceNlComponent) ?: return listOf()
      val destinationSceneComponent = rootSceneComponent.getSceneComponent(destinationId) ?: return listOf()
      return listOf(ActionTarget(sceneComponent, sourceSceneComponent, destinationSceneComponent))
    }
    return listOf()
  }
}