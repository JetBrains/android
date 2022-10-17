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
package com.android.tools.idea.uibuilder.model

import com.android.AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.dependsOnAppCompat
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import kotlin.math.hypot

/*
 * Layout editor-specific helper methods and data for NlModel
 */

const val CUSTOM_DENSITY_ID: String = "Custom Density"

// TODO: When appropriate move this static methods to appropriate file.
@JvmOverloads
fun updateConfigurationScreenSize(configuration: Configuration, @AndroidCoordinate xDimension: Int, @AndroidCoordinate yDimension: Int,
                                  original: Device? = configuration.cachedDevice) {
  val deviceBuilder = if (original != null) Device.Builder(original) else return // doesn't copy tag id
  deviceBuilder.setTagId(original.tagId)

  deviceBuilder.setName("Custom")
  deviceBuilder.setId(Configuration.CUSTOM_DEVICE_ID)
  val device = deviceBuilder.build()
  for (state in device.allStates) {
    val screen = state.hardware.screen
    screen.xDimension = xDimension
    screen.yDimension = yDimension

    val dpi = screen.pixelDensity.dpiValue.toDouble()
    val width = xDimension / dpi
    val height = yDimension / dpi
    val diagonalLength = hypot(width, height)

    screen.diagonalLength = diagonalLength
    screen.size = ScreenSize.getScreenSize(diagonalLength)

    screen.ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)

    screen.screenRound = device.defaultHardware.screen.screenRound
    screen.chin = device.defaultHardware.screen.chin
  }

  //Change the orientation of the device depending on the shape of the canvas
  val newState: State? =
    if (xDimension > yDimension) device.allStates.singleOrNull { it.orientation == ScreenOrientation.LANDSCAPE }
    else device.allStates.singleOrNull { it.orientation == ScreenOrientation.PORTRAIT }
  configuration.setEffectiveDevice(device, newState)
}

/**
 * Changes the configuration to use a custom device with the provided density. This is done only if the configuration's cached device is not
 * null, since the custom device is created from it.
 */
fun NlModel.overrideConfigurationDensity(density: Density) {
  val original = configuration.cachedDevice ?: return
  val deviceBuilder = Device.Builder(original) // doesn't copy tag id
  deviceBuilder.setTagId(original.tagId)
  deviceBuilder.setName("Custom")
  deviceBuilder.setId(CUSTOM_DENSITY_ID)
  val device = deviceBuilder.build()
  device.allStates
      .map { it.hardware.screen }
      .forEach { it.pixelDensity = density }

  configuration.setEffectiveDevice(device, device.defaultState)
}

@Deprecated(message = "Use NlModel.module.dependsOnAppCompat()",
            replaceWith = ReplaceWith("com.android.tools.idea.util.dependsOnAppCompat()") )
fun NlModel.moduleDependsOnAppCompat(): Boolean {
  return module.dependsOnAppCompat()
}

fun NlModel.currentActivityIsDerivedFromAppCompatActivity(): Boolean {
  var activityClassName: String? = configuration.activity ?: // The activity is not specified in the XML file.
      // We cannot know if the activity is derived from AppCompatActivity.
      // Assume we are since this is how the default activities are created.
      return true
  if (activityClassName!!.startsWith(".")) {
    val pkg = StringUtil.notNullize(facet.getModuleSystem().getPackageName())
    activityClassName = pkg + activityClassName
  }
  val facade = JavaPsiFacade.getInstance(project)
  var activityClass = facade.findClass(activityClassName, module.moduleScope)
  while (activityClass != null && !CLASS_APP_COMPAT_ACTIVITY.isEquals(activityClass.qualifiedName)) {
    activityClass = activityClass.superClass
  }
  return activityClass != null
}
