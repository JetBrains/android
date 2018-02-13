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
import com.android.annotations.VisibleForTesting
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.ResourceType
import com.android.support.AndroidxName
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.api.*
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

/*
 * Layout editor-specific helper methods and data for NlComponent
 */

/**
 * Regex to get the base name of a component id, where the basename of
 * "component123" is "component"
 */
private val BASE_ID_PATTERN = Regex("(.*[^0-9])([0-9]+)?")

@AndroidCoordinate
var NlComponent.x: Int
  get() = this.nlComponentData.x
  set(value) {
    this.nlComponentData.x = value
  }

@AndroidCoordinate
var NlComponent.y: Int
  get() = this.nlComponentData.y
  set(value) {
    this.nlComponentData.y = value
  }

var NlComponent.w: Int
  get() = this.nlComponentData.w
  set(value) {
    this.nlComponentData.w = value
  }

var NlComponent.h: Int
  get() = this.nlComponentData.h
  set(value) {
    this.nlComponentData.h = value
  }

var NlComponent.viewInfo: ViewInfo?
  get() = this.nlComponentData.viewInfo
  set(value) {
    this.nlComponentData.viewInfo = value
  }

fun NlComponent.setBounds(x: Int, y: Int, w: Int, h: Int) {
  this.x = x
  this.y = y
  this.w = w
  this.h = h
}

fun NlComponent.contains(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int): Boolean {
  return containsX(x) && containsY(y)
}

fun NlComponent.containsX(@AndroidCoordinate x: Int): Boolean {
  return Ranges.contains(this.x, this.x + w, x)
}

fun NlComponent.containsY(@AndroidCoordinate y: Int): Boolean {
  return Ranges.contains(this.y, this.y + h, y)
}

/**
 * Determines whether the given new component should have an id attribute.
 * This is generally false for layouts, and generally true for other views,
 * not including the `<include>` and `<merge>` tags. Note that
 * `<fragment>` tags **should** specify an id.

 * @return true if the component should have a default id
 */
fun NlComponent.needsDefaultId(): Boolean {
  if (!hasNlComponentInfo) {
    return false
  }
  if (NlComponentHelper.TAGS_THAT_DONT_NEED_DEFAULT_IDS.contains(tagName)) {
    return false
  }

  // Handle <Space> in the compatibility library b
  if (tagName.endsWith(SPACE) && tagName.length > SPACE.length && tagName[tagName.length - SPACE.length] == '.') {
    return false
  }

  // Assign id's to ViewGroups like ListViews, but not to views like LinearLayout
  if (viewHandler == null) {
    if (tagName.endsWith("Layout")) {
      return false
    }
  }
  else if (viewHandler is ViewGroupHandler) {
    return false
  }

  return true
}

/**
 * Returns the basename of a component id, where the basename of
 * "component123" is "component" or null if the id is empty or null, or no baseName can be found
 */
fun NlComponent.getBaseIdName(): String? {
  return this.id?.let {
    return BASE_ID_PATTERN.find(it)?.groups?.get(1)?.value
  }
}

/**
 * Ensure that there's a id, if not execute a write command to add
 * the id to the component.

 * @return
 */
fun NlComponent.ensureLiveId(): String {
  id?.let { return id!! }

  val attributes = startAttributeTransaction()
  val id = assignId()
  attributes.apply()

  NlWriteCommandAction.run(this, "Added ID", { attributes.commit() })
  return id
}

@AndroidCoordinate
fun NlComponent.getBaseline(): Int {
  try {
    val viewObject = viewInfo?.viewObject ?: return -1
    return viewObject.javaClass.getMethod("getBaseline").invoke(viewObject) as Int
  }
  catch (ignore: Throwable) {
  }

  return -1
}

private fun fixDefault(value: Int): Int {
  return if (value == Integer.MIN_VALUE) 0 else value
}

