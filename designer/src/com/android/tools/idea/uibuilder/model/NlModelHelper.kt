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
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.resources.Density
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.InsertType
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.common.editor.NlEditor
import com.android.tools.idea.common.editor.NlEditorProvider
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.util.XmlTagUtil.createTag
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
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
val BASE_ID_PATTERN = Regex("(.*[^0-9])([0-9]+)?")

/**
 * Returns true if the current module depends on the specified library.

 * @param artifact library artifact e.g. "com.android.support:appcompat-v7"
 */
fun NlModel.isModuleDependency(artifact: String): Boolean {
  val gradleModel = AndroidModuleModel.get(facet)
  return gradleModel != null && GradleUtil.dependsOn(gradleModel, artifact)
}

/**
 * Returns the [GradleVersion] of the specified library that the current module depends on.

 * @param artifact library artifact e.g. "com.android.support:appcompat-v7"
 * *
 * @return the revision or null if the module does not depend on the specified library.
 */
fun NlModel.getModuleDependencyVersion(artifact: String): GradleVersion? {
  val gradleModel = AndroidModuleModel.get(facet)
  return if (gradleModel != null) GradleUtil.getModuleDependencyVersion(gradleModel, artifact) else null
}

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

 * @param sceneView  The target screen, if known. Used to handle pixel to dp computations in view handlers, etc.
 * @param tag        The XmlTag for the component.
 * @param parent     The parent to add this component to.
 * @param before     The sibling to insert immediately before, or null to append
 * @param insertType The type of insertion
 */
fun NlModel.createComponent(sceneView: SceneView,
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
  val editor = ViewEditorImpl(sceneView)

  if (childHandler != null) {
    var ok = childHandler.onCreate(editor, parent, child, insertType)
    if (parent != null) {
      ok = ok and addDependencies(ImmutableList.of<NlComponent>(child), InsertType.CREATE)
    }
    if (!ok) {
      parent?.removeChild(child)
      realTag.delete()
      return null
    }
  }
  if (parent != null) {
    val parentHandler = viewHandlerManager.getHandler(parent)
    if (parentHandler is ViewGroupHandler) {
      parentHandler.onChildInserted(editor, parent, child, insertType)
    }
  }

  return child
}

/**
 * Make sure the dependencies of the components being added are present in the module.
 * If they are not: ask the user if they can be added now.
 * Return true if the dependencies are present now (they may have just been added).
 */
fun NlModel.addDependencies(toAdd: List<NlComponent>?, insertType: InsertType): Boolean {
  if (toAdd == null || insertType.isMove) {
    return true
  }
  val artifacts = HashSet<String>()
  toAdd.forEach { it.getDependencies(artifacts) }
  val dependencies = artifacts.asSequence()
      .map({ artifact -> GradleCoordinate.parseCoordinateString(artifact + ":+") })
      .filter({ Objects.nonNull(it) })
      .toList()
  val manager = GradleDependencyManager.getInstance(project)
  return manager.ensureLibraryIsIncluded(module, dependencies, null)
}

fun NlModel.createComponents(sceneView: SceneView,
                             item: DnDTransferItem,
                             insertType: InsertType): List<NlComponent> {
  val components = ArrayList<NlComponent>(item.components.size)
  for (dndComponent in item.components) {
    val tag = createTag(sceneView.model.project, dndComponent.representation)
    val component = createComponent(sceneView, tag, null, null, insertType) ?: return Collections.emptyList()  // User may have cancelled
    component.w = dndComponent.width
    component.h = dndComponent.height
    components.add(component)
  }
  return components
}

fun NlModel.handleAddition(added: List<NlComponent>, receiver: NlComponent, insertType: InsertType, surface: DesignSurface) {
  var realInsertType = insertType
  if (!receiver.hasNlComponentInfo) {
    return
  }
  val ids = Sets.newHashSet(NlComponent.getIds(this))
  val groupHandler = (receiver.viewHandler as ViewGroupHandler?)!!

  val view = surface.currentSceneView!!

  val editor = ViewEditorImpl(view)

  for (component: NlComponent in added) {
    if (insertType.isMove) {
      realInsertType = if (component.parent === receiver) InsertType.MOVE_WITHIN else InsertType.MOVE_INTO
    }
    if (component.needsDefaultId() && !realInsertType.isMove) {
      val id = component.id
      if (id.isNullOrBlank()) {
        ids.add(component.assignId(ids))
      }
      else {
        BASE_ID_PATTERN.find(id!!)?.groups?.get(1)?.value?.let {
          ids.add(component.assignId(it, ids))
        }
      }
    }
    groupHandler.onChildInserted(editor, receiver, component, realInsertType)
  }
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