/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport

import com.android.ddmlib.IDevice
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Transport
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerExtension
import com.intellij.util.messages.MessageBus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.io.File

class TransportFileManagerTest {
  @get:Rule
  val timeout = Timeout.seconds(60)

  @JvmField
  @Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var mockDevice: IDevice
  private lateinit var fileManager: TransportFileManager

  @Before
  fun setUp() {
    val fakeExtension = object : TransportConfigContributor {
      override fun customizeProxyService(proxy: TransportProxy) { }
      override fun customizeDaemonConfig(configBuilder: Transport.DaemonConfig.Builder) { }
      override fun customizeAgentConfig(configBuilder: Agent.AgentConfig.Builder, runConfig: AndroidRunConfigurationBase?) { }
    }

    ApplicationManager.getApplication().registerExtension(TransportConfigContributor.EP_NAME, fakeExtension, disposableRule.disposable)

    mockDevice = mock(IDevice::class.java)
    fileManager = TransportFileManager(mockDevice)
  }

  @Test
  fun testCopyNonExecutableFileToDevice() {
    temporaryFolder.apply {
      newFolder("dev")
      newFile("dev/perfa.jar")
    }

    val hostFile = DeployableFile.Builder("perfa.jar")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(false)
      .setIsRunningFromSources(true)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    fileManager.copyHostFileToDevice(hostFile)
    verify(mockDevice, times(1)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())
    verify(mockDevice, times(1)).executeShellCommand(eq("chmod 444 ${TransportFileManager.DEVICE_DIR}perfa.jar"), any())
    verify(mockDevice, times(1)).executeShellCommand(eq("chown shell:shell ${TransportFileManager.DEVICE_DIR}perfa.jar"), any())

    val expectedPaths = listOf(
      Pair("dev" + File.separator + "perfa.jar", "perfa.jar")
    ).map { (host, device) ->
      // maps from relative paths to absolute paths
      Pair(temporaryFolder.root.absolutePath + File.separator + host, TransportFileManager.DEVICE_DIR + device)
    }

    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.first })
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.second })
  }

  @Test
  fun testCopyExecutableAbiIndependentFileToDevice() {
    temporaryFolder.apply {
      newFolder("dev")

      listOf(Abi.X86, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFolder("dev", it.toString())
      }
      listOf(Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFile("dev/$it/transport")
      }
    }

    val hostFile = DeployableFile.Builder("transport")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(true)
      .setIsRunningFromSources(true)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    whenever(mockDevice.abis).thenReturn(listOf(
      // it will be ignored, because there is no perfd under it.
      Abi.X86,
      // it will be used.
      Abi.ARMEABI_V7A,
      // it will be ignored, because we only need one ABI. |IDevice#getAbis| are sorted in preferred order,
      // so it should choose |Abi.ARMEABI_V7A| instead.
      Abi.ARMEABI
    ).map { it.toString() })

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    fileManager.copyHostFileToDevice(hostFile)
    verify(mockDevice, times(1)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())

    val expectedPaths = listOf(
      Pair("dev" + File.separator + Abi.ARMEABI_V7A + File.separator + "transport", "transport")
    ).map { (host, device) ->
      // maps from relative paths to absolute paths
      Pair(temporaryFolder.root.absolutePath + File.separator + host, TransportFileManager.DEVICE_DIR + device)
    }

    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.first })
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.second })
    verify(mockDevice, times(1)).executeShellCommand(eq("chmod 755 ${TransportFileManager.DEVICE_DIR}transport"), any())
    verify(mockDevice, times(1)).executeShellCommand(eq("chown shell:shell ${TransportFileManager.DEVICE_DIR}transport"), any())
  }

  @Test
  fun testCopyExecutableAbiDependentFileToDevice() {
    temporaryFolder.apply {
      newFolder("dev")

      listOf(Abi.X86, Abi.X86_64, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFolder("dev", it.toString())
      }
      listOf(Abi.X86_64, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFile("dev/${it}/simpleperf")
      }
    }

    val hostFile = DeployableFile.Builder("simpleperf")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("simpleperf_%s")
      .setIsRunningFromSources(true)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    whenever(mockDevice.abis).thenReturn(listOf(
      // it will be ignored, because there is no simpleperf under it.
      Abi.X86,
      // it will be used.
      Abi.ARMEABI,
      // it will be ignored, because we only need one ABI per CPU arch.
      // It should choose |Abi.ARMEABI| instead, because it is more preferred and has the same CPU arch.
      Abi.ARMEABI_V7A,
      // it will be used.
      Abi.X86_64
    ).map { it.toString() })

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    fileManager.copyHostFileToDevice(hostFile)
    verify(mockDevice, times(2)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())

    val expectedAbis = listOf(
      Abi.ARMEABI,
      Abi.X86_64
    )

    val expectedHostPaths = expectedAbis.map {
      temporaryFolder.root.absolutePath + File.separator + "dev" + File.separator + it + File.separator + "simpleperf"
    }
    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedHostPaths)

    val expectedDevicePaths = expectedAbis.map { "${TransportFileManager.DEVICE_DIR}simpleperf_${it.cpuArch}" }
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedDevicePaths)
  }

  @Test
  fun testCopyExecutableAbiDependentFileInFolderToDevice() {
    temporaryFolder.apply {
      newFolder("dev")

      listOf(Abi.X86, Abi.X86_64, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFolder("dev", it.toString())
      }
      listOf(Abi.X86_64, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFile("dev/${it}/perfetto")
      }
    }

    val hostFile = DeployableFile.Builder("perfetto")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("%s/perfetto")
      .setIsRunningFromSources(true)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    whenever(mockDevice.abis).thenReturn(listOf(
      // it will be ignored, because there is no simpleperf under it.
      Abi.X86,
      // it will be used.
      Abi.ARMEABI,
      // it will be ignored, because we only need one ABI per CPU arch.
      // It should choose |Abi.ARMEABI| instead, because it is more preferred and has the same CPU arch.
      Abi.ARMEABI_V7A,
      // it will be used.
      Abi.X86_64
    ).map { it.toString() })

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    fileManager.copyHostFileToDevice(hostFile)
    verify(mockDevice, times(2)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())
    val expectedAbis = listOf(
      Abi.ARMEABI,
      Abi.X86_64
    )

    val expectedHostPaths = expectedAbis.map {
      temporaryFolder.root.absolutePath + File.separator + "dev" + File.separator + it + File.separator + "perfetto"
    }
    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedHostPaths)

    val expectedDevicePaths = expectedAbis.map { "${TransportFileManager.DEVICE_DIR}${it.cpuArch}/perfetto" }
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedDevicePaths)
    expectedAbis.map {
      val filePath = "${TransportFileManager.DEVICE_DIR}${it.cpuArch}"
      verify(mockDevice, times(1)).executeShellCommand(eq("mkdir -p -m 755 $filePath; chown shell:shell $filePath"), any())
    }
  }

  @Test
  fun testTraceboxFlagWithDeviceBetweenLAndQInclusive() {
    // Files expected to be copied for device L include TRANSPORT
    testNumberOfFilesToCopy(true, 21, 1)
    testNumberOfFilesToCopy(false, 21, 1)

    // Files expected to be copied for device M include TRANSPORT, TRACEBOX
    testNumberOfFilesToCopy(true, 23, 2)
    // Files expected to be copied for device M include TRANSPORT
    testNumberOfFilesToCopy(false, 23, 1)

    // Files expected to be copied for device O include TRANSPORT, PERFA, JVMTI_AGENT, SIMPLEPERF, TRACEBOX
    testNumberOfFilesToCopy(true, 26, 5)
    // Files expected to be copied include for device O TRANSPORT, PERFA, JVMTI_AGENT, SIMPLEPERF
    testNumberOfFilesToCopy(false, 26, 4)

    // Files expected to be copied for device P include TRANSPORT, PERFA, JVMTI_AGENT, SIMPLEPERF, TRACEBOX
    testNumberOfFilesToCopy(true, 28, 5)
    // Files expected to be copied for device P include TRANSPORT, PERFA, JVMTI_AGENT, SIMPLEPERF, PERFETTO, PERFETTO_SO,
    // TRACED, TRACED_PROBE
    testNumberOfFilesToCopy(false, 28, 8)

    // Files expected to be copied for device Q include TRANSPORT, PERFA, JVMTI_AGENT, SIMPLEPERF
    testNumberOfFilesToCopy(true, 29, 4)
    // Files expected to be copied for device Q include TRANSPORT, PERFA, JVMTI_AGENT, SIMPLEPERF
    testNumberOfFilesToCopy(false, 29, 4)
  }

  private fun testNumberOfFilesToCopy(traceboxFlag: Boolean, apiLevel: Int, expectedNumberOfFiles: Int) {
    StudioFlags.PROFILER_TRACEBOX.override(traceboxFlag)
    val device = mock(IDevice::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockMessageBus = mock(MessageBus::class.java, Mockito.RETURNS_DEEP_STUBS)
    val transportDeviceManagerListener =
      mock(TransportDeviceManager.TransportDeviceManagerListener::class.java, Mockito.RETURNS_DEEP_STUBS)

    doReturn(transportDeviceManagerListener).whenever(mockMessageBus).syncPublisher(TransportDeviceManager.TOPIC)
    val fileManagerSpy = spy(TransportFileManager(device))

    doReturn(ArrayList<String>()).whenever(fileManagerSpy).copyFileToDevice(any(DeployableFile::class.java))
    whenever(device.version.featureLevel).thenReturn(apiLevel)

    fileManagerSpy.copyFilesToDevice();
    verify(fileManagerSpy, times(expectedNumberOfFiles)).copyFileToDevice(any(DeployableFile::class.java))
  }
}