val NlComponent.margins: Insets
  get() {
    var result = this.nlComponentData.margins
    if (result == null) {
      val viewInfo = viewInfo
      if (viewInfo == null) {
        result = Insets.NONE
      }
      else {
        try {
          val layoutParams = viewInfo.layoutParamsObject
          val layoutClass = layoutParams.javaClass

          val left = fixDefault(layoutClass.getField("leftMargin").getInt(layoutParams))
          val top = fixDefault(layoutClass.getField("topMargin").getInt(layoutParams))
          val right = fixDefault(layoutClass.getField("rightMargin").getInt(layoutParams))
          val bottom = fixDefault(layoutClass.getField("bottomMargin").getInt(layoutParams))
          // Doesn't look like we need to read startMargin and endMargin here;
          // ViewGroup.MarginLayoutParams#doResolveMargins resolves and assigns values to the others

          result = if (left == 0 && top == 0 && right == 0 && bottom == 0) {
            Insets.NONE
          }
          else {
            Insets(left, top, right, bottom)
          }
        }
        catch (e: Throwable) {
          result = Insets.NONE
        }
      }
      this.nlComponentData.margins = result
    }
    return result!!
  }

val NlComponent.padding: Insets
  get() = getPadding(false)

fun NlComponent.getPadding(force: Boolean): Insets {
  var result = this.nlComponentData.padding
  val viewInfo = viewInfo
  if (result == null || force) {
    if (viewInfo == null) {
      return Insets.NONE
    }
    try {
      val layoutParams = viewInfo.viewObject
      val layoutClass = layoutParams.javaClass

      val left = fixDefault(layoutClass.getMethod("getPaddingLeft").invoke(layoutParams) as Int) // TODO: getPaddingStart!
      val top = fixDefault(layoutClass.getMethod("getPaddingTop").invoke(layoutParams) as Int)
      val right = fixDefault(layoutClass.getMethod("getPaddingRight").invoke(layoutParams) as Int)
      val bottom = fixDefault(layoutClass.getMethod("getPaddingBottom").invoke(layoutParams) as Int)
      result = if (left == 0 && top == 0 && right == 0 && bottom == 0) {
        Insets.NONE
      }
      else {
        Insets(left, top, right, bottom)
      }
    }
    catch (e: Throwable) {
      result = Insets.NONE
    }
    this.nlComponentData.padding = result
  }
  return result!!
}

fun NlComponent.isGroup(): Boolean {
  if (isOrHasSuperclass(CLASS_VIEWGROUP)) {
    return true
  }

  return when (tagName) {
    PreferenceTags.PREFERENCE_CATEGORY,
    PreferenceTags.PREFERENCE_SCREEN,
    TAG_GROUP,
    TAG_MENU,
    TAG_SELECTOR -> true
    else -> false
  }
}

/**
 * Returns true if this NlComponent's class is the specified class,
 * or if one of its super classes is the specified class.

 * @param className A fully qualified class name
 */
fun NlComponent.isOrHasSuperclass(className: String): Boolean {
  val viewInfo = viewInfo
  if (viewInfo != null) {
    val viewObject = viewInfo.viewObject ?: return ApplicationManager.getApplication().isUnitTestMode && tagName == className
    var viewClass: Class<*> = viewObject.javaClass
    while (viewClass != Any::class.java) {
      if (className == viewClass.name) {
        return true
      }
      viewClass = viewClass.superclass
    }
  }
  return false
}

/**
 * Returns true if this NlComponent's class is the specified class,
 * or if one of its super classes is the specified class.

 * @param className A fully qualified class name
 */
fun NlComponent.isOrHasSuperclass(className: AndroidxName): Boolean {
  return isOrHasSuperclass(className.oldName()) || isOrHasSuperclass(className.newName())
}

/**
 * Returns true if this NlComponent's class has a class in the androidx. namespace
 */
fun NlComponent.isOrHasAndroidxSuperclass(): Boolean {
  val viewInfo = viewInfo
  if (viewInfo != null) {
    val viewObject = viewInfo.viewObject ?: return ApplicationManager.getApplication().isUnitTestMode && tagName.startsWith(
        ANDROIDX_PKG_PREFIX)
    var viewClass: Class<*> = viewObject.javaClass
    while (viewClass != Any::class.java) {
      if (viewClass.name.startsWith(ANDROIDX_PKG_PREFIX)) {
        return true
      }
      viewClass = viewClass.superclass
    }
  }
  return false
}

/**
 * Returns the class within the className set that is the
 * the most derived (specific) class that matches NlComponent's class

 * @param classNames Set of class names to search
 */
