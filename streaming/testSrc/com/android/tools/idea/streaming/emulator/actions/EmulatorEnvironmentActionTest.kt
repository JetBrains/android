/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.Camera
import com.android.emulator.control.CameraList
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.createTestEvent
import com.android.tools.adtui.actions.executeAction
import com.android.tools.idea.avdmanager.EnvironmentsUpdater
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.file.registerFakeFileChooserFactory
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunsInEdt
class EmulatorEnvironmentActionTest {

  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule val rule = RuleChain(projectRule, emulatorRule, EdtRule())
  @get:Rule val tempDirRule = TemporaryDirectoryRule()

  val testRootDisposable
    get() = projectRule.disposable

  private val emulator by lazy {
    val avdFolder = FakeEmulator.createDisplayGlassesAvd(emulatorRule.avdRoot)
    emulatorRule.newEmulator(avdFolder)
  }
  private val emulatorController by lazy {
    emulator.start()
    val catalog = RunningEmulatorCatalog.getInstance()
    val controller = runBlocking { catalog.updateNow().await() }.first()
    waitForCondition(5.seconds) { controller.connectionState == EmulatorController.ConnectionState.CONNECTED }
    controller
  }
  private val dataSnapshotProvider by lazy { DataSnapshotProvider { sink -> sink[EMULATOR_CONTROLLER_KEY] = emulatorController } }

  @Before
  fun setUp() {
    StudioFlags.EMBEDDED_EMULATOR_CAMERA_ENVIRONMENT.overrideForTest(true, testRootDisposable)
  }

