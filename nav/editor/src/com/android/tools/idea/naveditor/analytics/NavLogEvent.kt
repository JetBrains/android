/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.analytics

import com.android.SdkConstants.ATTR_ARG_TYPE
import com.android.SdkConstants.ATTR_AUTO_VERIFY
import com.android.SdkConstants.ATTR_DEEPLINK_MIMETYPE
import com.android.SdkConstants.ATTR_DEFAULT_NAV_HOST
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_NAV_GRAPH
import com.android.SdkConstants.ATTR_NULLABLE
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.ATTR_URI
import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.SdkConstants.TAG_INCLUDE
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.inclusive
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isActivity
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isFragment
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.layout
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.model.schema
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavDestinationInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavPropertyInfo
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ACTION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DATA
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DATA_PATTERN
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_EXIT_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_SINGLE_TOP
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ACTION
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import org.jetbrains.annotations.TestOnly
import java.util.LinkedList

/**
 * This class probably shouldn't be instantiated directly. Instead do
 * [NavUsageTracker.getInstance(surface).createEvent(type).withWhateverInfo(...).log()].
 */
class NavLogEvent(event: NavEditorEvent.NavEditorEventType, private val tracker: NavUsageTracker) {

  private val navEventBuilder: NavEditorEvent.Builder = NavEditorEvent.newBuilder()
  private val schema: NavigationSchema? = tracker.model?.schema

  init {
    navEventBuilder.type = event
  }

  fun log() {
    tracker.logEvent(navEventBuilder.build())
  }

  @TestOnly
  fun getProtoForTest(): NavEditorEvent {
    return navEventBuilder.build()
  }

  fun withAttributeInfo(attrName: String,
                        tagName: String?,
                        wasEmpty: Boolean): NavLogEvent {
    val builder = navEventBuilder.propertyInfoBuilder
    builder.property = when (attrName) {
      ATTR_ACTION -> NavPropertyInfo.Property.ACTION
      ATTR_ARG_TYPE -> NavPropertyInfo.Property.ARG_TYPE
      ATTR_AUTO_VERIFY -> NavPropertyInfo.Property.AUTO_VERIFY
      ATTR_DATA -> NavPropertyInfo.Property.DATA
      ATTR_DATA_PATTERN -> NavPropertyInfo.Property.DATA_PATTERN
      ATTR_DEFAULT_NAV_HOST -> NavPropertyInfo.Property.DEFAULT_NAV_HOST
      ATTR_DEFAULT_VALUE -> NavPropertyInfo.Property.DEFAULT_VALUE
      ATTR_DESTINATION -> NavPropertyInfo.Property.DESTINATION
      ATTR_ENTER_ANIM -> NavPropertyInfo.Property.ENTER_ANIM
      ATTR_EXIT_ANIM -> NavPropertyInfo.Property.EXIT_ANIM
      ATTR_GRAPH -> NavPropertyInfo.Property.GRAPH
      ATTR_ID -> NavPropertyInfo.Property.ID
      ATTR_LABEL -> NavPropertyInfo.Property.LABEL
      ATTR_SINGLE_TOP -> NavPropertyInfo.Property.LAUNCH_SINGLE_TOP
      ATTR_NAME -> NavPropertyInfo.Property.NAME
      ATTR_NAV_GRAPH -> NavPropertyInfo.Property.NAV_GRAPH
      ATTR_NULLABLE -> NavPropertyInfo.Property.NULLABLE
      ATTR_POP_ENTER_ANIM -> NavPropertyInfo.Property.POP_ENTER_ANIM
      ATTR_POP_EXIT_ANIM -> NavPropertyInfo.Property.POP_EXIT_ANIM
      ATTR_POP_UP_TO -> NavPropertyInfo.Property.POP_UP_TO
      ATTR_POP_UP_TO_INCLUSIVE -> NavPropertyInfo.Property.POP_UP_TO_INCLUSIVE
      ATTR_START_DESTINATION -> NavPropertyInfo.Property.START_DESTINATION
      ATTR_URI -> NavPropertyInfo.Property.URI
      ATTR_DEEPLINK_MIMETYPE -> NavPropertyInfo.Property.MIME_TYPE

      else -> NavPropertyInfo.Property.CUSTOM
    }
    tagName?.let { builder.setContainingTag(convertTag(it)) }
    builder.wasEmpty = wasEmpty
    navEventBuilder.setPropertyInfo(builder)
    return this
  }

  private fun convertTag(tagName: String): NavPropertyInfo.TagType {
    return when {
      schema?.isFragmentTag(tagName) ?: false -> NavPropertyInfo.TagType.FRAGMENT_TAG
      schema?.isActivityTag(tagName) ?: false -> NavPropertyInfo.TagType.ACTIVITY_TAG
      TAG_INCLUDE == tagName -> NavPropertyInfo.TagType.INCLUDE_TAG
      schema?.isNavigationTag(tagName) ?: false -> NavPropertyInfo.TagType.NAVIGATION_TAG
      TAG_ACTION == tagName -> NavPropertyInfo.TagType.ACTION_TAG
      TAG_ARGUMENT == tagName -> NavPropertyInfo.TagType.ARGUMENT_TAG
      TAG_DEEP_LINK == tagName -> NavPropertyInfo.TagType.DEEPLINK_TAG
      else -> NavPropertyInfo.TagType.CUSTOM_TAG
    }
  }

