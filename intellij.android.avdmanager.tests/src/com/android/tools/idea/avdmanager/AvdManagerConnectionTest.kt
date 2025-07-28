/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.targets.SystemImage
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.android.testutils.truth.PathSubject
import com.android.tools.idea.avdmanager.AvdManagerConnection.Companion.resetConnectionFactory
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import com.intellij.credentialStore.Credentials
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import com.intellij.util.net.ProxyCredentialStore
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.AndroidTestCase

/** Tests for [AvdManagerConnection]. */
class AvdManagerConnectionTest : AndroidTestCase() {

  private val sdkRoot = createInMemoryFileSystemAndFolder("sdk")
  private val prefsRoot: Path = sdkRoot.root.resolve("android-home")

  private lateinit var avdManager: AvdManager
  private lateinit var avdManagerConnection: AvdManagerConnection
  private lateinit var avdFolder: Path
  private lateinit var systemImage: SystemImage

  override fun setUp() {
    super.setUp()

    sdkRoot.resolve("tools/lib/emulator/snapshots.img").recordExistingFile()
    recordGoogleApisSysImg23(sdkRoot)
    recordEmulatorVersion_23_4_5(sdkRoot)

    val androidSdkHandler = AndroidSdkHandler(sdkRoot, prefsRoot)

    avdManager =
      AvdManager.createInstance(
        androidSdkHandler,
        prefsRoot.resolve("avd"),
        DeviceManager.createInstance(androidSdkHandler, NullLogger.getLogger()),
        NullLogger.getLogger(),
      )

    avdFolder = AvdInfo.getDefaultAvdFolder(avdManager, name, false)
    systemImage =
      androidSdkHandler.getSystemImageManager(FakeProgressIndicator()).getImages().iterator().next()

    // We use Dispatchers.Unconfined to show dialogs: this causes MessageDialog to be invoked on the
    // calling thread. We don't simulate
    // user input in these tests; the dialogs just immediately throw an exception.
    avdManagerConnection =
      AvdManagerConnection(androidSdkHandler, avdManager, Dispatchers.Unconfined)
  }

  override fun tearDown() {
    super.tearDown()
    resetConnectionFactory()
  }

  fun testWipeAvd() {
    // Create an AVD
    val avd =
      avdManager.createAvd(
        avdFolder,
        name,
        systemImage,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        false,
      )
    // Create files that are present on some but not all AVDs.
    createFile(avdFolder, "sdcard.img")
    createFile(avdFolder, "user-settings.ini")

    // Create few additional files and directories.
    createFile(avdFolder, "cache.img")
    createFile(avdFolder, AvdManager.USERDATA_QEMU_IMG)
    createFile(avdFolder, "snapshots/default_boot/snapshot.pb")
    createFile(avdFolder, "data/misc/pstore/pstore.bin")

    // Do the "wipe-data"
    assertTrue("Could not wipe data from AVD", avdManagerConnection.wipeUserData(avd))

    val files: MutableList<Path?> = ArrayList()
    Files.list(avdFolder).use { stream -> stream.forEach { e: Path? -> files.add(e) } }
    assertThat(files)
      .containsExactly(
        avdFolder.resolve("config.ini"),
        avdFolder.resolve("sdcard.img"),
        avdFolder.resolve("user-settings.ini"),
        avdFolder.resolve("userdata.img"),
      )
  }

  // Note: This only tests a small part of startAvd(). We are not set up
  //       here to actually launch an Emulator instance.
  fun testStartAvdSkinned() {
    // Create an AVD with a skin
    val skinnyAvdName = "skinnyAvd"
    val skinnyAvdFolder = AvdInfo.getDefaultAvdFolder(avdManager, skinnyAvdName, false)
    val skinFolder = prefsRoot.resolve("skinFolder")
    Files.createDirectories(skinFolder)

    val skinnyAvd =
      avdManager.createAvd(
        skinnyAvdFolder,
        skinnyAvdName,
        systemImage,
        OnDiskSkin(skinFolder),
        null,
        null,
        null,
        null,
        false,
        true,
        false,
      )

    try {
      checkNotNull(skinnyAvd)
      runBlocking { avdManagerConnection.startAvd(null, skinnyAvd) }
      fail()
    } catch (expected: RuntimeException) {
      assertThat(expected.message).startsWith("No emulator installed")
    }
  }