  @Test
  fun testEmptyEnvironment() {
    val action = ActionManager.getInstance().getAction("android.emulator.environment.empty")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request)).isEqualTo("")
  }

  @Test
  fun testBuiltInEnvironments() {
    val environmentsUpdater = mock<EnvironmentsUpdater>()
    runBlocking {
      doAnswer { Path.of("/Sdk/environments/${it.getArgument<String>(0)}") }.whenever(environmentsUpdater).getUpdatedFile(any())
    }
    ApplicationManager.getApplication().replaceService(EnvironmentsUpdater::class.java, environmentsUpdater, testRootDisposable)
    val environments = listOf("indoor-study-dark", "outdoor-city-bright", "outdoor-nature-bright")
    for (environment in environments) {
      val actionId = "android.emulator.environment.${environment.replace('-', '.')}"
      val action = ActionManager.getInstance().getAction(actionId)
      executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

      val call = emulator.getNextGrpcCall(2.seconds)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
      assertThat(shortDebugString(call.request))
        .isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:/Sdk/environments/$environment.jpg\" }")
    }
  }

  @Test
  fun testCustomEnvironment() {
    val imageFile = mock<VirtualFile>()
    whenever(imageFile.path).thenReturn("/tmp/test_image.png")
    testRootDisposable.registerFakeFileChooserFactory(imageFile)

    val action = ActionManager.getInstance().getAction("android.emulator.environment.custom")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request)).isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:${imageFile.path}\" }")
  }

  @Test
  fun testRecentCustomEnvironment() {
    val filePath = "/tmp/recent_image.png"
    val action = EmulatorEnvironmentAction.RecentCustom(Path.of(filePath))
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request)).isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:$filePath\" }")
  }

  @Test
  fun testActionGroupIncludesRecentFiles() {
    val dir = Files.createDirectories(tempDirRule.newPath())
    val file1 = dir.resolve("file1.png")
    Files.createFile(file1)
    val file2 = dir.resolve("file2.png")
    Files.createFile(file2)
    // Setup recent files
    val properties = PropertiesComponent.getInstance()
    properties.setValue("EmulatorEnvironmentAction.recentFiles", "${file1.toAbsolutePath()}\n${file2.toAbsolutePath()}")

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val children = group.getChildren(null)

    // Expect original children + Separator + Recent Environments submenu
    assertThat(children.size).isEqualTo(9)
    val recentGroup = children[4] as DefaultActionGroup
    assertThat(children[5]).isInstanceOf(Separator::class.java)
    assertThat(recentGroup.templatePresentation.text).isEqualTo("Recent Custom Environments")
    val recentChildren = recentGroup.getChildren(null)
    assertThat(recentChildren.size).isEqualTo(2)
    assertThat((recentChildren[0] as EmulatorEnvironmentAction.RecentCustom).filePath).isEqualTo(file1.toAbsolutePath())
    assertThat((recentChildren[1] as EmulatorEnvironmentAction.RecentCustom).filePath).isEqualTo(file2.toAbsolutePath())
  }

  @Test
  fun testActionGroupDoesNotIncludeTitleWhenEmpty() {
    // Clear recent files
    val properties = PropertiesComponent.getInstance()
    properties.setValue("EmulatorEnvironmentAction.recentFiles", null)

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val children = group.getChildren(null)
    val recentGroup = children[4] as DefaultActionGroup

    val event = createTestEvent(project = projectRule.project, extra = dataSnapshotProvider)
    recentGroup.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActionGroupFiltersNonExistentFiles() {
    val dir = Files.createDirectories(tempDirRule.newPath())
    val file1 = dir.resolve("file1.png")
    Files.createFile(file1)
    val file2Path = "/tmp/non_existent_file.png"
    // Setup recent files
    val properties = PropertiesComponent.getInstance()
    properties.setValue("EmulatorEnvironmentAction.recentFiles", "${file1.toAbsolutePath()}\n$file2Path")

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val children = group.getChildren(null)

    // Expect original children + Separator + Recent Environments submenu containing 1 file (file1)
    assertThat(children.size).isEqualTo(9)
    val recentGroup = children[4] as DefaultActionGroup
    assertThat(children[5]).isInstanceOf(Separator::class.java)
    assertThat(recentGroup.templatePresentation.text).isEqualTo("Recent Custom Environments")
    val recentChildren = recentGroup.getChildren(null)
    assertThat(recentChildren.size).isEqualTo(1)
    assertThat((recentChildren[0] as EmulatorEnvironmentAction.RecentCustom).filePath).isEqualTo(file1.toAbsolutePath())
  }

  @Test
  fun testCameraEnvironment() {
    val action = EmulatorEnvironmentAction.Camera("FaceTime HD Camera", "camera_id_123")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request)).isEqualTo("environment { key: \"scene.mode\" value: \"webcam:camera_id_123\" }")
  }

  @Test
  fun testActionGroupIncludesCameras() {
    val camera1 = Camera.newBuilder().setDisplayName("Camera 1").setId("id1").build()
    val camera2 = Camera.newBuilder().setDisplayName("Camera 2").setId("id2").build()
    emulator.hostCameras = CameraList.newBuilder().addCameras(camera1).addCameras(camera2).build()

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val event = createTestEvent(project = projectRule.project, extra = dataSnapshotProvider)
    group.update(event)
    val children = group.getChildren(event)

    // Expect original children (5) + Separator + Cameras submenu
    assertThat(children.size).isEqualTo(9)
    assertThat(children[5]).isInstanceOf(Separator::class.java)
    val camerasGroup = children[6] as DefaultActionGroup
    assertThat(camerasGroup.templatePresentation.text).isEqualTo("Camera")
    val cameraChildren = camerasGroup.getChildren(event)
    assertThat(cameraChildren.size).isEqualTo(2)

    val cameraAction1 = cameraChildren[0] as EmulatorEnvironmentAction.Camera
    assertThat(cameraAction1.cameraName).isEqualTo("Camera 1")
    assertThat(cameraAction1.cameraId).isEqualTo("id1")

    val cameraAction2 = cameraChildren[1] as EmulatorEnvironmentAction.Camera
    assertThat(cameraAction2.cameraName).isEqualTo("Camera 2")
    assertThat(cameraAction2.cameraId).isEqualTo("id2")
  }

  @Test
  fun testActionGroupDoesNotIncludeCamerasWhenEmpty() {
    emulator.hostCameras = CameraList.getDefaultInstance()

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val event = createTestEvent(project = projectRule.project, extra = dataSnapshotProvider)
    group.update(event)
    val children = group.getChildren(event)
    val camerasGroup = children[6] as DefaultActionGroup

    camerasGroup.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActionGroupDoesNotIncludeCamerasWhenFlagDisabled() {
    StudioFlags.EMBEDDED_EMULATOR_CAMERA_ENVIRONMENT.overrideForTest(false, testRootDisposable)
    val camera1 = Camera.newBuilder().setDisplayName("Camera 1").setId("id1").build()
    emulator.hostCameras = CameraList.newBuilder().addCameras(camera1).build()

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val event = createTestEvent(project = projectRule.project, extra = dataSnapshotProvider)
    emulatorController
    group.update(event)
    val children = group.getChildren(event)
    val camerasGroup = children[6] as DefaultActionGroup

    camerasGroup.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }
}
