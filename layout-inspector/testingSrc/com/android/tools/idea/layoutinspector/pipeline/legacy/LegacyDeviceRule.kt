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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.AdbServiceRule
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.pipeline.adb.SimpleCommand
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Manifest sample xml file with 2 activities which have separate themes. */
@Language("XML")
private val manifestSample =
  """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
  <application
      android:theme="@style/App.Dark.Theme">
      <activity
          android:name=".MainActivity"
          android:exported="true"
          android:theme="@style/App.Dark.Theme" />
      <activity
          android:name=".LoginActivity"
          android:exported="true"
          android:theme="@style/Login.Dark.Theme" />
  </application>
</manifest>
"""
    .trimIndent()

/** Themes sample xml file which defines the themes referenced in the manifest. */
@Language("XML")
private val themesSample =
  """
<resources xmlns:android="http://schemas.android.com/apk/res/android">
    <style name="App.Dark.Theme" parent="android:Theme.Dark" />
    <style name="Login.Dark.Theme" parent="android:Base.Theme.DeviceDefault"/>
</resources>
"""
    .trimIndent()

/** Sample output from `adb shell am get-config` [Excerpt that gives a current configuration] */
private val configSample =
  """
config: mcc310-mnc260-en-rUS-ldltr-sw411dp-w411dp-h842dp-normal-long-notround-port-notnight-420dpi-finger-keysexposed-nokeys-navexposed-dpad-v23
abi: x86
"""
    .trim()

/** Sample output from `adb shell activity activities` [Excerpt that gives the current activity] */
private val activitiesSample =
  """
Display #0 (activities from top to bottom):
  Stack #11:
    Task id #14
    * TaskRecord{5f879ed #14 A=com.example U=0 sz=5}
      userId=0 effectiveUid=u0a61 mCallingUid=0 mCallingPackage=null
  mFocusedActivity: ActivityRecord{9432e85 u0 com.example/.LoginActivity t14}
  mFocusedStack=ActivityStack{218aab3 stackId=11, 1 tasks} mLastFocusedStack=ActivityStack{218aab3 stackId=11, 1 tasks}
  mSleepTimeout=false
  """
    .trimIndent()

/**
 * Rule for providing a legacy device setup which will allow LegacyTreeLoader to initialize a
 * configuration.
 */
class LegacyDeviceRule(
  private val packageName: String = "com.example",
  private val manifest: String = manifestSample,
  private val config: String = configSample,
  private val activities: String = activitiesSample,
  private val themes: String = themesSample,
) : ExternalResource() {
  private val projectRule = AndroidProjectRule.withSdk()
  private val commandHandler =
    FakeShellCommandHandler().apply {
      extraCommands.add(SimpleCommand("am get-config", config))
      extraCommands.add(SimpleCommand("dumpsys activity activities", activities))
    }
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)
  private val adbServiceRule = AdbServiceRule(projectRule::project, adbRule)
  private val disposableRule = DisposableRule()
  private var clientInstance: LegacyClient? = null

  val project: Project
    get() = projectRule.project

  val disposable: Disposable
    get() = disposableRule.disposable

  val client: LegacyClient
    get() = clientInstance!!

  val bridge: AndroidDebugBridge
    get() = adbRule.bridge

  override fun before() {
    val device = LEGACY_DEVICE
    adbRule.attachDevice(
      device.serial,
      device.manufacturer,
      device.model,
      device.version,
      device.apiLevel.toString(),
    )
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
    projectRule.fixture.addFileToProject("/AndroidManifest.xml", manifest)
    projectRule.fixture.addFileToProject("res/values/themes.xml", themes)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    AndroidModel.set(facet, TestAndroidModel(packageName))
    clientInstance = createSimpleLegacyClient()
  }

  /** Creates a real [LegacyClient] that's good enough for tests */
  private fun createSimpleLegacyClient(): LegacyClient {
    val model = model(disposable, project) {}
    val notificationModel = NotificationModel(project)
    val process = LEGACY_DEVICE.createProcess()
    val scope = AndroidCoroutineScope(disposable)
    val client =
      LegacyClient(
          process,
          model,
          notificationModel,
          LayoutInspectorSessionMetrics(model.project, process),
          scope,
          disposable,
        )
        .apply { launchMonitor = MockitoKt.mock() }
    // This causes the current client to register its listeners
    val treeSettings = FakeTreeSettings()
    LayoutInspector(
      scope,
      InspectorClientSettings(projectRule.project),
      client,
      model,
      notificationModel,
      treeSettings,
    )
    client.state = InspectorClient.State.CONNECTED
    return client
  }

  override fun apply(base: Statement, description: Description): Statement {
    val innerRules = listOf(disposableRule, adbServiceRule, adbRule, projectRule)
    return innerRules.fold(super.apply(base, description)) { stmt: Statement, rule: TestRule ->
      rule.apply(stmt, description)
    }
  }
}
