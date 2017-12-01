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

import com.android.SdkConstants.*
import com.android.resources.Density
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.common.editor.NlEditor
import com.android.tools.idea.common.editor.NlEditorProvider
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.util.XmlTagUtil.createTag
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.uibuilder.api.*
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.google.common.collect.ImmutableList
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet
import java.util.*

/*
 * Layout editor-specific helper methods and data for NlModel
 */

const val CUSTOM_DENSITY_ID = "Custom Density"

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
    screen.size = AvdScreenData.getScreenSize(diagonalLength)

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

fun NlModel.canAddComponents(receiver: NlComponent, toAdd: List<NlComponent>): Boolean {
  if (!receiver.hasNlComponentInfo) {
    return false
  }
  val parentHandler = receiver.viewHandler as? ViewGroupHandler ?: return false

  for (component in toAdd) {
    if (!parentHandler.acceptsChild(receiver, component)) {
      return false
    }

    val handler = ViewHandlerManager.get(project).getHandler(component)

    if (handler != null && !handler.acceptsParent(receiver, component)) {
      return false
    }
  }
  return true
}

fun NlModel.checkIfUserWantsToAddDependencies(toAdd: List<NlComponent>): Boolean {
  // May bring up a dialog such that the user can confirm the addition of the new dependencies:
  return NlDependencyManager.get().checkIfUserWantsToAddDependencies(toAdd, facet)
}

/**
 * Creates a new component of the given type. It will optionally insert it as a child of the given parent (and optionally
 * right before the given sibling or null to append at the end.)
 *
 *
 * Note: This operation can only be called when the caller is already holding a write lock. This will be the
 * case from [ViewHandler] callbacks such as [ViewHandler.onCreate] and [DragHandler.commit].
 *
 *
 * Note: The caller is responsible for calling [.notifyModified] if the creation completes successfully.

 * @param editor     A ViewEditor used to handle pixel to dp computations in view handlers, etc.
 * @param tag        The XmlTag for the component.
 * @param parent     The parent to add this component to.
 * @param before     The sibling to insert immediately before, or null to append
 * @param insertType The type of insertion
 */
fun NlModel.createComponent(editor: ViewEditor,
                            tag: XmlTag,
                            parent: NlComponent?,
                            before: NlComponent?,
                            insertType: InsertType): NlComponent? {
  val child = createComponent(tag, parent, before)
  val realTag = child.tag
  if (parent != null) {
    // Required attribute for all views; drop handlers can adjust as necessary
    if (realTag.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI) == null) {
      realTag.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, VALUE_WRAP_CONTENT)
    }
    if (realTag.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI) == null) {
      realTag.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, VALUE_WRAP_CONTENT)
    }
  }
  else {
    // No namespace yet: use the default prefix instead
    if (realTag.getAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH) == null) {
      realTag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
    }
    if (realTag.getAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT) == null) {
      realTag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
    }
  }

  // Notify view handlers
  val viewHandlerManager = ViewHandlerManager.get(project)
  val childHandler = viewHandlerManager.getHandler(child)

  if (childHandler != null) {
    var ok = childHandler.onCreate(editor, parent, child, insertType)
    if (parent != null) {
      ok = ok and NlDependencyManager.get().addDependencies((ImmutableList.of<NlComponent>(child)), facet)
    }
    if (!ok) {
      parent?.removeChild(child)
      realTag.delete()
      return null
    }
  }
  if (parent != null) {
    val parentHandler = viewHandlerManager.getHandler(parent)
    (parentHandler as? ViewGroupHandler)?.onChildInserted(editor, parent, child, insertType)
  }

  return child
}

fun NlModel.createComponents(sceneView: SceneView,
                             item: DnDTransferItem,
                             insertType: InsertType): List<NlComponent> {
  val components = ArrayList<NlComponent>(item.components.size)
  for (dndComponent in item.components) {
    val tag = createTag(sceneView.model.project, dndComponent.representation)
    val component = createComponent(ViewEditorImpl.getOrCreate(sceneView), tag, null, null, insertType) ?: return Collections.emptyList()  // User may have cancelled
    component.w = dndComponent.width
    component.h = dndComponent.height
    components.add(component)
  }
  return components
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