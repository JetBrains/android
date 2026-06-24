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
import com.android.tools.idea.avd.EnvironmentImage
import com.android.tools.idea.avd.EnvironmentsUpdater
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
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
    val list =
      listOf(
        EnvironmentImage(Path.of("/Sdk/environments/outdoor-nature-bright.jpg"), "Outdoor Nature Bright", false),
        EnvironmentImage(Path.of("/Sdk/environments/indoor-study-dark.jpg"), "Indoor Study Dark", true),
        EnvironmentImage(Path.of("/Sdk/environments/outdoor-city-bright.jpg"), "Outdoor City Bright", false),
      )
    runBlocking { whenever(environmentsUpdater.getEnvironments()).thenReturn(list) }
    ApplicationManager.getApplication().replaceService(EnvironmentsUpdater::class.java, environmentsUpdater, testRootDisposable)

    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val event = createTestEvent(project = projectRule.project, extra = dataSnapshotProvider)
    group.update(event)
    val children = group.getChildren(event)

    val environments = listOf("indoor-study-dark", "outdoor-city-bright", "outdoor-nature-bright")
    for (i in environments.indices) {
      val action = children[i]
      executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

      val call = emulator.getNextGrpcCall(2.seconds)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
      assertThat(shortDebugString(call.request))
        .isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:/Sdk/environments/${environments[i]}.jpg\" }")
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

  @Test
  fun testBuiltInImageActionPresentationText() {
    val group = ActionManager.getInstance().getAction("android.emulator.environments") as EmulatorEnvironmentActionGroup
    val event = createTestEvent(project = projectRule.project, extra = dataSnapshotProvider)
    group.update(event)
    val children = group.getChildren(event)

    val indoorAction = children.firstOrNull() as? EmulatorEnvironmentAction.BuiltInImage
    assertThat(indoorAction).isNotNull()
    assertThat(indoorAction!!.environmentPath.fileName.toString()).isEqualTo("indoor-study-dark.jpg")
    assertThat(indoorAction.templatePresentation.text).isEqualTo("Indoor Study Dark")
    assertThat(indoorAction.templatePresentation.description).isEqualTo("Select Indoor Study Dark environment")
  }

  @Test
  fun testCustom360Environment_featureFlagOn() {
    StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.overrideForTest(true, testRootDisposable)
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpeg = createMockJpegWithXml(xml)

    val imageFile = mock<VirtualFile>()
    whenever(imageFile.path).thenReturn(jpeg.toString())
    testRootDisposable.registerFakeFileChooserFactory(imageFile)

    val action = ActionManager.getInstance().getAction("android.emulator.environment.custom")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request))
      .isEqualTo("environment { key: \"scene.mode\" value: \"image360:${jpeg.systemIndependentString}\" }")
  }

  @Test
  fun testCustom360Environment_featureFlagOff() {
    StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.overrideForTest(false, testRootDisposable)
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpeg = createMockJpegWithXml(xml)

    val imageFile = mock<VirtualFile>()
    whenever(imageFile.path).thenReturn(jpeg.toString())
    testRootDisposable.registerFakeFileChooserFactory(imageFile)

    val action = ActionManager.getInstance().getAction("android.emulator.environment.custom")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request))
      .isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:${jpeg.systemIndependentString}\" }")
  }

  @Test
  fun testRecentCustom360Environment_featureFlagOn() {
    StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.overrideForTest(true, testRootDisposable)
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpeg = createMockJpegWithXml(xml)

    val action = EmulatorEnvironmentAction.RecentCustom(jpeg)
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request))
      .isEqualTo("environment { key: \"scene.mode\" value: \"image360:${jpeg.systemIndependentString}\" }")
  }

  @Test
  fun testRecentCustom360Environment_featureFlagOff() {
    StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.overrideForTest(false, testRootDisposable)
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpeg = createMockJpegWithXml(xml)

    val action = EmulatorEnvironmentAction.RecentCustom(jpeg)
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request))
      .isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:${jpeg.systemIndependentString}\" }")
  }

  private fun createMockJpegWithXml(xml: String): Path {
    val xmlBytes = xml.toByteArray()
    val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
    val payloadLength = xmpHeader.size + xmlBytes.size
    val totalLength = payloadLength + 2

    val jpeg = ByteArray(2 + 4 + payloadLength + 2)
    jpeg[0] = 0xFF.toByte()
    jpeg[1] = 0xD8.toByte() // SOI

    jpeg[2] = 0xFF.toByte()
    jpeg[3] = 0xE1.toByte() // APP1
    jpeg[4] = ((totalLength shr 8) and 0xFF).toByte()
    jpeg[5] = (totalLength and 0xFF).toByte()

    System.arraycopy(xmpHeader, 0, jpeg, 6, xmpHeader.size)
    System.arraycopy(xmlBytes, 0, jpeg, 6 + xmpHeader.size, xmlBytes.size)

    val eoiIdx = 6 + payloadLength
    jpeg[eoiIdx] = 0xFF.toByte()
    jpeg[eoiIdx + 1] = 0xD9.toByte() // EOI

    val file = tempDirRule.newPath("test_image.jpg")
    Files.write(file, jpeg)
    return file
  }

  @Test
  fun testCustomPng360Environment_featureFlagOn() {
    StudioFlags.EMBEDDED_EMULATOR_360_IMAGE_ENVIRONMENT.overrideForTest(true, testRootDisposable)
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val pngPath = createMockPngWithXml(xml)

    val imageFile = mock<VirtualFile>()
    whenever(imageFile.path).thenReturn(pngPath.toString())
    testRootDisposable.registerFakeFileChooserFactory(imageFile)

    val action = ActionManager.getInstance().getAction("android.emulator.environment.custom")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request))
      .isEqualTo("environment { key: \"scene.mode\" value: \"image360:${pngPath.systemIndependentString}\" }")
  }

  private fun createMockPngWithXml(xml: String, compressed: Boolean = false): Path {
    val bos = ByteArrayOutputStream()
    // Signature
    bos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

    // IHDR
    writePngChunk(bos, "IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))

    // iTXt
    val chunkBos = ByteArrayOutputStream()
    chunkBos.write("XML:com.adobe.xmp\u0000".toByteArray())
    if (compressed) {
      chunkBos.write(1) // compression flag
      chunkBos.write(0) // compression method
      chunkBos.write(0) // language tag null
      chunkBos.write(0) // translated keyword null
      val deflater = Deflater()
      deflater.setInput(xml.toByteArray())
      deflater.finish()
      val deflatedBytes = ByteArray(1024)
      val compressedBos = ByteArrayOutputStream()
      while (!deflater.finished()) {
        val count = deflater.deflate(deflatedBytes)
        compressedBos.write(deflatedBytes, 0, count)
      }
      deflater.end()
      chunkBos.write(compressedBos.toByteArray())
    } else {
      chunkBos.write(0) // compression flag
      chunkBos.write(0) // compression method
      chunkBos.write(0) // language tag null
      chunkBos.write(0) // translated keyword null
      chunkBos.write(xml.toByteArray())
    }
    writePngChunk(bos, "iTXt", chunkBos.toByteArray())

    // IEND
    writePngChunk(bos, "IEND", ByteArray(0))

    val file = tempDirRule.newPath("test_image.png")
    Files.write(file, bos.toByteArray())
    return file
  }

  private fun writePngChunk(bos: ByteArrayOutputStream, type: String, data: ByteArray) {
    val length = data.size
    bos.write((length shr 24) and 0xFF)
    bos.write((length shr 16) and 0xFF)
    bos.write((length shr 8) and 0xFF)
    bos.write(length and 0xFF)
    bos.write(type.toByteArray(Charsets.US_ASCII))
    bos.write(data)
    // CRC (dummy)
    bos.write(byteArrayOf(0, 0, 0, 0))
  }
}

private val Path.systemIndependentString: String
  get() = toSystemIndependentName(toString())