  // Note: This only tests a small part of startAvd(). We are not set up here to actually launch an
  // Emulator instance.
  fun testStartAvdSkinless() {
    // Create an AVD without a skin
    val skinlessAvdName = "skinlessAvd"
    val skinlessAvdFolder = AvdInfo.getDefaultAvdFolder(avdManager, skinlessAvdName, false)

    val skinlessAvd =
      avdManager.createAvd(
        skinlessAvdFolder,
        skinlessAvdName,
        systemImage,
        null,
        null,
        null,
        null,
        null,
        false,
        true,
        false,
      )

    try {
      runBlocking { avdManagerConnection.startAvd(null, skinlessAvd) }
      fail()
    } catch (expected: RuntimeException) {
      assertThat(expected.message).startsWith("No emulator installed")
    }
  }

  fun testStudioParams() {
    val config = ProxyConfiguration.proxy(ProxyProtocol.HTTP, "proxy.com", 80, "")
    ProxyCredentialStore.getInstance()
      .setCredentials("proxy.com", 80, Credentials("myuser", "hunter2"), false)

    val params = config.toStudioParams(ProxyCredentialStore.getInstance())
    assertThat(params)
      .containsExactly(
        "http.proxyHost=proxy.com",
        "http.proxyPort=80",
        "proxy.authentication.username=myuser",
        "proxy.authentication.password=hunter2",
      )
  }

  private fun createFile(dir: Path, relativePath: String) {
    val file = dir.resolve(relativePath)
    PathSubject.assertThat(file.recordExistingFile("Contents of $relativePath")).exists()
  }

  companion object {
    private fun recordGoogleApisSysImg23(sdkRoot: Path) {
      sdkRoot.resolve("system-images/android-23/google_apis/x86_64/system.img").recordExistingFile()
      sdkRoot
        .resolve("system-images/android-23/google_apis/x86_64/" + AvdManager.USERDATA_IMG)
        .recordExistingFile("Some dummy info")
      sdkRoot
        .resolve("system-images/android-23/google_apis/x86_64/package.xml")
        .recordExistingFile(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<ns3:sdk-sys-img " +
            "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\">" +
            "<localPackage path=\"system-images;android-23;google_apis;x86_64\">" +
            "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>" +
            "<tag><id>google_apis</id><display>Google APIs</display></tag>" +
            "<vendor><id>google</id><display>Google Inc.</display></vendor>" +
            "<abi>x86_64</abi></type-details><revision><major>9</major></revision>" +
            "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>" +
            "</localPackage></ns3:sdk-sys-img>\n")
        )
    }

    private fun recordEmulatorVersion_23_4_5(sdkRoot: Path) {
      // This creates two 'package' directories.
      // We do not create a valid Emulator executable, so tests expect
      // a failure when they try to launch the Emulator.
      sdkRoot
        .resolve("emulator/package.xml")
        .recordExistingFile(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\"" +
            "                xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\">" +
            "  <localPackage path=\"emulator\">" +
            "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
            "        xsi:type=\"ns3:genericDetailsType\"/>" +
            "    <revision><major>23</major><minor>4</minor><micro>5</micro></revision>" +
            "    <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>" +
            "  </localPackage>" +
            "</ns2:repository>")
        )
      sdkRoot
        .resolve("tools/package.xml")
        .recordExistingFile(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\"" +
            "                xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\">" +
            "  <localPackage path=\"tools\">" +
            "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
            "        xsi:type=\"ns3:genericDetailsType\"/>" +
            "    <revision><major>23</major><minor>4</minor><micro>5</micro></revision>" +
            "    <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>" +
            "  </localPackage>" +
            "</ns2:repository>")
        )
    }
  }
}
