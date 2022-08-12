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
package com.android.tools.idea.appinspection.internal.process

import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.net.Socket

class DebuggableProcessFilterTest {
  private val commandHandler = FakeShellDumpSysCommandHandler()
  @get:Rule
  val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)

  @Test
  fun testIsProcessDebuggable() {
    adbRule.attachDevice("emulator-123", "Google", "Pixel", "29", "29", "arm64-v8a", emptyMap(), USB, "MyAvd", "/path")
    val device: IDevice = adbRule.bridge.devices.single()
    assertThat(device.isPackageDebuggable("com.google.android.webview")).isFalse()
    assertThat(device.isPackageDebuggable("androidx.compose.ui.test")).isTrue()
    assertThat(device.isPackageDebuggable("unknown")).isFalse()
  }
}

private const val DUMP_PACKAGE = "dumpsys package "

private class FakeShellDumpSysCommandHandler : DeviceCommandHandler("shell") {
  override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
    val response = when (command) {
      "shell" -> handleShellCommand(args)
      else -> return false
    }
    writeOkay(socket.getOutputStream())
    writeString(socket.getOutputStream(), response)
    return true
  }

  private fun handleShellCommand(args: String): String {
    if (args.startsWith(DUMP_PACKAGE)) {
      return dumpPackage(args.substringAfter(DUMP_PACKAGE))
    }
    return ""
  }

  private fun dumpPackage(args: String): String = when (args) {
    "com.google.android.webview" -> """
      Package [com.google.android.webview] (a768fce):
        userId=10116
        pkg=Package{ba02eef com.google.android.webview}
        codePath=/product/app/WebViewGoogle-Stub
        resourcePath=/product/app/WebViewGoogle-Stub
        legacyNativeLibraryDir=/product/app/WebViewGoogle-Stub/lib
        extractNativeLibs=false
        primaryCpuAbi=null
        secondaryCpuAbi=null
        cpuAbiOverride=null
        versionCode=447211487 minSdk=29 targetSdk=31
        minExtensionVersions=[]
        versionName=91.0.4472.114
        usesNonSdkApi=false
        splits=[base]
        apkSigningVersion=3
        applicationInfo=PackageImpl{ba02eef com.google.android.webview}
        flags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]
        privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION ALLOW_AUDIO_PLAYBACK_CAPTURE ISOLATED_SPLIT_LOADING PRODUCT PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING ]
        forceQueryable=false
        queriesPackages=[]
        queriesIntents=[Intent { act=android.media.browse.MediaBrowserService }]
        dataDir=/data/user/0/com.google.android.webview
        supportsScreens=[small, medium, large, xlarge, resizeable, anyDensity]
        usesStaticLibraries:
          com.google.android.trichromelibrary version:447211487
        timeStamp=2021-08-25 13:27:56
        firstInstallTime=2021-08-25 13:27:56
        lastUpdateTime=2021-08-25 13:27:56
        signatures=PackageSignatures{ffe9d7e version:3, signatures:[f4ae824f], past signatures:[]}
        installPermissionsFixed=false
        pkgFlags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]
        install permissions:
          android.permission.FOREGROUND_SERVICE: granted=true
          android.permission.INTERNET: granted=true
          android.permission.ACCESS_NETWORK_STATE: granted=true
        User 0: ceDataInode=0 installed=true hidden=false suspended=false distractionFlags=0 stopped=false notLaunched=false enabled=0 instant=false virtual=false
          gids=[3003]
    """.trimIndent()

    "androidx.compose.ui.test" -> """
      Package [androidx.compose.ui.test] (37f7072):
        userId=10137
        pkg=Package{bb1ccc3 androidx.compose.ui.test}
        codePath=/data/app/androidx.compose.ui.test-FfmGwXpOUKUBL-ZnNRFf7A==
        resourcePath=/data/app/androidx.compose.ui.test-FfmGwXpOUKUBL-ZnNRFf7A==
        legacyNativeLibraryDir=/data/app/androidx.compose.ui.test-FfmGwXpOUKUBL-ZnNRFf7A==/lib
        primaryCpuAbi=null
        secondaryCpuAbi=null
        versionCode=0 minSdk=21 targetSdk=31
        versionName=null
        splits=[base]
        apkSigningVersion=2
        applicationInfo=ApplicationInfo{8e9c940 androidx.compose.ui.test}
        flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]
        privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION ALLOW_AUDIO_PLAYBACK_CAPTURE ]
        dataDir=/data/user/0/androidx.compose.ui.test
        supportsScreens=[small, medium, large, xlarge, resizeable, anyDensity]
        usesLibraries:
          android.test.mock
          android.test.runner
        usesLibraryFiles:
          /system/framework/android.test.mock.jar
          /system/framework/android.test.runner.jar
        timeStamp=2021-08-25 13:43:32
        firstInstallTime=2021-08-25 13:43:32
        lastUpdateTime=2021-08-25 13:43:32
        signatures=PackageSignatures{e5c6079 version:2, signatures:[d5c13030], past signatures:[]}
        installPermissionsFixed=true
        pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]
        install permissions:
          android.permission.REORDER_TASKS: granted=true
        User 0: ceDataInode=-4294835830 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0 instant=false virtual=false
        overlay paths:
          /product/overlay/DisplayCutoutEmulationEmu01/DisplayCutoutEmulationEmu01Overlay.apk
      """.trimIndent()

    else -> ""
  }
}
