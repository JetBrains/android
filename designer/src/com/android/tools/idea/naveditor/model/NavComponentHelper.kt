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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.BooleanAutoAttributeDelegate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.StringAttributeDelegate
import com.android.tools.idea.common.model.StringAutoAttributeDelegate
import com.android.tools.idea.res.resolveStringValue
import com.android.tools.idea.uibuilder.model.IdAutoAttributeDelegate
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.io.File

/*
 * Extensions to NlComponent used by the navigation editor
 */

/**
 * This is an enumeration indicating the type of action represented by the specified NlComponent.
 * In order of decreasing precedence:
 * NONE: This tag is either not an action or is invalid.
 * SELF: The destination attribute refers to the action's parent.
 * GLOBAL: The action's parent is a navigation element.
 * REGULAR: The destination attribute refers to a sibling of the action's parent
 * EXIT: The destination attribute refers to an element that is not under the action's parent's parent.
 */
enum class ActionType {
  NONE,
  SELF,
  GLOBAL,
  REGULAR,
  EXIT
}

fun NlComponent.getUiName(resourceResolver: ResourceResolver?): String {
  val name = id ?:
      resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)?.substringAfterLast(".") ?:
      tagName
  return resourceResolver?.resolveStringValue(name) ?: name
}

val NlComponent.visibleDestinations: List<NlComponent>
  get() {
    val schema = NavigationSchema.get(model.facet)
    val result = arrayListOf<NlComponent>()
    var p: NlComponent? = this
    while (p != null) {
      p.children.filterTo(result, { c -> schema.getDestinationType(c.tagName) != null })
      p = p.parent
    }
    // The above won't add the root itself
    result.addAll(model.components)
    return result
  }

fun NlComponent.findVisibleDestination(id: String): NlComponent? {
  val schema = NavigationSchema.get(model.facet)
  var p = parent
  while (p != null) {
    p.children.firstOrNull { c -> schema.getDestinationType(c.tagName) != null && c.id == id }?.let { return it }
    p = p.parent
  }
  // The above won't pick up the root
  return model.components.firstOrNull { c -> c.id == id }
}

val NlComponent.destinationType
  get() = model.schema.getDestinationType(tagName)

val NlComponent.includeAttribute: String?
  get() = resolveAttribute(AUTO_URI, ATTR_GRAPH)

val NlComponent.includeFile: XmlFile?
  get() {
    val resources = model.configuration.resourceResolver ?: return null
    val value = resources.findResValue(includeAttribute, false) ?: return null
    val vFile = VfsUtil.findFileByIoFile(File(value.value), true) ?: return null
    return PsiManager.getInstance(model.project).findFile(vFile) as? XmlFile
  }

val NlComponent.includeFileName: String?
  get() = includeFile?.name

val NlComponent.isStartDestination: Boolean
  get() {
    val actualStart = parent?.startDestination
    return actualStart != null && actualStart == id
  }

val NlComponent.isDestination: Boolean
  get() = destinationType != null

val NlComponent.isAction: Boolean
  get() = tagName == NavigationSchema.TAG_ACTION

val NlComponent.isNavigation: Boolean
  get() = destinationType == NavigationSchema.DestinationType.NAVIGATION

val NlComponent.isSelfAction: Boolean
  get() = actionType == ActionType.SELF

val NlComponent.actionType: ActionType
  get() {
    if (!isAction) {
      return ActionType.NONE
    }

    val myParent = parent ?: throw IllegalStateException()

    val destination = effectiveDestinationId
    if (myParent.id == destination) {
      return ActionType.SELF
    }

    if (myParent.isNavigation) {
      return ActionType.GLOBAL
    }

    myParent.parent?.let {
      if (destination != null && it.containsDestination(destination)) {
        return ActionType.REGULAR
      }
    }

    return ActionType.EXIT
  }

private fun NlComponent.containsDestination(destinationId: String): Boolean {
  return children.map { it.id }.contains(destinationId)
}

var NlComponent.actionDestinationId: String? by IdAutoAttributeDelegate(NavigationSchema.ATTR_DESTINATION)
var NlComponent.enterAnimation: String? by StringAutoAttributeDelegate(NavigationSchema.ATTR_ENTER_ANIM)
var NlComponent.className: String? by StringAttributeDelegate(ANDROID_URI, ATTR_NAME)
var NlComponent.layout: String? by StringAttributeDelegate(TOOLS_URI, ATTR_LAYOUT)
var NlComponent.exitAnimation: String? by StringAutoAttributeDelegate(NavigationSchema.ATTR_EXIT_ANIM)
// TODO: Use IdAutoAttributeDelegate for popUpTo
var NlComponent.popUpTo: String? by IdAutoAttributeDelegate(NavigationSchema.ATTR_POP_UP_TO)
var NlComponent.inclusive: Boolean by BooleanAutoAttributeDelegate(NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE)
var NlComponent.singleTop: Boolean by BooleanAutoAttributeDelegate(NavigationSchema.ATTR_SINGLE_TOP)
var NlComponent.document: Boolean by BooleanAutoAttributeDelegate(NavigationSchema.ATTR_DOCUMENT)
var NlComponent.clearTask: Boolean by BooleanAutoAttributeDelegate(NavigationSchema.ATTR_CLEAR_TASK)

