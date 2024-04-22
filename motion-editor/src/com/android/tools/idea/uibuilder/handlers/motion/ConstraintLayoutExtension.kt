/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag
import com.android.tools.idea.uibuilder.model.ensureLiveId
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities
import com.android.tools.idea.uibuilder.scout.Direction

/**
 * Implementation of the [ConstraintComponentUtilities.ConstraintLayoutExtension] interface
 * for the Motion Editor providing the entry points to handle connection of constraints in the
 * Layout Editor.
 */
class ConstraintLayoutExtensionImpl : ConstraintComponentUtilities.ConstraintLayoutExtension {
  override fun scoutConnect(source: NlComponent,
                            sourceDirection: Direction,
                            target: NlComponent,
                            targetDirection: Direction,
                            margin: Int): Boolean {
    if (!MotionSceneUtils.isUnderConstraintSet(source)) return false

    val srcIndex = sourceDirection.ordinal
    val attrib = ConstraintComponentUtilities.ATTRIB_MATRIX[srcIndex][targetDirection.ordinal]
                 ?: throw RuntimeException("cannot connect $sourceDirection to $targetDirection")
    val list = ArrayList<String>()
    for (i in ConstraintComponentUtilities.ATTRIB_CLEAR[srcIndex].indices) {
      val clr_attr = ConstraintComponentUtilities.ATTRIB_CLEAR[srcIndex][i]
      if (attrib != clr_attr) {
        list.add(clr_attr)
      }
    }

    val tagwriter = MotionSceneUtils.getTagWriter(source)

    clearAttributes(SdkConstants.SHERPA_URI, list, tagwriter)
    val targetId = if (target === source.parent) {
      SdkConstants.ATTR_PARENT
    }
    else {
      SdkConstants.NEW_ID_PREFIX + target.ensureLiveId()
    }
    tagwriter.setAttribute(SdkConstants.SHERPA_URI, attrib, targetId)
    if ((srcIndex <= Direction.BASELINE.ordinal) && (margin > 0)) {
      tagwriter.setAttribute(SdkConstants.ANDROID_URI, ConstraintComponentUtilities.ATTRIB_MARGIN[srcIndex], margin.toString() + "dp")
      if (ConstraintComponentUtilities.ATTRIB_MARGIN_LR[srcIndex] != null) { // add the left and right as needed
        tagwriter.setAttribute(SdkConstants.ANDROID_URI, ConstraintComponentUtilities.ATTRIB_MARGIN_LR[srcIndex], margin.toString() + "dp")
      }
    }
    tagwriter.commit("create connection")
    val str = when (sourceDirection) {
      Direction.BASELINE -> DecoratorUtilities.BASELINE_CONNECTION
      Direction.BOTTOM -> DecoratorUtilities.BOTTOM_CONNECTION
      Direction.LEFT -> DecoratorUtilities.LEFT_CONNECTION
      Direction.RIGHT -> DecoratorUtilities.RIGHT_CONNECTION
      Direction.TOP -> DecoratorUtilities.TOP_CONNECTION
      else -> null
    }
    // noinspection ConstantConditions
    if (str != null) {
      DecoratorUtilities.setTimeChange(source, str, DecoratorUtilities.ViewStates.INFERRED, DecoratorUtilities.ViewStates.SELECTED)
    }

    return true
  }

  private fun clearAttributes(uri: String?, attributes: List<String?>, tagwriter: MTag.TagWriter) {
    val count = attributes.size
    for (i in 0 until count) {
      val attribute = attributes[i]
      tagwriter.setAttribute(uri, attribute, null)
    }
  }
}