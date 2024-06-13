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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.FakeInspectorState
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Root
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewResource
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.GRAMMATICAL_GENDER_FEMININE
import com.android.tools.idea.layoutinspector.properties.PropertyType.INT32
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_HDR_YES
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_WIDE_COLOR_GAMUT_YES
import com.android.tools.idea.layoutinspector.resource.KEYBOARDHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.KEYBOARD_QWERTY
import com.android.tools.idea.layoutinspector.resource.NAVIGATIONHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_WHEEL
import com.android.tools.idea.layoutinspector.resource.ORIENTATION_PORTRAIT
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LAYOUTDIR_RTL
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LONG_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_ROUND_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_SMALL
import com.android.tools.idea.layoutinspector.resource.TOUCHSCREEN_STYLUS
import com.android.tools.idea.layoutinspector.resource.UI_MODE_NIGHT_NO
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_NORMAL
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Dimension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class AppInspectionSnapshotSupportTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val appInspectorRule = AppInspectionInspectorRule(projectRule)
  private lateinit var inspectorClientSettings: InspectorClientSettings
  private val inspectorRule =
    LayoutInspectorRule(
      listOf(
        appInspectorRule.createInspectorClientProvider(
          getClientSettings = { inspectorClientSettings }
        )
      ),
      projectRule,
    ) {
      it.name == PROCESS.name
    }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(appInspectorRule).around(inspectorRule)!!

  @Before
  fun setUp() {
    inspectorClientSettings = InspectorClientSettings(projectRule.project)
    inspectorRule.attachDevice(MODERN_DEVICE)
  }

  private val savePath = createInMemoryFileSystemAndFolder("snapshot").resolve("snapshot.li")

  @Test
  fun saveAndLoadLiveSnapshot() {
    inspectorClientSettings.inLiveMode = false
    runBlocking { inspectorRule.inspectorClient.stopFetching() }
    appInspectorRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      appInspectorRule.viewInspector.connection.sendEvent { rootsEventBuilder.apply { addIds(1L) } }

      appInspectorRule.viewInspector.connection.sendEvent { createLayoutEvent(layoutEventBuilder) }
      appInspectorRule.viewInspector.connection.sendEvent {
        createPropertiesEvent(propertiesEventBuilder)
      }

      LayoutInspectorViewProtocol.Response.newBuilder()
        .setStartFetchResponse(LayoutInspectorViewProtocol.StartFetchResponse.getDefaultInstance())
        .build()
    }

    inspectorRule.processNotifier.fireConnected(PROCESS)
    inspectorRule.processes.selectedProcess = PROCESS
    waitForCondition(20, TimeUnit.SECONDS) { inspectorRule.inspectorModel.windows.isNotEmpty() }

    inspectorRule.inspectorClient.saveSnapshot(savePath)
    val snapshotLoader = SnapshotLoader.createSnapshotLoader(savePath)!!
    val newModel =
      InspectorModel(inspectorRule.project, AndroidCoroutineScope(inspectorRule.disposable))
    snapshotLoader.loadFile(
      savePath,
      newModel,
      inspectorRule.notificationModel,
      inspectorRule.inspectorClient.stats,
    )
    checkSnapshot(newModel, snapshotLoader)
    assertThat(newModel.resourceLookup.dpi).isEqualTo(640)
    assertThat(newModel.resourceLookup.fontScale).isEqualTo(1.2f)
    assertThat(newModel.resourceLookup.screenDimension).isEqualTo(Dimension(800, 1600))
    assertThat(newModel.resourceLookup.hasResolver).isTrue()
  }

  @Test
  fun saveAndLoadLiveSnapshotWithDeepComposeNesting() {
    inspectorClientSettings.inLiveMode = true
    val inspectorState =
      FakeInspectorState(appInspectorRule.viewInspector, appInspectorRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeViewTreeAsSnapshot()
    inspectorState.createFakeLargeComposeTree()
    inspectorRule.processNotifier.fireConnected(PROCESS)
    inspectorRule.processes.selectedProcess = PROCESS
    waitForCondition(20, TimeUnit.SECONDS) { inspectorRule.inspectorModel.windows.isNotEmpty() }

    inspectorRule.inspectorClient.saveSnapshot(savePath)
    inspectorRule.inspectorModel.resourceLookup.updateConfiguration(null, null, null)

    val snapshotLoader = SnapshotLoader.createSnapshotLoader(savePath)!!
    val newModel =
      InspectorModel(inspectorRule.project, AndroidCoroutineScope(inspectorRule.disposable))
    val newNotificationModel = NotificationModel(inspectorRule.project)
    snapshotLoader.loadFile(
      savePath,
      newModel,
      newNotificationModel,
      inspectorRule.inspectorClient.stats,
    )

    // Verify we have all 126 composables
    for (id in -300L downTo -425L) {
      assertThat(newModel[id]).isNotNull()
    }

    assertThat(newModel.resourceLookup.dpi).isEqualTo(240)
    assertThat(newModel.resourceLookup.fontScale).isEqualTo(1.5f)
    assertThat(newModel.resourceLookup.screenDimension).isEqualTo(Dimension(800, 1600))
  }

  @Test
  fun saveAndLoadNonLiveSnapshot() {
    inspectorClientSettings.inLiveMode = false
    runBlocking { inspectorRule.inspectorClient.stopFetching() }
    appInspectorRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      appInspectorRule.viewInspector.connection.sendEvent { rootsEventBuilder.apply { addIds(1L) } }

      appInspectorRule.viewInspector.connection.sendEvent { createLayoutEvent(layoutEventBuilder) }
      appInspectorRule.viewInspector.connection.sendEvent {
        createPropertiesEvent(propertiesEventBuilder)
      }

      LayoutInspectorViewProtocol.Response.newBuilder()
        .setStartFetchResponse(LayoutInspectorViewProtocol.StartFetchResponse.getDefaultInstance())
        .build()
    }

    inspectorRule.processNotifier.fireConnected(PROCESS)
    inspectorRule.processes.selectedProcess = PROCESS
    waitForCondition(20, TimeUnit.SECONDS) { inspectorRule.inspectorModel.windows.isNotEmpty() }

    inspectorRule.inspectorClient.saveSnapshot(savePath)
    val snapshotLoader = SnapshotLoader.createSnapshotLoader(savePath)!!
    val newModel =
      InspectorModel(inspectorRule.project, AndroidCoroutineScope(inspectorRule.disposable))
    val newNotificationModel = NotificationModel(inspectorRule.project)
    snapshotLoader.loadFile(
      savePath,
      newModel,
      newNotificationModel,
      inspectorRule.inspectorClient.stats,
    )
    checkSnapshot(newModel, snapshotLoader)
    assertThat(newModel.resourceLookup.dpi).isEqualTo(640)
    assertThat(newModel.resourceLookup.fontScale).isEqualTo(1.2f)
    assertThat(newModel.resourceLookup.screenDimension).isEqualTo(Dimension(800, 1600))
    assertThat(newModel.resourceLookup.hasResolver).isTrue()
  }

  @Test
  fun saveNonLiveSnapshotImmediately() {
    // Connect initially in live mode
    inspectorClientSettings.inLiveMode = true
    appInspectorRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      appInspectorRule.viewInspector.connection.sendEvent { rootsEventBuilder.apply { addIds(2L) } }

      appInspectorRule.viewInspector.connection.sendEvent {
        layoutEventBuilder.apply {
          ViewString(1, "com.android.internal.policy")
          ViewString(2, "DecorView")
          ViewString(3, "demo")
          ViewString(4, "layout")
          ViewString(5, "android.widget")
          ViewString(6, "RelativeLayout")
          ViewString(12, "http://schemas.android.com/apk/res/myapp")

          Root {
            id = 2
            packageName = 1
            className = 2
            ViewNode {
              id = VIEW1
              packageName = 5
              className = 6
              layoutResource = ViewResource(4, 12, 3)
            }
          }
        }
      }
      LayoutInspectorViewProtocol.Response.newBuilder()
        .setStartFetchResponse(LayoutInspectorViewProtocol.StartFetchResponse.getDefaultInstance())
        .build()
    }

    appInspectorRule.viewInspector.interceptWhen({ it.hasStopFetchCommand() }) {
      LayoutInspectorViewProtocol.Response.newBuilder()
        .setStopFetchResponse(LayoutInspectorViewProtocol.StopFetchResponse.getDefaultInstance())
        .build()
    }

    inspectorRule.processNotifier.fireConnected(PROCESS)
    inspectorRule.processes.selectedProcess = PROCESS

    // Now switch to non-live
    inspectorClientSettings.inLiveMode = false
    runBlocking { inspectorRule.inspectorClient.stopFetching() }

    val startedLatch = CountDownLatch(1)
    // Try to save the snapshot right away, before we've gotten any events
    val snapshotThread = thread {
      startedLatch.countDown()
      inspectorRule.inspectorClient.saveSnapshot(savePath)
    }

    // Now send the events
    startedLatch.await()
    appInspectorRule.viewInspector.connection.sendEvent { rootsEventBuilder.apply { addIds(1L) } }

    appInspectorRule.viewInspector.connection.sendEvent { createLayoutEvent(layoutEventBuilder) }
    appInspectorRule.viewInspector.connection.sendEvent {
      createPropertiesEvent(propertiesEventBuilder)
    }

    // Wait for saving to complete
    snapshotThread.join()

    // Ensure the snapshot was saved correctly
    val snapshotLoader = SnapshotLoader.createSnapshotLoader(savePath)!!
    val newModel =
      InspectorModel(inspectorRule.project, AndroidCoroutineScope(inspectorRule.disposable))
    val newNotificationModel = NotificationModel(inspectorRule.project)
    snapshotLoader.loadFile(
      savePath,
      newModel,
      newNotificationModel,
      inspectorRule.inspectorClient.stats,
    )
    checkSnapshot(newModel, snapshotLoader)
  }

  private fun checkSnapshot(newModel: InspectorModel, snapshotLoader: SnapshotLoader) {
    assertTreeStructure(
      newModel.windows[ROOT]!!.root,
      view(ROOT, qualifiedName = "com.android.internal.policy.DecorView") {
        view(VIEW1, qualifiedName = "android.widget.RelativeLayout") {
          view(VIEW2, qualifiedName = "android.widget.TextView")
          view(VIEW3, qualifiedName = "android.widget.RelativeLayout") {
            view(VIEW4, qualifiedName = "android.widget.TextView")
          }
        }
      },
    )
    var checkedProperties = false
    snapshotLoader.propertiesProvider.addResultListener { _, node, table ->
      assertThat(node.drawId).isEqualTo(VIEW2)
      val item = table["http://schemas.android.com/apk/res/myapp", "myInt"]
      assertThat(item.value).isEqualTo("12345")
      assertThat(item.type).isEqualTo(INT32)
      checkedProperties = true
    }
    snapshotLoader.propertiesProvider.requestProperties(newModel[VIEW2]!!).get()
    assertThat(checkedProperties).isTrue()
  }

  private fun createPropertiesEvent(builder: LayoutInspectorViewProtocol.PropertiesEvent.Builder) {
    builder.apply {
      rootId = ROOT
      addAllStrings(
        listOf(
          ViewString(1, "myInt"),
          ViewString(3, "demo"),
          ViewString(4, "layout"),
          ViewString(12, "myapp"),
        )
      )
      addAllPropertyGroups(
        listOf(
          LayoutInspectorViewProtocol.PropertyGroup.newBuilder()
            .apply {
              viewId = VIEW2
              layout = ViewResource(4, 12, 3)
              addAllProperty(
                listOf(
                  LayoutInspectorViewProtocol.Property.newBuilder()
                    .apply {
                      type = LayoutInspectorViewProtocol.Property.Type.INT32
                      name = 1
                      namespace = 12
                      int32Value = 12345
                    }
                    .build()
                )
              )
            }
            .build(),
          LayoutInspectorViewProtocol.PropertyGroup.newBuilder().apply { viewId = VIEW1 }.build(),
          LayoutInspectorViewProtocol.PropertyGroup.newBuilder().apply { viewId = VIEW3 }.build(),
          LayoutInspectorViewProtocol.PropertyGroup.newBuilder().apply { viewId = VIEW4 }.build(),
          LayoutInspectorViewProtocol.PropertyGroup.newBuilder().apply { viewId = ROOT }.build(),
        )
      )
    }
  }

  private fun createLayoutEvent(builder: LayoutInspectorViewProtocol.LayoutEvent.Builder) {
    builder.apply {
      ViewString(1, "com.android.internal.policy")
      ViewString(2, "DecorView")
      ViewString(3, "demo")
      ViewString(4, "layout")
      ViewString(5, "android.widget")
      ViewString(6, "RelativeLayout")
      ViewString(7, "TextView")
      ViewString(8, "id")
      ViewString(9, "title")
      ViewString(10, "android")
      ViewString(11, "AppTheme")
      ViewString(12, "com.example")
      ViewString(13, "style")

      Root {
        id = ROOT
        packageName = 1
        className = 2
        ViewNode {
          id = VIEW1
          packageName = 5
          className = 6
          layoutResource = ViewResource(4, 12, 3)
          ViewNode {
            id = VIEW2
            packageName = 5
            className = 7
            resource = ViewResource(8, 10, 9)
            layoutResource = ViewResource(4, 12, 3)
          }
          ViewNode {
            id = VIEW3
            packageName = 5
            className = 6
            layoutResource = ViewResource(4, 12, 3)
            ViewNode {
              id = VIEW4
              packageName = 5
              className = 7
              layoutResource = ViewResource(4, 12, 3)
            }
          }
        }
      }

      appContextBuilder.apply {
        configurationBuilder.apply {
          fontScale = 1.2f
          countryCode = 310
          networkCode = 410
          screenLayout =
            SCREENLAYOUT_SIZE_SMALL or
              SCREENLAYOUT_LONG_YES or
              SCREENLAYOUT_LAYOUTDIR_RTL or
              SCREENLAYOUT_ROUND_YES
          colorMode = COLOR_MODE_WIDE_COLOR_GAMUT_YES or COLOR_MODE_HDR_YES
          touchScreen = TOUCHSCREEN_STYLUS
          keyboard = KEYBOARD_QWERTY
          keyboardHidden = KEYBOARDHIDDEN_NO
          hardKeyboardHidden = KEYBOARDHIDDEN_NO
          navigation = NAVIGATION_WHEEL
          navigationHidden = NAVIGATIONHIDDEN_NO
          uiMode = UI_MODE_TYPE_NORMAL or UI_MODE_NIGHT_NO
          smallestScreenWidthDp = 200
          density = 640
          orientation = ORIENTATION_PORTRAIT
          screenWidthDp = 480
          screenHeightDp = 800
          grammaticalGender = GRAMMATICAL_GENDER_FEMININE
        }
        mainDisplayWidth = 800
        mainDisplayHeight = 1600
        theme = ViewResource(13, 12, 11)
      }
    }
  }

  private fun assertTreeStructure(node: ViewNode, expected: ViewNode) {
    assertThat(node.qualifiedName).isEqualTo(expected.qualifiedName)
    assertThat(node.drawId).isEqualTo(expected.drawId)
    ViewNode.readAccess {
      assertThat(node.children.size).isEqualTo(expected.children.size)
      for (index in node.children.indices) {
        assertTreeStructure(node.children[index], expected.children[index])
      }
    }
  }
}
