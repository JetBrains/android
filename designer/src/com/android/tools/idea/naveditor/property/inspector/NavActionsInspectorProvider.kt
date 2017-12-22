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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.scene.targets.ActionTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.ui.components.JBList
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.event.HierarchyListener
import javax.swing.event.ListSelectionListener

// Open for testing only
open class NavActionsInspectorProvider : NavListInspectorProvider<NavActionsProperty>(NavActionsProperty::class.java,
    StudioIcons.NavEditor.Properties.ACTION) {

  override fun addItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?) {
    assert(parents.size == 1)

    val addActionDialog = AddActionDialog(existing, parents[0], resourceResolver)

    if (addActionDialog.showAndGet()) {
      WriteCommandAction.runWriteCommandAction(null, {
        val realComponent = existing ?: run {
          val source = addActionDialog.source
          val tag = source.tag.createChildTag(NavigationSchema.TAG_ACTION, null, null, false)
          source.model.createComponent(tag, source, null)
        }
        realComponent.ensureId()
        realComponent.setAttribute(
            SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, SdkConstants.ID_PREFIX + addActionDialog.destination.id!!)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_ENTER_ANIM, addActionDialog.enterTransition)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_EXIT_ANIM, addActionDialog.exitTransition)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO, addActionDialog.popTo)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE,
            if (addActionDialog.isInclusive) "true" else null)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_SINGLE_TOP,
            if (addActionDialog.isSingleTop) "true" else null)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DOCUMENT,
            if (addActionDialog.isDocument) "true" else null)
        realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_CLEAR_TASK,
            if (addActionDialog.isClearTask) "true" else null)
      })
    }
  }

  override fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?) =
      if (components.size == 1 && components[0] == surface?.currentNavigation) {
        "Global Actions"
      }
      else {
        "Actions"
      }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): NavListInspectorComponent<NavActionsProperty> {
    val inspector: NavListInspectorComponent<NavActionsProperty>  = super.createCustomInspector(components, properties, propertiesManager)
    val scene = propertiesManager.designSurface?.scene
    if (scene != null) {
      inspector.addAttachListener { list ->
        list.addListSelectionListener {
          updateSelection(scene, list)
        }
        list.addHierarchyListener {
          updateSelection(scene, list)
        }
      }
    }
    return inspector
  }

  private fun updateSelection(scene: Scene, list: JBList<NlProperty>) {
    val selected: Multimap<NlComponent, String> = HashMultimap.create()
    list.selectedValuesList
        .flatMap { it.components }
        .forEach { selected.put(it.parent, it.id) }
    for (component: SceneComponent in scene.sceneComponents) {
      component.targets
          .filterIsInstance(ActionTarget::class.java)
          .forEach {
            it.isHighlighted = selected.containsEntry(it.component.nlComponent, it.id)
          }
    }
    scene.needsRebuildList()
    scene.repaint()
  }
}
