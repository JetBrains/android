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
import com.android.SdkConstants.ATTR_AUTO_VERIFY
import com.android.SdkConstants.ATTR_DEEPLINK_ACTION
import com.android.SdkConstants.ATTR_DEEPLINK_MIMETYPE
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_NULLABLE
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.ATTR_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.NAVIGATION_PREFIX
import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.BooleanAttributeDelegate
import com.android.tools.idea.common.model.BooleanAutoAttributeDelegate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.StringAttributeDelegate
import com.android.tools.idea.common.model.StringAutoAttributeDelegate
import com.android.tools.idea.naveditor.analytics.MetricsLoggingAttributeDelegate
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.model.createChild
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.DELETE_ACTION
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.DELETE_ARGUMENT
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.DELETE_DEEPLINK
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.DELETE_DESTINATION
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.DELETE_INCLUDE
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.DELETE_NESTED
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import icons.StudioIcons.NavEditor.Properties.ACTION
import icons.StudioIcons.NavEditor.Tree.ACTIVITY
import icons.StudioIcons.NavEditor.Tree.FRAGMENT
import icons.StudioIcons.NavEditor.Tree.INCLUDE_GRAPH
import icons.StudioIcons.NavEditor.Tree.NESTED_GRAPH
import icons.StudioIcons.NavEditor.Tree.PLACEHOLDER
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.navigation.DeeplinkElement
import org.jetbrains.android.dom.navigation.NavActionElement
import org.jetbrains.android.dom.navigation.NavArgumentElement
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_SINGLE_TOP
import org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ACTION
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import org.jetbrains.android.dom.navigation.NavigationSchema.get
import java.io.File
import java.util.function.Consumer
import javax.swing.Icon

private const val ADD_NESTED_COMMAND_NAME = "Add to Nested Graph"
private const val ADD_NESTED_GROUP_ID = "ADD_NESTED_GROUP_ID"

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
  val schema = get(model.module)
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
    return runReadAction { PsiManager.getInstance(model.project).findFile(vFile) } as? XmlFile
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
  get() = tagName == TAG_ACTION

val NlComponent.isArgument: Boolean
  get() = tagName == TAG_ARGUMENT

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

fun NlComponent.getArgumentNames() : List<String> {
  return children.filter { it.tagName == TAG_ARGUMENT }.mapNotNull { it.argumentName }
}

private fun NlComponent.containsDestination(destinationId: String): Boolean {
  return children.map { it.id }.contains(destinationId)
}


private val actionDestinationIdDelegate =
  MetricsLoggingAttributeDelegate(::IdAutoAttributeDelegate, ATTR_DESTINATION,
                                  NlComponent::actionDestinationId)
var NlComponent.actionDestinationId: String? by actionDestinationIdDelegate
fun NlComponent.setActionDestinationIdAndLog(value: String?, site: NavEditorEvent.Source) =
  actionDestinationIdDelegate.set(this, value, site)

private val nameDelegate = MetricsLoggingAttributeDelegate(::StringAttributeDelegate,
                                                           ANDROID_URI, ATTR_NAME,
                                                           NlComponent::className)
var NlComponent.className: String? by nameDelegate
fun NlComponent.setClassNameAndLog(value: String?, site: NavEditorEvent.Source) = nameDelegate.set(this, value, site)

var NlComponent.argumentName: String? by nameDelegate
fun NlComponent.setArgumentNameAndLog(value: String?, site: NavEditorEvent.Source) = nameDelegate.set(this, value, site)

private val layoutDelegate = MetricsLoggingAttributeDelegate(::StringAttributeDelegate,
                                                             TOOLS_URI, ATTR_LAYOUT,
                                                             NlComponent::layout)
var NlComponent.layout: String? by layoutDelegate
fun NlComponent.setLayoutAndLog(value: String?, site: NavEditorEvent.Source) = layoutDelegate.set(this, value, site)

private val enterAnimationDelegate =
  MetricsLoggingAttributeDelegate(::StringAutoAttributeDelegate, ATTR_ENTER_ANIM,
                                  NlComponent::enterAnimation)
var NlComponent.enterAnimation: String? by enterAnimationDelegate
fun NlComponent.setEnterAnimationAndLog(value: String?, site: NavEditorEvent.Source) = enterAnimationDelegate.set(this, value, site)

private val exitAnimationDelegate =
  MetricsLoggingAttributeDelegate(::StringAutoAttributeDelegate, ATTR_EXIT_ANIM,
                                  NlComponent::exitAnimation)
var NlComponent.exitAnimation: String? by exitAnimationDelegate
fun NlComponent.setExitAnimationAndLog(value: String?, site: NavEditorEvent.Source) = exitAnimationDelegate.set(this, value, site)

private val popUpToDelegate = MetricsLoggingAttributeDelegate(::IdAutoAttributeDelegate,
                                                              ATTR_POP_UP_TO,
                                                              NlComponent::popUpTo)
var NlComponent.popUpTo: String? by popUpToDelegate
fun NlComponent.setPopUpToAndLog(value: String?, site: NavEditorEvent.Source) = popUpToDelegate.set(this, value, site)

