/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceValue
import com.android.manifmerger.Actions
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlNode.NodeKey
import com.android.resources.ScreenSize
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.dom.ActivityAttributesSnapshot
import com.android.tools.idea.configurations.getDefaultTheme
import com.android.tools.idea.run.activity.ActivityLocatorUtils
import com.android.xml.AndroidManifest
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * An immutable snapshot of the merged manifests at a point in time.
 */
class MergedManifestSnapshot internal constructor(
  val module: Module,
  val `package`: String?,
  val versionCode: Int?,
  val manifestTheme: String?,
  private val myAttributes: ImmutableMap<String, ActivityAttributesSnapshot>,
  val mergedManifestInfo: MergedManifestInfo?,
  val minSdkVersion: AndroidVersion,
  val targetSdkVersion: AndroidVersion,
  val applicationIcon: ResourceValue?,
  val applicationLabel: ResourceValue?,
  val isRtlSupported: Boolean,
  val applicationDebuggable: Boolean?,
  val document: Document?,
  manifestFiles: ImmutableList<VirtualFile>?,
  val permissionHolder: ImmutablePermissionHolder,
  val applicationHasCode: Boolean,
  private val myActivities: ImmutableList<Element>,
  private val myActivityAliases: ImmutableList<Element>,
  private val myServices: ImmutableList<Element>,
  val actions: Actions?,
  private val myLoggingRecords: ImmutableList<MergingReport.Record>,
  /**
   * Returns false if the manifest merger encountered any errors when computing this snapshot,
   * indicating that this snapshot contains dummy values that may not represent the merged
   * manifest accurately.
   */
  val isValid: Boolean
) {
  private val myNodeKeys: ImmutableMap<String, NodeKey>
  private val myFiles: ImmutableList<VirtualFile>

  init {
    myFiles = manifestFiles ?: ImmutableList.of()
    myNodeKeys = if (actions != null) {
      val nodeKeysBuilder = ImmutableMap.builder<String, NodeKey>()
      val keys = actions.nodeKeys
      for (key in keys) {
        nodeKeysBuilder.put(key.toString(), key)
      }
      nodeKeysBuilder.build()
    } else {
      ImmutableMap.of()
    }
  }

  val manifestFiles: List<VirtualFile>
    get() = myFiles
  val activityAttributesMap: Map<String, ActivityAttributesSnapshot>
    get() = myAttributes

  fun getActivityAttributes(activity: String): ActivityAttributesSnapshot? {
    var activity = activity
    val index = activity.indexOf('.')
    if (index <= 0 && `package` != null && !`package`.isEmpty()) {
      activity = `package` + (if (index == -1) "." else "") + activity
    }
    return activityAttributesMap[activity]
  }

  fun getDefaultTheme(renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String {
    return manifestTheme ?: module.getDefaultTheme(renderingTarget, screenSize, device)
  }

  val activities: List<Element>
    get() = myActivities
  val activityAliases: List<Element>
    get() = myActivityAliases
  val services: List<Element>
    get() = myServices

  fun findUsedFeature(name: String): Element? {
    if (document == null) {
      return null
    }
    var node = document.documentElement.firstChild
    while (node != null) {
      if (node.nodeType == Node.ELEMENT_NODE && AndroidManifest.NODE_USES_FEATURE == node.nodeName) {
        val element = node as Element
        if (name == element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)) {
          return element
        }
      }
      node = node.nextSibling
    }
    return null
  }

  val loggingRecords: ImmutableList<MergingReport.Record>
    get() = if (mergedManifestInfo != null) mergedManifestInfo.loggingRecords else ImmutableList.of()

  fun getNodeKey(name: String?): NodeKey? {
    return myNodeKeys[name]
  }

  fun findActivity(qualifiedName: String?): Element? {
    return if (qualifiedName == null || myActivities == null) {
      null
    } else getActivityOrAliasByName(qualifiedName, myActivities)
  }

  fun findActivityAlias(qualifiedName: String?): Element? {
    return if (qualifiedName == null || myActivityAliases == null) {
      null
    } else getActivityOrAliasByName(
      qualifiedName,
      myActivityAliases
    )
  }

  companion object {
    private fun getActivityOrAliasByName(qualifiedName: String, activityOrAliasElements: List<Element>): Element? {
      for (activity in activityOrAliasElements) {
        if (qualifiedName == ActivityLocatorUtils.getQualifiedName(activity)) {
          return activity
        }
      }
      return null
    }
  }
}
