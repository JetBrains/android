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
import com.android.tools.idea.configurations.getDeviceDefaultTheme
import com.android.tools.idea.run.activity.ActivityLocatorUtils
import com.android.xml.AndroidManifest
import com.google.common.collect.ImmutableList
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
  val activityAttributesMap: Map<String, ActivityAttributesSnapshot>,
  internal val mergedManifestInfo: MergedManifestInfo?,
  val minSdkVersion: AndroidVersion,
  val targetSdkVersion: AndroidVersion,
  val applicationIcon: ResourceValue?,
  val applicationLabel: ResourceValue?,
  val isRtlSupported: Boolean,
  val applicationDebuggable: Boolean?,
  val document: Document?,
  val manifestFiles: List<VirtualFile>,
  val permissionHolder: ImmutablePermissionHolder,
  val activities: List<Element>,
  val services: List<Element>,
  val actions: Actions?,
  /**
   * If the manifest merger did not encounter any errors when computing this snapshot.
   * `false` indicates that this snapshot contains dummy values that may not represent the merged
   * manifest accurately.
   */
  val isValid: Boolean,
  val exception: Exception?
) {

  private val nodeKeys: Map<String, NodeKey> = actions?.nodeKeys?.associateBy { it.toString() } ?: mapOf()

  fun getActivityAttributes(activity: String): ActivityAttributesSnapshot? {
    val index = activity.indexOf('.')
    return activityAttributesMap[
      if (index <= 0 && !`package`.isNullOrEmpty()) {
        "$`package`${if (index == -1) "." else ""}$activity"
      } else activity
    ]
  }

  fun getDefaultTheme(renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String =
    manifestTheme ?: module.getDeviceDefaultTheme(renderingTarget, screenSize, device)

  fun findUsedFeature(name: String): Element? =
    generateSequence(document?.documentElement?.firstChild, Node::getNextSibling)
      .filterIsInstance<Element>()
      .find {
        it.nodeType == Node.ELEMENT_NODE &&
        it.nodeName == AndroidManifest.NODE_USES_FEATURE &&
        it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == name
      }

  val loggingRecords: ImmutableList<MergingReport.Record>
    get() = mergedManifestInfo?.loggingRecords ?: ImmutableList.of()

  fun getNodeKey(name: String?): NodeKey? = nodeKeys[name]

  fun findActivity(qualifiedName: String): Element? = activities.findActivityByQualifiedName(qualifiedName)

  private fun List<Element>.findActivityByQualifiedName(name : String) : Element? =
    find { name == ActivityLocatorUtils.getQualifiedName(it) }
}
