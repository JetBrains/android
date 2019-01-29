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
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ARG_TYPE
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_NULLABLE
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.NAVIGATION_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.BooleanAutoAttributeDelegate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.StringAttributeDelegate
import com.android.tools.idea.common.model.StringAutoAttributeDelegate
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.model.IdAutoAttributeDelegate
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavActionElement
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import java.io.File
import kotlin.streams.toList

/*
 * Extensions to NlComponent used by the navigation editor
 */

/**
 * This is an enumeration indicating the type of action represented by the specified NlComponent when viewed from a given context.
 * In order of decreasing precedence:
 * NONE: This tag is either not an action or is invalid.
 * SELF: The destination attribute refers to the action's parent.
 * GLOBAL: The action's parent is the current view context.
 * REGULAR: The destination attribute refers to a sibling of the action's parent.
 * EXIT: The destination attribute refers to an element that is not under the current view context.
 * EXIT_DESTINATION: The destination attribute refers to a child of the current view context, but the source is a (great-)grandchild.
 *
 */
enum class ActionType {
  NONE,
  SELF,
  GLOBAL,
  REGULAR,
  EXIT,
  EXIT_DESTINATION
}

val NlComponent.uiName: String
get() =  id
      ?: resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)?.substringAfterLast(".")
      ?: tagName

/**
 * Creates a map of the visible destinations
 * The keys make up the parent chain to the root.
 * Each value is a list of visible destinations under the key sorted by UiName
 * If a destination appears as a key, it will not appear in
 * the values list under its parent
 * i.e. if B and C are children of A, then the visible destination map
 * of B will be:
 *     B -> { }
 *     A -> { C } (no B)
 *
 */
val NlComponent.visibleDestinations: Map<NlComponent, List<NlComponent>>
  get() {
    val map = HashMap<NlComponent, List<NlComponent>>()
    val current: NlComponent? = if (isDestination) this else parent

    current?.parentSequence()?.forEach {
      map[it] = it.children.filter { it.isDestination && !map.containsKey(it) }
        .sortedBy { it.uiName }
    }

    return map
  }

fun NlComponent.findVisibleDestination(id: String): NlComponent? {
  val schema = NavigationSchema.get(model.module)
  var p = parent
  while (p != null) {
    p.children.firstOrNull { c -> !schema.getDestinationTypesForTag(c.tagName).isEmpty() && c.id == id }?.let { return it }
    p = p.parent
  }
  // The above won't pick up the root
  return model.components.firstOrNull { c -> c.id == id }
}

/**
 * Attempts to find the best []DestinationType] for this component
 */
val NlComponent.destinationType: NavigationSchema.DestinationType?
  get() {
    val schema = model.schema
    var type = className?.let { schema.getDestinationTypeForDestinationClassName(it) }

    if (type == null) {
      val typeCollection = schema.getDestinationTypesForTag(tagName)
      if (typeCollection.size == 1) {
        type = typeCollection.first()
      }
    }
    return type
  }

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
    val actualStart = parent?.startDestinationId
    return actualStart != null && actualStart == id
  }

val NlComponent.isDestination: Boolean
  get() = destinationType != null

val NlComponent.isAction: Boolean
  get() = tagName == NavigationSchema.TAG_ACTION

val NlComponent.isArgument: Boolean
  get() = tagName == NavigationSchema.TAG_ARGUMENT

val NlComponent.isFragment: Boolean
  get() = model.schema.isFragmentTag(tagName)

val NlComponent.isActivity: Boolean
  get() = model.schema.isActivityTag(tagName)

val NlComponent.isNavigation: Boolean
  get() = model.schema.isNavigationTag(tagName)

val NlComponent.isOther: Boolean
  get() = model.schema.isOtherTag(tagName)

val NlComponent.isInclude: Boolean
  get() = model.schema.isIncludeTag(tagName)

val NlComponent.isSelfAction: Boolean
  get() = getActionType(null) == ActionType.SELF

fun NlComponent.getActionType(currentRoot: NlComponent?): ActionType {
    if (!isAction) {
      return ActionType.NONE
    }

    val parent = parent ?: throw IllegalStateException()

    val destination = effectiveDestinationId ?: return ActionType.EXIT
    if (parent.id == destination) {
      return ActionType.SELF
    }

    if (currentRoot == null) {
      return ActionType.NONE
    }

    if (parent.isNavigation && parent == currentRoot) {
      return ActionType.GLOBAL
    }

    if (currentRoot.containsDestination(destination)) {
      return if (parent.parent == currentRoot) ActionType.REGULAR else ActionType.EXIT_DESTINATION
    }

    return ActionType.EXIT
  }

private fun NlComponent.containsDestination(destinationId: String): Boolean {
  return children.map { it.id }.contains(destinationId)
}