var NlComponent.startDestination: String? by IdAutoAttributeDelegate(ATTR_START_DESTINATION)

val NlComponent.actionDestination: NlComponent?
  get() {
    assert(isAction)
    var p: NlComponent = parent ?: return null
    val targetId = actionDestinationId ?: return null
    while (true) {
      p.children.firstOrNull { it.id == targetId }?.let { return it }
      p = p.parent ?: break
    }
    // The above won't check the root itself
    return model.components.firstOrNull { it.id == targetId }
  }

/**
 * [actionSetup] should include everything needed to set the default id (destination, popTo, and popToInclusive).
 */
@JvmOverloads
fun NlComponent.createAction(destinationId: String? = null, actionSetup: NlComponent.() -> Unit = {}): NlComponent {
  val newAction = createChild(NavigationSchema.TAG_ACTION)
  newAction.actionDestinationId = destinationId
  newAction.actionSetup()
  // TODO: it would be nice if, when we changed something affecting the below logic and the id hasn't been changed,
  // we could update the id as a refactoring so references are also updated.
  newAction.assignId(generateActionId(this, newAction.actionDestinationId, newAction.popUpTo, newAction.inclusive))
  return newAction
}

fun generateActionId(source: NlComponent, destinationId: String?, popTo: String?, inclusive: Boolean): String {
  val displaySourceId = source.id ?: source.model.virtualFile.nameWithoutExtension
  if (destinationId == null) {
    if (popTo == null) {
      return ""
    }
    if (inclusive) {
      if (popTo == source.id) {
        return "action_${displaySourceId}_pop"
      }
      return "action_${displaySourceId}_pop_including_${popTo}"
    }
  }
  val effectiveId = destinationId ?: popTo
  if (effectiveId == source.id) {
    return "action_${displaySourceId}_self"
  }
  if (source.isNavigation) {
    return "action_global_${effectiveId}"
  }
  return "action_${displaySourceId}_to_${effectiveId}"
}

fun NlComponent.createSelfAction(): NlComponent {
  return createAction(id)
}

fun NlComponent.createReturnToSourceAction(): NlComponent {
  return createAction {
    popUpTo = parent?.id
    inclusive = true
  }
}

fun NlComponent.setAsStartDestination() {
  parent?.startDestination = id
}

fun NlComponent.createNestedGraph(): NlComponent {
  return createChild(model.schema.getDefaultTag(NavigationSchema.DestinationType.NAVIGATION)!!)
}

private fun NlComponent.createChild(tagName: String): NlComponent {
  val newTag = tag.createChildTag(tagName, null, null, false)
  val child = model.createComponent(newTag, this, null)
  child.ensureId()
  return child
}

/**
 * If the action has a destination attribute set, return it.
 * Otherwise, return the popupto attribute if the pop is non-inclusive
 */
val NlComponent.effectiveDestinationId: String?
  get() {
    actionDestinationId?.let { return it }
    return if (inclusive) null else popUpTo
  }

@VisibleForTesting
class NavComponentMixin(component: NlComponent)
  : NlComponent.XmlModelComponentMixin(component) {

  private val includeAttrs: Table<String, String, String>? by lazy(fun(): Table<String, String, String>? {
    val xmlFile = component.includeFile ?: return null
    val result: Table<String, String, String> = HashBasedTable.create()
    xmlFile.rootTag?.attributes?.forEach { result.put(it.namespace, it.localName, it.value) }
    return result
  })

  override fun getAttribute(namespace: String?, attribute: String): String? {
    if (component.tagName == TAG_INCLUDE) {
      if (attribute == NavigationSchema.ATTR_GRAPH) {
        // To avoid recursion
        return null
      }
      return includeAttrs?.get(namespace, attribute)
    }

    return null
  }

  override fun getTooltipText(): String? {
    // TODO
    return null
  }
}

object NavComponentHelper {

  /**
   * Enhance the given [NlComponent] with nav-specific properties and methods.
   *
   * Note: For mocked components, you probably want LayoutTestUtilities.registerNlComponent.
   */
  fun registerComponent(component: NlComponent) {
    component.setMixin(NavComponentMixin(component))
  }
}
