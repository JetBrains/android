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
package com.android.tools.idea.uibuilder.handlers.motion.property.testutil

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils
import com.android.tools.idea.uibuilder.handlers.motion.editor.NlComponentTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionSelection
import com.intellij.psi.xml.XmlFile

class MotionSelectionFactory(private val nlModel: NlModel, sceneFile: XmlFile) {
  private val motionLayout = nlModel.components.single()
  private val motionScene =
    MotionSceneTag.parse(motionLayout, nlModel.project, sceneFile.virtualFile, sceneFile)
  private val meModel = MeModel(motionScene, convertToNlComponentTag(motionLayout), null, null)

  fun createConstraintSet(id: String): MotionSelection {
    val tag = findConstraintSet(id)
    return MotionSelection(
      MotionEditorSelector.Type.CONSTRAINT_SET,
      arrayOf(tag),
      nlModel.components
    )
  }

  fun createConstraint(setId: String, id: String): MotionSelection {
    val component = nlModel.find(id) ?: throw error("NlComponent not found: $id")
    val componentTag = convertToNlComponentTag(component)
    val constraintSet = findConstraintSet(setId)
    val tag = findConstraint(constraintSet, id) ?: componentTag
    val attrs = meModel.populateViewInfo(constraintSet)
    componentTag.setClientData(MotionSceneUtils.MOTION_LAYOUT_PROPERTIES, attrs[id])
    return MotionSelection(MotionEditorSelector.Type.CONSTRAINT, arrayOf(tag), listOf(component))
  }

  fun createTransition(start: String, end: String): MotionSelection {
    val tag = findTransition(start, end)
    return MotionSelection(MotionEditorSelector.Type.TRANSITION, arrayOf(tag), nlModel.components)
  }

  fun createKeyFrame(
    start: String,
    end: String,
    keyType: String,
    framePosition: Int,
    target: String
  ): MotionSelection {
    val component = nlModel.find(target) ?: motionLayout
    val keyFrameSet = findKeyFrameSet(start, end)
    val keyFrame = findKeyFrame(keyFrameSet, keyType, framePosition, target) as MTag
    return MotionSelection(
      MotionEditorSelector.Type.KEY_FRAME,
      arrayOf(keyFrame),
      listOf(component)
    )
  }

  private fun findConstraintSet(id: String): MotionSceneTag {
    return motionScene.getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET).firstOrNull {
      id == Utils.stripID(it.getAttributeValue(SdkConstants.ATTR_ID))
    } as? MotionSceneTag ?: throw error("ConstraintSet not found: $id")
  }

  private fun findTransition(start: String, end: String): MotionSceneTag {
    return motionScene.getChildTags(MotionSceneAttrs.Tags.TRANSITION).firstOrNull {
      start ==
        Utils.stripID(it.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START)) &&
        end ==
          Utils.stripID(it.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END))
    } as? MotionSceneTag ?: throw error("Transition not found: start=$start, end=$end")
  }

  private fun findKeyFrameSet(start: String, end: String): MotionSceneTag {
    val transition = findTransition(start, end)
    return transition.getChildTags(MotionSceneAttrs.Tags.KEY_FRAME_SET)?.single() as? MotionSceneTag
      ?: throw error("KeyFrameSet not found: start=$start, end=$end")
  }

  private fun findConstraint(constraintSet: MotionSceneTag, id: String): MotionSceneTag? {
    return constraintSet.getChildTags(MotionSceneAttrs.Tags.CONSTRAINT).firstOrNull {
      id == Utils.stripID(it.getAttributeValue(SdkConstants.ATTR_ID))
    } as? MotionSceneTag
  }

  private fun findKeyFrame(
    keyFrameSet: MotionSceneTag,
    keyType: String,
    framePosition: Int,
    target: String
  ): MotionSceneTag? {
    return keyFrameSet.getChildTags(keyType).singleOrNull {
      framePosition.toString() == it.getAttributeValue(MotionSceneAttrs.Key.FRAME_POSITION) &&
        target == Utils.stripID(it.getAttributeValue(MotionSceneAttrs.Key.MOTION_TARGET))
    } as? MotionSceneTag
      ?: error("$keyType not found at position: $framePosition and target: $target")
  }

  private fun convertToNlComponentTag(component: NlComponent): NlComponentTag {
    val parent = component.parent
    val parentTag = if (parent != null) convertToNlComponentTag(parent) else null
    return NlComponentTag(component, parentTag)
  }
}