  fun withActionInfo(actionComponent: NlComponent): NavLogEvent {
    val builder = navEventBuilder.actionInfoBuilder
    val source = actionComponent.parent ?: return this // shouldn't happen
    val destination = actionComponent.actionDestination
    val root = if (actionComponent.parent?.isNavigation == true) actionComponent.parent else actionComponent.parent?.parent
    builder.type = when (actionComponent.getActionType(root)) {
      ActionType.GLOBAL -> NavActionInfo.ActionType.GLOBAL
      ActionType.EXIT -> NavActionInfo.ActionType.EXIT
      ActionType.REGULAR -> NavActionInfo.ActionType.REGULAR
      ActionType.SELF -> NavActionInfo.ActionType.SELF
      else -> NavActionInfo.ActionType.UNKNOWN
    }
    if (actionComponent.popUpTo != null) {
      builder.hasPop = true
    }
    if (actionComponent.inclusive == true) {
      builder.inclusive = true
    }
    builder.countFromSource = actionComponent.parent?.children?.count { it.isAction } ?: 0
    val destinationParent = destination?.parent ?: destination
    if (destinationParent != null) {
      var countToDestination = 0
      var countSame = 0
      val destinationQueue = LinkedList<NlComponent>()
      destinationQueue.add(destinationParent)
      while (destinationQueue.isNotEmpty()) {
        val d = destinationQueue.removeFirst()
        if (d != source && d.id == source.id) {
          continue
        }
        d.children.forEach { child ->
          if (child.isAction) {
            if (child.actionDestination == destination) {
              countToDestination++
              if (child.parent == source) {
                countSame++
              }
            }
          }
          if (child.isDestination) {
            destinationQueue.add(child)
          }
        }
      }
      builder.countToDestination = countToDestination
      builder.countSame = countSame
    }
    navEventBuilder.setActionInfo(builder)
    return this
  }

  fun withDestinationInfo(destination: NlComponent): NavLogEvent {
    val builder = navEventBuilder.destinationInfoBuilder
    builder.type = when {
      destination.isFragment -> NavDestinationInfo.DestinationType.FRAGMENT
      destination.isActivity -> NavDestinationInfo.DestinationType.ACTIVITY
      else -> NavDestinationInfo.DestinationType.OTHER
    }
    if (!destination.className.isNullOrBlank()) {
      builder.hasClass = true
    }
    if (!destination.layout.isNullOrBlank()) {
      builder.hasLayout = true
    }
    navEventBuilder.setDestinationInfo(builder)
    return this
  }

  fun withSchemaInfo(): NavLogEvent {
    val builder = navEventBuilder.schemaInfoBuilder
    builder.customNavigators = schema?.customNavigatorCount ?: 0
    builder.customTags = schema?.customTagCount ?: 0
    builder.customDestinations = schema?.customDestinationCount ?: 0
    navEventBuilder.setSchemaInfo(builder)
    return this
  }

  fun withNavigationContents(): NavLogEvent {
    val builder = navEventBuilder.contentsBuilder
    val model = tracker.model ?: return this
    var fragments = 0
    var activities = 0
    var customDestinations = 0
    var regularActions = 0
    var exitActions = 0
    var globalActions = 0
    var selfActions = 0
    var includes = 0
    var nestedGraphs = 0
    var placeholders = 0

    for (component in model.flattenComponents()) {
      if (component.isDestination) {
        if (component.isFragment) {
          fragments++
        }
        if (component.isActivity) {
          activities++
        }
        if (component.destinationType == NavigationSchema.DestinationType.OTHER) {
          customDestinations++
        }
        if (component.isInclude) {
          includes++
        }
        if (component.parent != null && component.isNavigation && !component.isInclude) {
          nestedGraphs++
        }
        if (component.destinationType != NavigationSchema.DestinationType.NAVIGATION && component.className == null) {
          placeholders++
        }
      }
      if (component.isAction) {
        val actionRoot = if (component.parent?.isNavigation == true) component.parent else component.parent?.parent
        when (component.getActionType(actionRoot)) {
          ActionType.GLOBAL -> globalActions++
          ActionType.SELF -> selfActions++
          ActionType.REGULAR -> regularActions++
          ActionType.EXIT -> exitActions++
          ActionType.NONE,
          ActionType.EXIT_DESTINATION -> {}
        }
      }
    }

    builder.fragments = fragments
    builder.activities = activities
    builder.customDestinations = customDestinations
    builder.regularActions = regularActions
    builder.exitActions = exitActions
    builder.globalActions = globalActions
    builder.selfActions = selfActions
    builder.includes = includes
    builder.nestedGraphs = nestedGraphs
    builder.placeholders = placeholders

    navEventBuilder.setContents(builder)
    return this
  }

  fun withSource(source: NavEditorEvent.Source?): NavLogEvent {
    source?.let { navEventBuilder.source = source }
    return this
  }
}
