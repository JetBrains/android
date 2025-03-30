/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common.deploy

import com.android.tools.deployer.model.Apk
import com.android.tools.deployer.model.App
import com.android.tools.manifest.parser.XmlNode
import com.android.tools.manifest.parser.components.ManifestActivityInfo
import com.android.tools.manifest.parser.components.ManifestServiceInfo

fun createApp(appId: String, servicesName: List<String> = emptyList(), activitiesName: List<String> = emptyList()
): App {
  val services = servicesName.map { createManifestServiceInfo(it, appId) }
  val activities = activitiesName.map { createManifestActivityInfo(it, appId) }
  val apk = Apk.Builder().setServices(services).setActivities(activities).build()
  return App.fromApk(appId, apk)
}

private fun createManifestServiceInfo(
  serviceName: String, appId: String, attrs: Map<String, String> = emptyMap()
): ManifestServiceInfo {
  val node = XmlNode()
  node.attributes()["name"] = serviceName
  for ((attr, value) in attrs) {
    node.attributes()[attr] = value
  }
  return ManifestServiceInfo(node, appId)
}

private fun createManifestActivityInfo(
  activityName: String, appId: String, attrs: Map<String, String> = emptyMap()
): ManifestActivityInfo {
  val node = XmlNode()
  node.attributes()["name"] = activityName
  for ((attr, value) in attrs) {
    node.attributes()[attr] = value
  }
  return ManifestActivityInfo(node, appId)
}