private val inclusiveDelegate =
  MetricsLoggingAttributeDelegate(::BooleanAutoAttributeDelegate, ATTR_POP_UP_TO_INCLUSIVE,
                                  NlComponent::inclusive)
var NlComponent.inclusive: Boolean? by inclusiveDelegate
fun NlComponent.setInclusiveAndLog(value: Boolean?, site: NavEditorEvent.Source) = inclusiveDelegate.set(this, value, site)

private val popEnterAnimationDelegate =
  MetricsLoggingAttributeDelegate(::StringAutoAttributeDelegate, ATTR_POP_ENTER_ANIM,
                                  NlComponent::popEnterAnimation)
var NlComponent.popEnterAnimation: String? by popEnterAnimationDelegate
fun NlComponent.setPopEnterAnimationAndLog(value: String?, site: NavEditorEvent.Source) = popEnterAnimationDelegate.set(this, value, site)

private val popExitAnimationDelegate =
  MetricsLoggingAttributeDelegate(::StringAutoAttributeDelegate, ATTR_POP_EXIT_ANIM,
                                  NlComponent::popExitAnimation)
var NlComponent.popExitAnimation: String? by popExitAnimationDelegate
fun NlComponent.setPopExitAnimationAndLog(value: String?, site: NavEditorEvent.Source) = popExitAnimationDelegate.set(this, value, site)

private val singleTopDelegate = MetricsLoggingAttributeDelegate(::BooleanAutoAttributeDelegate,
                                                                ATTR_SINGLE_TOP,
                                                                NlComponent::singleTop)
var NlComponent.singleTop: Boolean? by singleTopDelegate
fun NlComponent.setSingleTopAndLog(value: Boolean?, site: NavEditorEvent.Source) = singleTopDelegate.set(this, value, site)

private val typeDelegate = MetricsLoggingAttributeDelegate(::StringAutoAttributeDelegate,
                                                           ATTR_ARG_TYPE, NlComponent::typeAttr)
var NlComponent.typeAttr: String? by typeDelegate
fun NlComponent.setTypeAndLog(value: String?, site: NavEditorEvent.Source) = typeDelegate.set(this, value, site)

private val defaultValueDelegate =
  MetricsLoggingAttributeDelegate(::StringAttributeDelegate, ANDROID_URI, ATTR_DEFAULT_VALUE,
                                  NlComponent::defaultValue)
var NlComponent.defaultValue: String? by defaultValueDelegate
fun NlComponent.setDefaultValueAndLog(value: String?, site: NavEditorEvent.Source) = defaultValueDelegate.set(this, value, site)

private val nullableDelegate = MetricsLoggingAttributeDelegate(::BooleanAutoAttributeDelegate,
                                                               ATTR_NULLABLE,
                                                               NlComponent::nullable)
var NlComponent.nullable: Boolean? by nullableDelegate
fun NlComponent.setNullableAndLog(value: Boolean?, site: NavEditorEvent.Source) = nullableDelegate.set(this, value, site)

private val startDestinationIdDelegate =
  MetricsLoggingAttributeDelegate(::IdAutoAttributeDelegate, ATTR_START_DESTINATION,
                                  NlComponent::startDestinationId)
var NlComponent.startDestinationId: String? by startDestinationIdDelegate
fun NlComponent.setStartDestinationIdAndLog(value: String?, site: NavEditorEvent.Source) =
  startDestinationIdDelegate.set(this, value, site)

private val autoVerifyDelegate =
  MetricsLoggingAttributeDelegate(::BooleanAttributeDelegate, ANDROID_URI, ATTR_AUTO_VERIFY, NlComponent::autoVerify)
var NlComponent.autoVerify: Boolean? by autoVerifyDelegate
fun NlComponent.setAutoVerifyAndLog(value: Boolean?, site: NavEditorEvent.Source) = autoVerifyDelegate.set(this, value, site)

private val uriDelegate = MetricsLoggingAttributeDelegate(::StringAutoAttributeDelegate, ATTR_URI, NlComponent::uri)
var NlComponent.uri: String? by uriDelegate
fun NlComponent.setUriAndLog(value: String?, site: NavEditorEvent.Source) = uriDelegate.set(this, value, site)

private val deepLinkMimeTypeDelegate =
  MetricsLoggingAttributeDelegate(::StringAttributeDelegate, AUTO_URI, ATTR_DEEPLINK_MIMETYPE, NlComponent::deepLinkMimeType)
var NlComponent.deepLinkMimeType: String? by deepLinkMimeTypeDelegate
fun NlComponent.setDeeplinkMimeTypeAndLog(value: String?, site: NavEditorEvent.Source)
  = deepLinkMimeTypeDelegate.set(this, value, site)

private val deepLinkActionDelegate =
  MetricsLoggingAttributeDelegate(::StringAttributeDelegate, AUTO_URI, ATTR_DEEPLINK_ACTION, NlComponent::deepLinkAction)
