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
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.*
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.application.ApplicationManager

/*
 * Layout editor-specific helper methods and data for NlComponent
 */

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

  // Handle <Space> in the compatibility library package
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
 * Returns the ID, but also assigns a default id if the component does not already have an id (even if the component does
 * not need one according to [.needsDefaultId]
 */
fun NlComponent.ensureId(): String {
  return id ?: assignId()
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

          if (left == 0 && top == 0 && right == 0 && bottom == 0) {
            result = Insets.NONE
          }
          else {
            result = Insets(left, top, right, bottom)
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
      if (left == 0 && top == 0 && right == 0 && bottom == 0) {
        result = Insets.NONE
      }
      else {
        result = Insets(left, top, right, bottom)
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

  when (tagName) {
    PreferenceTags.PREFERENCE_CATEGORY,
    PreferenceTags.PREFERENCE_SCREEN,
    TAG_GROUP,
    TAG_MENU,
    TAG_SELECTOR -> return true
    else -> return false
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
    if (!tag.isValid) {
      return null
    }
    return ViewHandlerManager.get(tag.project).getHandler(this)
  }

val NlComponent.viewGroupHandler: ViewGroupHandler?
  get() {
    if (!tag.isValid) {
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
                                                                insertType: InsertType): NlComponent? {
  val tagName = NlComponentHelper.viewClassToTag(fqcn)
  val tag = tag.createChildTag(tagName, null, null, false)

  return model.createComponent((editor as ViewEditorImpl).sceneView, tag, this, before, insertType)
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

fun NlComponent.getDependencies(artifacts: MutableSet<String>) {
  if (!hasNlComponentInfo) {
    return
  }
  val handler = ViewHandlerManager.get(model.project).getHandler(this)
  if (handler != null) {
    val artifactId = handler.getGradleCoordinateId(this)

    if (artifactId != PaletteComponentHandler.IN_PLATFORM) {
      artifacts.add(artifactId)
    }
  }
  children?.forEach { it.getDependencies(artifacts) }
}

private val NlComponent.nlComponentData: NlComponentData
  get() {
    val mixin = this.mixin
    when (mixin) {
      is NlComponentMixin -> return mixin.data
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