fun NlComponent.getMostSpecificClass(classNames: Set<String>): String? {
  val viewInfo = viewInfo
  if (viewInfo != null) {
    val viewObject = viewInfo.viewObject ?: return null
    var viewClass: Class<*> = viewObject.javaClass
    while (viewClass != Any::class.java) {
      if (classNames.contains(viewClass.name)) {
        return viewClass.name
      }
      viewClass = viewClass.superclass
    }
  }
  return null
}

val NlComponent.viewHandler: ViewHandler?
  get() {
    return ApplicationManager.getApplication().runReadAction(Computable{
      if (!tag.isValid) {
        null
      }
      else {
        ViewHandlerManager.get(tag.project).getHandler(this)
      }
    })
  }

val NlComponent.viewGroupHandler: ViewGroupHandler?
  get() {
    @Suppress("SENSELESS_COMPARISON")
    // tag can be null for a mock component. To avoid the need of creating a fully functionnal mock XmlTag
    // that passes all tests, we check the nullity.
    if (tag == null || !tag.isValid) {
      return null
    }
    return ViewHandlerManager.get(tag.project).findLayoutHandler(this, false)
  }

/**
 * Creates a new child of the given type, and inserts it before the given sibling (or null to append at the end).
 * Note: This operation can only be called when the caller is already holding a write lock. This will be the
 * case from [ViewHandler] callbacks such as [ViewHandler.onCreate]
 * and [DragHandler.commit].

 * @param editor     The editor showing the component
 * *
 * @param fqcn       The fully qualified name of the widget to insert, such as `android.widget.LinearLayout`
 * *                   You can also pass XML tags here (this is typically the same as the fully qualified class name
 * *                   of the custom view, but for Android framework views in the android.view or android.widget packages,
 * *                   you can omit the package.)
 * *
 * @param before     The sibling to insert immediately before, or null to append
 * *
 * @param insertType The type of insertion
 */
fun NlComponent.createChild(editor: ViewEditor,
                            fqcn: String,
                            before: NlComponent?,
                            insertType: InsertType
): NlComponent? {
  val tagName = NlComponentHelper.viewClassToTag(fqcn)
  val tag = tag.createChildTag(tagName, null, null, false)

  return model.createComponent(editor, tag, this, before, insertType)
}

fun NlComponent.clearAttributes() {
  viewGroupHandler?.clearAttributes(this)
}

val NlComponent.hasNlComponentInfo: Boolean
  get() = NlComponentHelper.hasNlComponentInfo(this)

/**
 * @return true if the receiver can be safely morphed into a view group
 */
val NlComponent.isMorphableToViewGroup: Boolean
  get() = VIEW == tagName && getAttribute(TOOLS_URI, ATTR_MOCKUP) != null

val NlComponent.componentClassName: String?
  get() = viewInfo?.className

private val NlComponent.nlComponentData: NlComponentData
  get() {
    val mixin = this.mixin
    return when (mixin) {
      is NlComponentMixin -> mixin.data
      else -> throw IllegalArgumentException("${this} is not registered!")
    }
  }

internal data class NlComponentData(
    var x: Int = 0,
    var y: Int = 0,
    var w: Int = 0,
    var h: Int = 0,
    var viewInfo: ViewInfo? = null,
    var margins: Insets? = null,
    var padding: Insets? = null)