var NlComponent.deepLinkAction: String? by deepLinkActionDelegate
fun NlComponent.setDeeplinkActionAndLog(value: String?, site: NavEditorEvent.Source)
  = deepLinkActionDelegate.set(this, value, site)

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
fun NlComponent.createAction(destinationId: String? = null, id: String? = null, actionSetup: NlComponent.() -> Unit = {}): NlComponent? {
  val newAction = createChild(TAG_ACTION)
  newAction?.ensureId()
  if (newAction == null) {
    ApplicationManager.getApplication().invokeLater {
      Messages.showErrorDialog(model.project, "Failed to create Action!", "Error")
    }
    return null
  }
  newAction.actionDestinationId = destinationId
  newAction.actionSetup()
  // TODO: it would be nice if, when we changed something affecting the below logic and the id hasn't been changed,
  // we could update the id as a refactoring so references are also updated.
  newAction.assignId(id ?: generateActionId(this, newAction.actionDestinationId, newAction.popUpTo, newAction.inclusive ?: false))
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

fun NlComponent.createSelfAction(): NlComponent? {
  return createAction(id)
}

fun NlComponent.createReturnToSourceAction(): NlComponent? {
  return createAction {
    popUpTo = parent?.id
    inclusive = true
  }
}

fun NlComponent.setAsStartDestination() {
  parent?.startDestinationId = id
}

fun NlComponent.createNestedGraph(): NlComponent? {
  val newComponent = createChild(model.schema.getDefaultTag(DestinationType.NAVIGATION)!!)
  newComponent?.ensureId()
  if (newComponent == null) {
    ApplicationManager.getApplication().invokeLater {
      Messages.showErrorDialog(model.project, "Failed to create Nested Graph!", "Error")
    }
  }
  return newComponent
}

val NlComponent.supportsActions: Boolean
  get() = this.supportsElement(NavActionElement::class.java)

val NlComponent.supportsArguments: Boolean
  get() = this.supportsElement(NavArgumentElement::class.java)

val NlComponent.supportsDeeplinks: Boolean
  get() = this.supportsElement(DeeplinkElement::class.java)

private fun NlComponent.supportsElement(element: Class<out AndroidDomElement>) =
  model.schema.getDestinationSubtags(tagName).containsKey(element)

/**
 * If the action has a destination attribute set, return it.
 * Otherwise, return the popupto attribute if the pop is non-inclusive
 */
val NlComponent.effectiveDestinationId: String?
  get() {
    actionDestinationId?.let { return it }
    return if (inclusive == true) null else popUpTo
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
fun moveIntoNestedGraph(surface: NavDesignSurface, newParent: () -> NlComponent?): Boolean {
  val currentNavigation = surface.currentNavigation
  val components = surface.selectionModel.selection.filter { it.isDestination && it.parent == currentNavigation }

  if (components.isEmpty()) {
    return false
  }

  WriteCommandAction.runWriteCommandAction(surface.project, ADD_NESTED_COMMAND_NAME, ADD_NESTED_GROUP_ID, Runnable {
    val graph = newParent() ?: return@Runnable
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

    graph.model.addComponents(components, graph, null, InsertType.MOVE, null, null, ADD_NESTED_GROUP_ID)
    if (graph.startDestinationId == null) {
      graph.startDestinationId = candidate
    }
    surface.selectionModel.setSelection(listOf(graph))

  }, surface.model!!.file)
  return true
}

@VisibleForTesting
class NavComponentMixin(component: NlComponent)
  : NlComponent.XmlModelComponentMixin(component) {

  override fun maybeHandleDeletion(children: Collection<NlComponent>): Boolean {
    children.forEach {
      val tracker = NavUsageTracker.getInstance(it.model)
      when {
        it.isAction -> tracker.createEvent(DELETE_ACTION).withActionInfo(it)
        it.isInclude -> tracker.createEvent(DELETE_INCLUDE)
        it.isNavigation -> tracker.createEvent(DELETE_NESTED)
        it.isDestination -> tracker.createEvent(DELETE_DESTINATION).withDestinationInfo(it)
        it.tagName == TAG_DEEP_LINK -> tracker.createEvent(DELETE_DEEPLINK)
        it.tagName == TAG_ARGUMENT -> tracker.createEvent(DELETE_ARGUMENT)
        else -> null
      }?.log()
    }
    return false
  }

  private val includeAttrs: Table<String, String, String>? by lazy(fun(): Table<String, String, String>? {
    val xmlFile = component.includeFile ?: return null
    val result: Table<String, String, String> = HashBasedTable.create()
    xmlFile.rootTag?.attributes?.forEach { it.value?.let { value -> result.put(it.namespace, it.localName, value) } }
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

  override fun getIcon(): Icon {
    return when {
      component.isInclude -> INCLUDE_GRAPH
      component.isNavigation -> NESTED_GRAPH
      component.isAction -> ACTION
      component.className == null -> PLACEHOLDER
      component.isActivity -> ACTIVITY
      else -> FRAGMENT
    }
  }
}

/**
 * Enhance the given [NlComponent] with nav-specific properties and methods.
 *
 * Note: For mocked components, you probably want LayoutTestUtilities.registerNlComponent.
 */
object NavComponentRegistrar : Consumer<NlComponent> {
  override fun accept(component: NlComponent) {
    component.setMixin(NavComponentMixin(component))
  }
}