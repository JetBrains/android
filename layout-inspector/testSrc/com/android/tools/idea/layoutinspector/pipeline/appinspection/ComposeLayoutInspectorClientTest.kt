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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo.Status
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.COMPOSE_INSPECTION_NOT_AVAILABLE_KEY
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.COMPOSE_JAR_FOUND_FOUND_KEY
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.COMPOSE_MAY_CAUSE_APP_CRASH_KEY
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient.Companion.resolveFolder
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INCOMPATIBLE_LIBRARY_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INSPECTOR_NOT_FOUND_USE_SNAPSHOT_KEY
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.MAVEN_DOWNLOAD_PROBLEM
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.MINIMUM_COMPOSE_COORDINATE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.PROGUARDED_LIBRARY_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.VERSION_MISSING_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportNonExistingFileException
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.UnknownCommandResponse
import org.jetbrains.kotlin.konan.file.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.net.UnknownHostException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumSet

class ComposeLayoutInspectorClientTest {
  private val processDescriptor = object : ProcessDescriptor {
    override val device = object : DeviceDescriptor {
      override val manufacturer = "mfg"
      override val model = "model"
      override val serial = "emulator-1234"
      override val isEmulator = true
      override val apiLevel = 30
      override val version = "10.0.0"
      override val codename: String? = null
    }
    override val abiCpuArch = "x86_64"
    override val name = "my name"
    override val packageName = "my package name"
    override val isRunning = true
    override val pid = 1234
    override val streamId = 4321L
  }

