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

import com.android.SdkConstants.CLASS_APP_COMPAT_ACTIVITY
import com.android.resources.Density
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.common.editor.NlEditor
import com.android.tools.idea.common.editor.NlEditorProvider
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.model.MergedManifest
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.util.dependsOnAppCompat
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import org.jetbrains.android.facet.AndroidFacet

/*
 * Layout editor-specific helper methods and data for NlModel
 */

const val CUSTOM_DENSITY_ID: String = "Custom Density"

/**
 * Changes the configuration to use a custom device with screen size defined by xDimension and yDimension.
 */
fun NlModel.overrideConfigurationScreenSize(@AndroidCoordinate xDimension: Int, @AndroidCoordinate yDimension: Int) {
  val original = configuration.device
  val deviceBuilder = Device.Builder(original) // doesn't copy tag id
  if (original != null) {
    deviceBuilder.setTagId(original.tagId)
  }
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
    val diagonalLength = Math.sqrt(width * width + height * height)

    screen.diagonalLength = diagonalLength
    screen.size = ScreenSize.getScreenSize(diagonalLength)

    screen.ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)

    screen.screenRound = device.defaultHardware.screen.screenRound
    screen.chin = device.defaultHardware.screen.chin
  }

  // If a custom device already exists, remove it before adding the latest one
  val devices = configuration.configurationManager.devices
  var customDeviceReplaced = false
  for (i in devices.indices) {
    if ("Custom" == devices[i].id) {
      devices[i] = device
      customDeviceReplaced = true
      break
    }
  }

  if (!customDeviceReplaced) {
    devices.add(device)
  }

  val better: VirtualFile?
  val newState: State?
  //Change the orientation of the device depending on the shape of the canvas
  if (xDimension > yDimension) {
    better = ConfigurationMatcher.getBetterMatch(configuration, device, "Landscape", null, null)
    newState = device.getState("Landscape")
  }
  else {
    better = ConfigurationMatcher.getBetterMatch(configuration, device, "Portrait", null, null)
    newState = device.getState("Portrait")
  }

  if (better != null) {
    val old = configuration.file!!
    val project = project
    val descriptor = OpenFileDescriptor(project, better, -1)
    val manager = FileEditorManager.getInstance(project)
    val selectedEditor = manager.getSelectedEditor(old)
    manager.openEditor(descriptor, true)
    // Switch to the same type of editor (XML or Layout Editor) in the target file
    if (selectedEditor is NlEditor) {
      manager.setSelectedEditor(better, NlEditorProvider.DESIGNER_ID)
    }
    else if (selectedEditor != null) {
      manager.setSelectedEditor(better, TextEditorProvider.getInstance().editorTypeId)
    }

    val facet = AndroidFacet.getInstance(configuration.module)
    if (facet != null) {
      val configuration = ConfigurationManager.getOrCreateInstance(facet).getConfiguration(better)
      configuration.setEffectiveDevice(device, newState)
    }
  }
  else {
    configuration.setEffectiveDevice(device, newState)
  }
}

/**
 * Changes the configuration to use a custom device with the provided density.
 */
fun NlModel.overrideConfigurationDensity(density: Density) {
  val original = configuration.device
  val deviceBuilder = Device.Builder(original) // doesn't copy tag id
  if (original != null) {
    deviceBuilder.setTagId(original.tagId)
  }
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
  return this.module.dependsOnAppCompat()
}

fun NlModel.currentActivityIsDerivedFromAppCompatActivity(): Boolean {
  val configuration = this.configuration
  var activityClassName: String? = configuration.activity ?: // The activity is not specified in the XML file.
      // We cannot know if the activity is derived from AppCompatActivity.
      // Assume we are since this is how the default activities are created.
      return true
  if (activityClassName!!.startsWith(".")) {
    val manifest = MergedManifest.get(this.module)
    val pkg = StringUtil.notNullize(manifest.`package`)
    activityClassName = pkg + activityClassName
  }
  val facade = JavaPsiFacade.getInstance(this.project)
  var activityClass = facade.findClass(activityClassName, this.module.moduleScope)
  while (activityClass != null && !CLASS_APP_COMPAT_ACTIVITY.isEquals(activityClass.qualifiedName)) {
    activityClass = activityClass.superClass
  }
  return activityClass != null
}

object NlModelHelper {
  fun handleDeletion(parent: NlComponent, children: Collection<NlComponent>): Boolean {
    if (parent.hasNlComponentInfo) {
      val viewHandlerManager = ViewHandlerManager.get(parent.model.facet)

      val handler = viewHandlerManager.getHandler(parent)
      if (handler is ViewGroupHandler) {
        return handler.deleteChildren(parent, children)
      }
    }
    return false
  }
}