var NlComponent.actionDestinationId: String? by IdAutoAttributeDelegate(NavigationSchema.ATTR_DESTINATION)
var NlComponent.className: String? by StringAttributeDelegate(ANDROID_URI, ATTR_NAME)
var NlComponent.argumentName: String? by StringAttributeDelegate(ANDROID_URI, ATTR_NAME)
var NlComponent.layout: String? by StringAttributeDelegate(TOOLS_URI, ATTR_LAYOUT)
var NlComponent.enterAnimation: String? by StringAutoAttributeDelegate(NavigationSchema.ATTR_ENTER_ANIM)
var NlComponent.exitAnimation: String? by StringAutoAttributeDelegate(NavigationSchema.ATTR_EXIT_ANIM)
// TODO: Use IdAutoAttributeDelegate for popUpTo
var NlComponent.popUpTo: String? by IdAutoAttributeDelegate(NavigationSchema.ATTR_POP_UP_TO)
var NlComponent.inclusive: Boolean by BooleanAutoAttributeDelegate(NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE)
var NlComponent.popEnterAnimation: String? by StringAutoAttributeDelegate(NavigationSchema.ATTR_POP_ENTER_ANIM)
var NlComponent.popExitAnimation: String? by StringAutoAttributeDelegate(NavigationSchema.ATTR_POP_EXIT_ANIM)
var NlComponent.singleTop: Boolean by BooleanAutoAttributeDelegate(NavigationSchema.ATTR_SINGLE_TOP)
var NlComponent.typeAttr: String? by StringAttributeDelegate(AUTO_URI, ATTR_ARG_TYPE)
var NlComponent.defaultValue: String? by StringAttributeDelegate(ANDROID_URI, ATTR_DEFAULT_VALUE)
var NlComponent.nullable: Boolean by BooleanAutoAttributeDelegate(ATTR_NULLABLE)

var NlComponent.startDestinationId: String? by IdAutoAttributeDelegate(ATTR_START_DESTINATION)

val NlComponent.actionDestination: NlComponent?
  get() {
    assert(isAction)
    val targetId = actionDestinationId ?: return null
    return findVisibleDestination(targetId)
  }

val NlComponent.effectiveDestination: NlComponent?
  get() {
    assert(isAction)
    val targetId = effectiveDestinationId ?: return null
    return findVisibleDestination(targetId)
  }

fun NlComponent.getEffectiveSource(currentRoot: NlComponent): NlComponent? {
  assert(isAction)
  return parent?.parentSequence()?.find { it.parent == currentRoot }
}

val NlComponent.startDestination: NlComponent?
  get() = startDestinationId?.let { start -> children.find { it.id == start } }

/**
 * [actionSetup] should include everything needed to set the default id (destination, popTo, and popToInclusive).
 */
@JvmOverloads
fun NlComponent.createAction(destinationId: String? = null, id: String? = null, actionSetup: NlComponent.() -> Unit = {}): NlComponent {
  val newAction = createChild(NavigationSchema.TAG_ACTION)
  newAction.actionDestinationId = destinationId
  newAction.actionSetup()
  // TODO: it would be nice if, when we changed something affecting the below logic and the id hasn't been changed,
  // we could update the id as a refactoring so references are also updated.
  newAction.assignId(id ?: generateActionId(this, newAction.actionDestinationId, newAction.popUpTo, newAction.inclusive))
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
  parent?.startDestinationId = id
}

fun NlComponent.createNestedGraph(): NlComponent {
  return createChild(model.schema.getDefaultTag(NavigationSchema.DestinationType.NAVIGATION)!!)
}

val NlComponent.supportsActions: Boolean
  get() = model.schema.getDestinationSubtags(tagName).containsKey(NavActionElement::class.java)

private fun NlComponent.createChild(tagName: String): NlComponent {
  val newTag = tag.createChildTag(tagName, null, null, false)
  val child = model.createComponent(null, newTag, this, null, InsertType.CREATE)
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

/**
 * Sequence of NlComponents starting with this and going down the parent tree to the root.
 */
fun NlComponent.parentSequence(): Sequence<NlComponent> = generateSequence(this) { it.parent }

/**
 * The "path" to this. The first element is the filename, and the following elements are the ids of the elements containing this.
 */
val NlComponent.idPath: List<String?>
  get() = parentSequence().asIterable().reversed().map {
    when {
      it.isRoot -> model.virtualFile.name
      it.isInclude -> it.getAttribute(AUTO_URI, ATTR_GRAPH)?.substring(NAVIGATION_PREFIX.length)
      else -> it.id
    }
  }

/**
 * Moves the currently selected destinations into the nested graph returned from newParent
 * Since newParent may create a new NlComponent it is evaluated inside the run command
 */
fun moveIntoNestedGraph(surface: NavDesignSurface, newParent: () -> NlComponent) {
  val currentNavigation = surface.currentNavigation
  val components = surface.selectionModel.selection.filter { it.isDestination && it.parent == currentNavigation }

  if (components.isEmpty()) {
    return
  }

  WriteCommandAction.runWriteCommandAction(surface.project, "Add to Nested Graph", null, Runnable {
    val graph = newParent()
    val ids = components.map { it.id }
    components.forEach { surface.sceneManager?.performUndoablePositionAction(it) }

    // Pick an arbitrary destination to be the start destination,
    // but give preference to destinations with incoming actions
    // TODO: invoke dialog to have user select the best start destination?
    var candidate = components[0].id

    // All actions that point to any component in this set should now point to the
    // new parent graph, unless they are children of an element in the set
    currentNavigation.children.filter { !ids.contains(it.id) && it != graph }
      .flatMap { it.flatten().toList() }
      .filter { it.isAction && ids.contains(it.actionDestinationId) }
      .forEach {
        candidate = it.actionDestinationId
        it.actionDestinationId = graph.id
      }

    graph.model.addComponents(components, graph, null, InsertType.MOVE_WITHIN, surface)
    if (graph.startDestinationId == null) {
      graph.startDestinationId = candidate
    }
    surface.selectionModel.setSelection(listOf(graph))

  }, surface.model!!.file)
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
    if (component.isInclude) {
      if (attribute == ATTR_GRAPH) {
        // To avoid recursion
        return null
      }
      return includeAttrs?.get(namespace, attribute)
    }

    return null
  }

  override fun beforeMove(insertType: InsertType, receiver: NlComponent, ids: MutableSet<String>) {
    if (receiver.children.any { it.id == component.id }) {
      component.incrementId(ids)
    }
  }

  override fun getTooltipText() = if (component.isAction) component.id else null
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