@VisibleForTesting
class NlComponentMixin(component: NlComponent)
  : NlComponent.XmlModelComponentMixin(component) {
  internal val data: NlComponentData = NlComponentData()

  override fun toString(): String {
    return String.format("%s (%s, %s) %s × %s", super.toString(), data.x, data.y, data.w, data.h)
  }

  override fun getAttribute(namespace: String?, attribute: String): String? {
    val styleAttributeValue = component.getAttribute(null, "style") ?: return null

    val resources = component.model.configuration.resourceResolver ?: return null

    // Pretend the style was referenced from a proper resource by constructing a temporary ResourceValue. TODO: aapt namespace?
    val tmpResourceValue = ResourceValue(ResourceNamespace.TODO, ResourceType.STYLE, component.tagName, styleAttributeValue)

    val styleResourceValue = resources.resolveResValue(tmpResourceValue) as? StyleResourceValue ?: return null

    val itemResourceValue = resources.findItemInStyle(styleResourceValue, attribute, true) ?: return null

    return itemResourceValue.value
  }

  override fun getTooltipText(): String? {
    component.id?.let { return it }
    val str = component.componentClassName ?: return null
    return str.substring(str.lastIndexOf('.') + 1)
  }

  override fun canAddTo(receiver: NlComponent): Boolean {
    if (!receiver.hasNlComponentInfo) {
      return false
    }
    val parentHandler = receiver.viewHandler as? ViewGroupHandler ?: return false

    if (!parentHandler.acceptsChild(receiver, component)) {
      return false
    }

    val handler = ViewHandlerManager.get(component.model.project).getHandler(component)

    if (handler != null && !handler.acceptsParent(receiver, component)) {
      return false
    }
    return true
  }

  /**
   * Find the Gradle dependency for the given component and return them as a list of String
   */
  override fun getDependencies(): Set<String> {
    val artifacts = mutableSetOf<String>()
    val handler = ViewHandlerManager.get(component.model.project).getHandler(component) ?: return emptySet()
    val artifactId = handler.getGradleCoordinateId(component.tag.name)
    if (artifactId != PaletteComponentHandler.IN_PLATFORM) {
      artifacts.add(artifactId)
    }
    component.children.flatMap { it.dependencies }.toCollection(artifacts)

    return artifacts.toSet()
  }

  override fun beforeMove(insertType: InsertType, receiver: NlComponent, ids: MutableSet<String>) {
    var realInsertType = insertType
    if (insertType.isMove) {
      realInsertType = if (component.parent === receiver) InsertType.MOVE_WITHIN else InsertType.MOVE_INTO
    }

    // AssignId
    if (component.needsDefaultId() && !realInsertType.isMove) {
      val id = component.id
      if (id == null || id.isEmpty()) {
        ids.add(component.assignId(ids))
      } else {
        val baseName = component.getBaseIdName()
        if (baseName != null && !baseName.isEmpty()) {
          ids.add(component.assignId(baseName, ids))
        }
      }
    }
  }

  override fun afterMove(insertType: InsertType, receiver: NlComponent, surface: DesignSurface?) {
    var realInsertType = insertType
    if (insertType.isMove) {
      realInsertType = if (component.parent === receiver) InsertType.MOVE_WITHIN else InsertType.MOVE_INTO
    }
    receiver.viewGroupHandler?.onChildInserted(ViewEditorImpl(component.model, surface?.scene), receiver, component, realInsertType)
  }
}

object NlComponentHelper {

  /**
   * Enhance the given [NlComponent] with layout-specific properties and methods.
   *
   * Note: For mocked components, you probably want LayoutTestUtilities.registerNlComponent.
   */
  fun registerComponent(component: NlComponent) {
    component.setMixin(NlComponentMixin(component))
  }

  // TODO Add a needsId method to the handler classes
  val TAGS_THAT_DONT_NEED_DEFAULT_IDS: Collection<String> = ImmutableSet.Builder<String>()
      .add(REQUEST_FOCUS)
      .add(SPACE)
      .add(TAG_ITEM)
      .add(VIEW_INCLUDE)
      .add(VIEW_MERGE)
      .addAll(PreferenceUtils.VALUES)
      .build()

  /**
   * Maps a custom view class to the corresponding layout tag;
   * e.g. `android.widget.LinearLayout` maps to just `LinearLayout`, but
   * `android.support.v4.widget.DrawerLayout` maps to
   * `android.support.v4.widget.DrawerLayout`.

   * @param fqcn fully qualified class name
   * *
   * @return the corresponding view tag
   */
  fun viewClassToTag(fqcn: String): String {
    if (!viewNeedsPackage(fqcn)) {
      return fqcn.substring(fqcn.lastIndexOf('.') + 1)
    }

    return fqcn
  }

  /**
   * Returns true if views with the given fully qualified class name need to include
   * their package in the layout XML tag

   * @param fqcn the fully qualified class name, such as android.widget.Button
   * *
   * @return true if the full package path should be included in the layout XML element
   * * tag
   */
  private fun viewNeedsPackage(fqcn: String): Boolean {
    return !(fqcn.startsWith(ANDROID_WIDGET_PREFIX)
        || fqcn.startsWith(ANDROID_VIEW_PKG)
        || fqcn.startsWith(ANDROID_WEBKIT_PKG))
  }

  fun hasNlComponentInfo(component: NlComponent): Boolean {
    return component.mixin is NlComponentMixin
  }
}
