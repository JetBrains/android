/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SystemImageSupplier
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.recordExistingFile
import com.android.testutils.file.someRoot
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.sdk.DeviceManagers
import com.android.utils.CpuArchitecture
import com.android.utils.StdLogger
import com.android.utils.osArchitecture
import com.intellij.util.io.createDirectories
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.bridge.LocalComponent

class SdkFixture {
  val fileSystem = createInMemoryFileSystem()
  val sdkRoot: Path = Files.createDirectories(fileSystem.someRoot.resolve("sdk"))
  val avdRoot: Path = sdkRoot.root.resolve("avd")
  val repoPackages = RepositoryPackages()
  val repoManager = FakeRepoManager(sdkRoot, repoPackages)
  val sdkHandler = AndroidSdkHandler(sdkRoot, avdRoot, repoManager)
  private val logger = StdLogger(StdLogger.Level.INFO)
  val deviceManager = DeviceManagers.getDeviceManager(sdkHandler)
  val avdManager = AvdManager.createInstance(sdkHandler, avdRoot, deviceManager, logger)

  internal fun systemImageState(
    hasLocal: Boolean = true,
    hasRemote: Boolean = true,
    error: String? = null,
  ) =
    SystemImageState(
      hasLocal = hasLocal,
      hasRemote = hasRemote,
      error = error,
      images =
        SystemImageSupplier(
            repoManager,
            sdkHandler.getSystemImageManager(FakeProgressIndicator()),
            logger,
          )
          .get()
          .toImmutableList(),
    )

  fun createLocalSystemImage(
    path: String,
    tags: List<IdDisplay>,
    androidVersion: AndroidVersion,
    abi: String = recommendedAbiForHost(),
    displayName: String = (tags.map { it.display } + abi + "System Image").joinToString(" "),
    vendor: IdDisplay = IdDisplay.create("google", "Google"),
  ): FakeLocalPackage =
    createSystemImage(false, path, tags, androidVersion, displayName, listOf(abi), vendor = vendor)
      as FakeLocalPackage

  fun createRemoteSystemImage(
    path: String,
    tags: List<IdDisplay>,
    androidVersion: AndroidVersion,
    abi: String = recommendedAbiForHost(),
    displayName: String = (tags.map { it.display } + abi + "System Image").joinToString(" "),
    vendor: IdDisplay = IdDisplay.create("google", "Google"),
  ): FakeRemotePackage =
    createSystemImage(true, path, tags, androidVersion, displayName, listOf(abi), vendor = vendor)
      as FakeRemotePackage

  fun createSystemImage(
    isRemote: Boolean,
    path: String,
    tags: List<IdDisplay>,
    androidVersion: AndroidVersion,
    displayName: String,
    abis: List<String>,
    translatedAbis: List<String> = emptyList(),
    vendor: IdDisplay = IdDisplay.create("google", "Google"),
  ): FakePackage {
    val fullPath = "system-images;android-${androidVersion.apiStringWithExtension};$path;${abis[0]}"
    val pkg =
      if (isRemote) {
        FakeRemotePackage(fullPath)
      } else {
        val location = sdkRoot.resolve(fullPath.replace(";", "/"))
        location.resolve(SystemImageManager.SYS_IMG_NAME).recordExistingFile()
        location.resolve("data").createDirectories()

        FakeLocalPackage(fullPath, location)
      }
    pkg.displayName = displayName
    pkg.typeDetails =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType().apply {
        this.tags.addAll(tags)
        this.abis.addAll(abis)
        this.translatedAbis.addAll(translatedAbis)
        this.vendor = vendor
        this.apiLevelString = androidVersion.apiStringWithoutExtension
        this.codename = androidVersion.codename
        this.isBaseExtension = androidVersion.isBaseExtension
        this.extensionLevel = androidVersion.extensionLevel
      } as TypeDetails
    return pkg
  }

  fun ComposeContentTestRule.setContentWithSdkLocals(content: @Composable () -> Unit) {
    val swingPanel = JPanel()
    setContent {
      CompositionLocalProvider(
        LocalComponent provides swingPanel,
        LocalFileSystem provides fileSystem,
        LocalProject provides null,
      ) {
        content()
      }
    }
  }
}

fun recommendedAbiForHost() =
  when (osArchitecture) {
    CpuArchitecture.X86_64 -> Abi.X86_64.toString()
    CpuArchitecture.ARM -> Abi.ARM64_V8A.toString()
    else -> throw IllegalStateException("Unexpected host architecture: $osArchitecture")
  }
