/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.ui.spring.BaseSpringWidgetModel
import com.android.tools.idea.uibuilder.property.ui.spring.SpringMode
import com.android.tools.idea.uibuilder.property.ui.spring.SpringModelChangeListener
import com.android.tools.idea.uibuilder.property.ui.spring.SpringParameter
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.diagnostic.Logger

/**
 * MotionLayout model for the Spring widget.
 */
class MotionLayoutSpringModel(propertiesModel: MotionLayoutAttributesModel) : BaseSpringWidgetModel() {
  private val log = Logger.getInstance(MotionLayoutSpringModel::class.java)
  private val allProperties = propertiesModel.allProperties

  init {
    propertiesModel.addListener(object : PropertiesModelListener<NlPropertyItem> {
      override fun propertyValuesChanged(model: PropertiesModel<NlPropertyItem>) {
        listeners.forEach(SpringModelChangeListener::onModelChanged)
      }
    })
  }

  override val startingMode: SpringMode = SpringMode.NORMAL

  override val supportedModes: Array<SpringMode> = kotlin.run {
    val onSwipeProp = allProperties[MotionSceneAttrs.Tags.ON_SWIPE]!!
    val supportsSpring = onSwipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_DAMPING) != null
    return@run if (supportsSpring) {
      arrayOf(SpringMode.NORMAL, SpringMode.SPRING_WITH_DAMP_CONSTANT)
    }
    else {
      arrayOf(SpringMode.NORMAL)
    }
  }

  @UiThread
  override fun getValue(parameter: SpringParameter): String {
    val transitionProp = allProperties[MotionSceneAttrs.Tags.TRANSITION]!!
    val swipeProp = allProperties[MotionSceneAttrs.Tags.ON_SWIPE]!!

    return when (parameter) {
      SpringParameter.MAX_ACC -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_MAX_ACCELERATION)
      SpringParameter.MAX_VEL -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_MAX_VELOCITY)
      SpringParameter.DAMPING -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_SPRING_DAMPING)
      SpringParameter.THRESHOLD -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_SPRING_STOP_THRESHOLD)
      SpringParameter.STIFFNESS -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_SPRING_STIFFNESS)
      SpringParameter.DURATION -> {
        var duration = 400
        val durationProp = transitionProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.Transition.ATTR_DURATION)
        if (durationProp != null) {
          val str = durationProp.resolvedValue
          if (str != null) {
            duration = str.toInt()
          }
        }
        return duration.toString()
      }
      SpringParameter.BOUNDARY -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_SPRING_BOUNDARY)
      SpringParameter.MASS -> swipeProp.getStringValue(MotionSceneAttrs.OnSwipe.ATTR_SPRING_MASS)
      SpringParameter.DAMPING_RATIO -> ""
    }
  }

  @UiThread
  override fun setValue(parameter: SpringParameter, value: String) {
    var valueToWrite = value
    val swipeProp = allProperties[MotionSceneAttrs.Tags.ON_SWIPE]

    if (swipeProp == null) {
      log.warn("OnSwipe Tag for: ${parameter.displayName} not found")
      return
    }

    val propertyItem: NlPropertyItem? = when (parameter) {
      SpringParameter.MAX_ACC -> swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_MAX_ACCELERATION)
      SpringParameter.MAX_VEL -> swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_MAX_VELOCITY)
      SpringParameter.DAMPING -> swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_DAMPING)
      SpringParameter.THRESHOLD ->
        swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_STOP_THRESHOLD)
      SpringParameter.STIFFNESS -> swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_STIFFNESS)
      SpringParameter.BOUNDARY -> swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_BOUNDARY)
      SpringParameter.MASS -> swipeProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_MASS)
      SpringParameter.DURATION -> {
        valueToWrite = value.toFloat().toInt().toString()
        val transitionProp = allProperties[MotionSceneAttrs.Tags.TRANSITION]!!
        transitionProp.getOrNull(SdkConstants.AUTO_URI, MotionSceneAttrs.Transition.ATTR_DURATION)
      }
      SpringParameter.DAMPING_RATIO -> null
    }
    if (propertyItem != null) {
      propertyItem.writeValue(valueToWrite)
    }
    else {
      log.warn("Property for Spring parameter: ${parameter.displayName} not found")
    }
  }
}

private fun NlPropertyItem.writeValue(value: String) {
  NlWriteCommandActionUtil.run(this.components, "Spring panel modification") { this.value = value }
}

private fun PropertiesTable<NlPropertyItem>.getStringValue(attributeName: String): String =
  this.getOrNull(SdkConstants.AUTO_URI, attributeName)?.resolvedValue ?: ""
