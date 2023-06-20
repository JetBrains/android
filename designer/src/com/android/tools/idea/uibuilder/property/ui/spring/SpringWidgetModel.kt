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
package com.android.tools.idea.uibuilder.property.ui.spring

import com.android.annotations.concurrency.UiThread
import com.intellij.util.SmartList

/**
 * Mode in which the Spring widget can operate. Each of these include a list of their supported parameters.
 */
enum class SpringMode(@JvmField val displayName: String, @JvmField val parameters: Collection<SpringParameter>){
  NORMAL("Normal", listOf(SpringParameter.DURATION, SpringParameter.MAX_ACC, SpringParameter.MAX_VEL)),
  SPRING_WITH_DAMP_CONSTANT("Spring",listOf(
    SpringParameter.BOUNDARY,
    SpringParameter.DAMPING,
    SpringParameter.MASS,
    SpringParameter.STIFFNESS,
    SpringParameter.THRESHOLD
  )),
  SPRING_WITH_DAMP_RATIO("Spring With Ratio", listOf(
    SpringParameter.STIFFNESS,
    SpringParameter.DAMPING_RATIO,
    SpringParameter.THRESHOLD
  ))
}

/**
 * Parameters available to modify for the Spring widget.
 */
enum class SpringParameter(@JvmField val displayName: String) {
  // NORMAL
  DURATION("Duration"),
  MAX_ACC("MaxAcc"),
  MAX_VEL("MaxVel"),
  // SPRING
  BOUNDARY("Boundary"),
  DAMPING("Damping"),
  DAMPING_RATIO("Ratio"),
  MASS("Mass"),
  STIFFNESS("Stiffness"),
  THRESHOLD("Threshold");
}

/**
 * Interface used by the Spring widget to read and write data.
 */
interface SpringWidgetModel {
  /**
   * Mode in which the Spring widget should initialize. If null, the widget will start in the first mode available in [supportedModes].
   */
  val startingMode: SpringMode?
    get() = null

  val supportedModes: Array<SpringMode>
    get() = arrayOf(SpringMode.NORMAL)

  fun addListener(listener: SpringModelChangeListener)

  fun removeListener(listener: SpringModelChangeListener)

  @UiThread
  fun getValue(parameter: SpringParameter): String

  @UiThread
  fun setValue(parameter: SpringParameter, value: String)

  companion object {
    /**
     * Model that makes no operations. Can be used to start off the Spring widget model.
     */
    val NO_OP = object : SpringWidgetModel {

      override fun addListener(listener: SpringModelChangeListener) {
        // Do nothing
      }

      override fun removeListener(listener: SpringModelChangeListener) {
        // Do nothing
      }

      @UiThread
      override fun getValue(parameter: SpringParameter) = ""

      @UiThread
      override fun setValue(parameter: SpringParameter, value: String) {}
    }
  }
}

/**
 * Listener to be triggered when the Spring parameters are externally modified in the underlying data model.
 */
interface SpringModelChangeListener {
  fun onModelChanged()
}

abstract class BaseSpringWidgetModel: SpringWidgetModel {
  protected val listeners = SmartList<SpringModelChangeListener>()

  override fun addListener(listener: SpringModelChangeListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SpringModelChangeListener) {
    listeners.remove(listener)
  }
}
