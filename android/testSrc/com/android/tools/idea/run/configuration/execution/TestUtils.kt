/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.model.Apk
import com.android.tools.deployer.model.App
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.run.ApkInfo
import com.android.tools.manifest.parser.XmlNode
import com.android.tools.manifest.parser.components.ManifestActivityInfo
import com.android.tools.manifest.parser.components.ManifestServiceInfo
import com.android.utils.NullLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType


internal fun createApp(device: IDevice, appId: String, servicesName: List<String>, activitiesName: List<String>): App {
  val services = servicesName.map { createManifestServiceInfo(it, appId) }
  val activities = activitiesName.map { createManifestActivityInfo(it, appId) }
  val apk = Apk.Builder().setServices(services).setActivities(activities).build()
  return App(appId, listOf(apk), device, NullLogger())
}

private fun createManifestServiceInfo(serviceName: String,
                                      appId: String,
                                      attrs: Map<String, String> = emptyMap()): ManifestServiceInfo {
  val node = XmlNode()
  node.attributes()["name"] = serviceName
  for ((attr, value) in attrs) {
    node.attributes()[attr] = value
  }
  return ManifestServiceInfo(node, appId)
}

private fun createManifestActivityInfo(activityName: String,
                                       appId: String,
                                       attrs: Map<String, String> = emptyMap()): ManifestActivityInfo {
  val node = XmlNode()
  node.attributes()["name"] = activityName
  for ((attr, value) in attrs) {
    node.attributes()[attr] = value
  }
  return ManifestActivityInfo(node, appId)
}


internal class TestApplicationInstaller : ApplicationDeployer {

  private var appIdToApp: HashMap<String, App>

  constructor(appId: String, app: App) : this(hashMapOf<String, App>(Pair(appId, app)))

  constructor(appIdToApp: HashMap<String, App>) {
    this.appIdToApp = appIdToApp
  }

  override fun fullDeploy(device: IDevice, app: ApkInfo, deployOptions: DeployOptions, indicator: ProgressIndicator): Deployer.Result {
    val appId = app.applicationId
    return Deployer.Result(false, false, false, appIdToApp[appId]!!)
  }

  override fun applyChangesDeploy(device: IDevice,
                                  app: ApkInfo,
                                  deployOptions: DeployOptions,
                                  indicator: ProgressIndicator): Deployer.Result {
    TODO("Not yet implemented")
  }

  override fun applyCodeChangesDeploy(device: IDevice,
                                      app: ApkInfo,
                                      deployOptions: DeployOptions,
                                      indicator: ProgressIndicator): Deployer.Result {
    TODO("Not yet implemented")
  }
}

internal fun JavaCodeInsightTestFixture.addWearDependenciesToProject() {
  // Simulates that 'com.google.android.support:wearable:xxx' was added to `build.gradle`
  addFileToProject(
    "src/android/support/wearable/watchface/WatchFaceService.kt",
    """
      package android.support.wearable.watchface

      open class WatchFaceService
      """.trimIndent())

  addFileToProject(
    "src/androidx/wear/tiles/TileService.kt",
    """
      package androidx.wear.tiles

      open class TileService
      """.trimIndent())

  addFileToProject(
    "src/androidx/wear/watchface/complications/datasource/ComplicationDataSourceService.kt",
    """
      package androidx.wear.watchface.complications.datasource

      open class ComplicationDataSourceService
      """.trimIndent())
}

internal fun PsiFile.findElementByText(text: String): PsiElement = findDescendantOfType { it.node.text == text }!!