  private val projectRule = AndroidProjectRule.inMemory()
  private val adbRule = FakeAdbRule()
  private val devFlagRule = FlagRule(StudioFlags.APP_INSPECTION_USE_DEV_JAR)
  private val devFolderFlagRule = FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER)

  @get:Rule
  val rule = RuleChain.outerRule(projectRule).around(adbRule).around(devFlagRule).around(devFolderFlagRule)!!

  @Before
  fun before() {
    adbRule.attachDevice(processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
                         processDescriptor.device.version, processDescriptor.device.apiLevel.toString(),
                         "arm64-v8a", emptyMap(), DeviceState.HostConnectionType.LOCAL, "myAvd", "/android/avds/myAvd")
  }

  @Test
  fun testClientCreation() = runBlocking {
    val artifactService = mock<InspectorArtifactService>()
    val messenger = mock<AppInspectorMessenger>()
    whenever(artifactService.getOrResolveInspectorArtifact(any(), any())).thenReturn(Paths.get("/foo/bar"))
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.launchInspector(any())).thenReturn(messenger)
    val target = mock<AppInspectionTarget>()
    whenever(messenger.sendRawCommand(any())).thenReturn(UnknownCommandResponse.getDefaultInstance().toByteArray())
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.COMPATIBLE, "1.3.0", "")))
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, "", AttachErrorCode.UNKNOWN_ERROR_CODE, expectClient = true)
  }

  @Test
  fun inspectorArtifactNotFound_showUseSnapshotBanner() = runBlocking {
    val artifactService = object : InspectorArtifactService {
      override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
        throw AppInspectionArtifactNotFoundException("not found",
                                                     ArtifactCoordinate("group", "id", "1.3.0-SNAPSHOT", ArtifactCoordinate.Type.AAR))
      }
    }
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.COMPATIBLE, "1.3.0-SNAPSHOT", "")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, LayoutInspectorBundle.message(INSPECTOR_NOT_FOUND_USE_SNAPSHOT_KEY),
                AttachErrorCode.APP_INSPECTION_SNAPSHOT_NOT_SPECIFIED)
  }

  @Test
  fun inspectorArtifactNotFound_showComposeInspectionNotAvailableBanner() = runBlocking {
    val artifactService = object : InspectorArtifactService {
      override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
        throw AppInspectionArtifactNotFoundException("not found",
                                                     ArtifactCoordinate("androidx.compose.ui", "ui", "1.3.0", ArtifactCoordinate.Type.AAR))
      }
    }
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.COMPATIBLE, "1.3.0", "")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, LayoutInspectorBundle.message(COMPOSE_INSPECTION_NOT_AVAILABLE_KEY),
                AttachErrorCode.APP_INSPECTION_COMPOSE_INSPECTOR_NOT_FOUND)
  }

  @Test
  fun inspectorArtifactVersionMissing_showBanner() = runBlocking {
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.VERSION_MISSING, "", "error")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, LayoutInspectorBundle.message(VERSION_MISSING_MESSAGE_KEY),
                AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND)
  }

  @Test
  fun inspectorArtifactProguarded_showBanner() = runBlocking {
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.APP_PROGUARDED, "", "error")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, LayoutInspectorBundle.message(PROGUARDED_LIBRARY_MESSAGE_KEY),
                AttachErrorCode.APP_INSPECTION_PROGUARDED_APP)
  }

  @Test
  fun inspectorArtifactIncompatible_showBanner() = runBlocking {
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.INCOMPATIBLE, "garbage version", "error")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, LayoutInspectorBundle.message(INCOMPATIBLE_LIBRARY_MESSAGE_KEY),
                AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION)
  }

  @Test
  fun inspectorCouldNotDownloadArtifact_showBanner() = runBlocking {
    val artifact = ArtifactCoordinate("androidx.compose.ui", "ui", "1.3.0", ArtifactCoordinate.Type.AAR)
    val artifactService = object : InspectorArtifactService {
      override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
        throw AppInspectionArtifactNotFoundException("Artifact $artifactCoordinate could not be resolved on $GMAVEN_HOSTNAME.",
                                                     artifact, UnknownHostException(GMAVEN_HOSTNAME))
      }
    }
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.COMPATIBLE, "1.3.0", "")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, LayoutInspectorBundle.message(MAVEN_DOWNLOAD_PROBLEM, artifact.toString()),
                AttachErrorCode.APP_INSPECTION_FAILED_MAVEN_DOWNLOAD)
  }

  @Test
  fun inspectorCouldNotFindComposeInspectorJarWithDevFlag() = runBlocking {
    val folder = "/non-existing-folder/other/folder"
    val file = "$folder/compose-ui-inspection.jar"
    StudioFlags.APP_INSPECTION_USE_DEV_JAR.override(true)
    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER.override(folder)
    val artifactService = mock<InspectorArtifactService>()
    whenever(artifactService.getOrResolveInspectorArtifact(any(), any())).thenReturn(Paths.get("/foo/bar"))
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.launchInspector(any())).thenThrow(
      TransportNonExistingFileException("File $file could not be found for device emulator-123", file)
    )
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), mock(), "1.3.0", "")))
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices,
                LayoutInspectorBundle.message(COMPOSE_JAR_FOUND_FOUND_KEY, file,
                                              StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER.id),
                AttachErrorCode.TRANSPORT_PUSH_FAILED_FILE_NOT_FOUND)
  }

  @Test
  fun inspectorCouldNotFindComposeInspectorJarWithReleaseFlag() = runBlocking {
    val folder = "/non-existing-folder"
    val file = "$folder/compose-ui-inspection.jar"
    StudioFlags.APP_INSPECTION_USE_DEV_JAR.override(true)
    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER.override(folder)
    val artifactService = mock<InspectorArtifactService>()
    whenever(artifactService.getOrResolveInspectorArtifact(any(), any())).thenReturn(Paths.get("/foo/bar"))
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.launchInspector(any())).thenThrow(
      TransportNonExistingFileException("File $file could not be found for device emulator-123", file)
    )
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), mock(), "1.3.0", "")))
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices,
                LayoutInspectorBundle.message(COMPOSE_JAR_FOUND_FOUND_KEY, file,
                                              StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER.id),
                AttachErrorCode.TRANSPORT_PUSH_FAILED_FILE_NOT_FOUND,
                isRunningFromSources = false)
  }

  @Test
  fun inspectorArtifactLibraryMissing_showNoBanner() = runBlocking {
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(listOf(LibraryCompatbilityInfo(mock(), Status.LIBRARY_MISSING, "", "error")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    checkLaunch(apiServices, "", AttachErrorCode.UNKNOWN_ERROR_CODE)
  }

  @Test
  fun inspectorVersionWarning() = runBlocking {
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any()))
      .thenReturn(comp("1.1.0-beta05"))
      .thenReturn(comp("1.2.1"))
      .thenReturn(comp("1.2.0"))
      .thenReturn(comp("1.3.0"))
      .thenReturn(comp("1.3.0-alpha01"))
    val messenger = mock<AppInspectorMessenger>()
    whenever(messenger.sendRawCommand(any())).thenReturn(UnknownCommandResponse.getDefaultInstance().toByteArray())
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)
    whenever(apiServices.launchInspector(any())).thenReturn(messenger)
    val artifactService = mock<InspectorArtifactService>()
    whenever(artifactService.getOrResolveInspectorArtifact(any(), any())).thenReturn(Paths.get("/foo/bar"))
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    projectRule.mockProjectService(GradleProjectInfo::class.java)
    whenever(GradleProjectInfo.getInstance(projectRule.project).isBuildWithGradle).thenReturn(true)

    checkLaunch(apiServices, LayoutInspectorBundle.message(COMPOSE_MAY_CAUSE_APP_CRASH_KEY, "1.1.0-beta05", "1.2.1"), expectClient = true)
    checkLaunch(apiServices, "", expectClient = true)
    checkLaunch(apiServices, LayoutInspectorBundle.message(COMPOSE_MAY_CAUSE_APP_CRASH_KEY, "1.2.0", "1.2.1"), expectClient = true)
    checkLaunch(apiServices, "", expectClient = true)
    checkLaunch(apiServices, LayoutInspectorBundle.message(COMPOSE_MAY_CAUSE_APP_CRASH_KEY, "1.3.0-alpha01", "1.3.0"), expectClient = true)
  }

  private fun comp(version: String): List<LibraryCompatbilityInfo> =
    listOf(LibraryCompatbilityInfo(MINIMUM_COMPOSE_COORDINATE, Status.COMPATIBLE, version, ""))


  @Test
  fun testResolveFolder() {
    assertThat(resolveFolder("/Volumes/android/studio-main/tools/adt/idea", "#tools/../prebuilts/studio/sdk"))
      .isEqualTo("../../../prebuilts/studio/sdk".replace("/", File.separator))
    assertThat(resolveFolder("/Volumes/android/studio-main/tools/adt/idea", "#idea/../data"))
      .isEqualTo("../data".replace("/", File.separator))
    assertThat(resolveFolder("/Volumes/android/studio-main/tools/adt/idea", "#Volumes/data"))
      .isEqualTo("../../../../../data".replace("/", File.separator))
    assertThat(resolveFolder("/Volumes/android/studio-main/tools/adt/idea", "#not-here/data"))
      .isEqualTo("data")
    assertThat(resolveFolder("/Volumes/android", "../relative/extra"))
      .isEqualTo("../relative/extra")
    assertThat(resolveFolder(
      "/Volumes/android/androidx-main/frameworks/support/studio/android-studio-2022.2.1.5-mac/Android Studio Preview.app/Contents",
      "#studio/../../../out/some-folder")
    ).isEqualTo("../../../../../../out/some-folder".replace("/", File.separator))
    assertThat(resolveFolder(
      "/usr/local/google/home/jlauridsen/internal/androidx-main/frameworks/support/studio/android-studio-2022.2.1.5-linux/android-studio",
      "#studio/../../../out/some-folder")
    ).isEqualTo("../../../../../out/some-folder".replace("/", File.separator))
  }

  private suspend fun checkLaunch(
    apiServices: AppInspectionApiServices,
    expectedMessage: String,
    expectedError: AttachErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE,
    expectClient: Boolean = false,
    isRunningFromSources: Boolean = true
  ) {
    var errorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
    val capabilities = EnumSet.noneOf(InspectorClient.Capability::class.java)
    val client = ComposeLayoutInspectorClient.launch(apiServices, processDescriptor, model(projectRule.project) {}, mock(), capabilities,
                                                     mock(), { errorCode = it }, isRunningFromSources)
    if (expectClient) {
      assertThat(client).isNotNull()
    } else {
      assertThat(client).isNull()
    }

    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("No banner")
    if (expectedMessage.isEmpty()) {
      assertThat(bannerService.notifications)
        .named("expected to be empty but has: ${bannerService.notifications.firstOrNull()?.message}")
        .isEmpty()
    } else {
      val notification1 = bannerService.notifications.single()
      assertThat(notification1.message).isEqualTo(expectedMessage)
      assertThat(errorCode).isEqualTo(expectedError)

      // Clear the banner for the next invocation:
      bannerService.clear()
    }
  }
}