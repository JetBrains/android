/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.annotations.concurrency.WorkerThread
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto
import com.google.common.annotations.VisibleForTesting
import com.google.protobuf.Timestamp
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.openapi.util.io.FileUtil.exists
import java.io.File

@VisibleForTesting
const val DEVICE_INFO_LABEL = "device-info"

@VisibleForTesting
const val DEVICE_INFO_NAMESPACE = "android"

private const val DEFAULT_DEVICE_NAME = "Unknown device"
private val DEFAULT_DEVICE_TYPE = AndroidDeviceType.LOCAL_PHYSICAL_DEVICE

private data class DeviceTestSuite(val device: AndroidDevice) {
  lateinit var testSuite: AndroidTestSuite
}

private typealias DeviceMap = Map<Pair<String, AndroidDeviceType>, DeviceTestSuite>

/**
 * An adapter to parse Unified Test Platform (UTP) result protobuf, and forward them to
 * AndroidTestResultListener
 *
 * @param listener the listener to receive the test results
 */
class UtpTestResultAdapter(private val protoFile: File) {
  private val resultProto = TestSuiteResultProto.TestSuiteResult.parseFrom(protoFile.inputStream())
  private val dir = protoFile.parentFile
  private val deviceMap = getDeviceMap(dir, resultProto)

  /**
   * Return the first package name in the test suite. Null if there is no package name.
   *
   * @return package name.
   */
  fun getPackageName(): String? {
    return resultProto.testResultList.asSequence().map {
      it?.testCase?.testPackage
    }.filterNotNull().firstOrNull()
  }
  /**
   * Parse UTP test results and forward to the listener
   *
   * @param inputStream contains the binary protobuf of the UTP test suite results
   */
  @WorkerThread
  fun forwardResults(listener: AndroidTestResultListener) {
    val dir = protoFile.parentFile
    startAll(listener, deviceMap, resultProto)
    runAll(listener, deviceMap, dir, resultProto)
    finishAll(listener, deviceMap)
  }

  private fun getDeviceMap(dir: File, resultProto: TestSuiteResultProto.TestSuiteResult): DeviceMap {
    val defaultDevice = AndroidDevice(DEFAULT_DEVICE_NAME,
                                      DEFAULT_DEVICE_NAME,
                                      DEFAULT_DEVICE_NAME,
                                      DEFAULT_DEVICE_TYPE,
                                      AndroidVersion.DEFAULT)
    val defaultDeviceSuite = DeviceTestSuite(defaultDevice)
    var id = 0
    val deviceSequence = resultProto.testResultList.asSequence().map<TestResultProto.TestResult, DeviceTestSuite> {
      val deviceInfo = it.getDeviceInfo(dir)
      if (deviceInfo == null) {
        return@map defaultDeviceSuite
      } else {
        val deviceType = when {
          deviceInfo.avdName.isEmpty() -> AndroidDeviceType.LOCAL_PHYSICAL_DEVICE
          deviceInfo.gradleDslDeviceName.isEmpty() -> AndroidDeviceType.LOCAL_EMULATOR
          else -> AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR
        }

        val device = AndroidDevice(
          id.toString(),
          deviceInfo.displayName(),
          deviceInfo.avdName,
          deviceType,
          AndroidVersion(deviceInfo.apiLevel)
        )
        id += 1
        return@map DeviceTestSuite(device)
      }
    }
    return deviceSequence.associateBy { deviceSuite: DeviceTestSuite ->
      Pair(deviceSuite.device.deviceName, deviceSuite.device.deviceType)
    }
  }

  private fun startAll(listener: AndroidTestResultListener, deviceMap: DeviceMap, resultProto: TestSuiteResultProto.TestSuiteResult) {
    for (deviceTestSuite in deviceMap.values) {
      listener.onTestSuiteScheduled(deviceTestSuite.device)
      deviceTestSuite.testSuite = AndroidTestSuite(resultProto.testSuiteMetaData.testSuiteName,
                                                   resultProto.testSuiteMetaData.testSuiteName,
                                                   resultProto.testResultCount,
                                                   AndroidTestSuiteResult.PASSED)
      listener.onTestSuiteStarted(deviceTestSuite.device, deviceTestSuite.testSuite)
    }
  }

  private fun runAll(listener: AndroidTestResultListener, deviceMap: DeviceMap, dir: File, resultProto: TestSuiteResultProto.TestSuiteResult) {
    val defaultDeviceKey = Pair(DEFAULT_DEVICE_NAME, DEFAULT_DEVICE_TYPE)
    for (testResultProto in resultProto.testResultList) {
      val deviceInfo = testResultProto.getDeviceInfo(dir)
      val deviceKey = if (deviceInfo == null) {
        defaultDeviceKey
      } else {
        Pair(deviceInfo.displayName(), deviceInfo.deviceType())
      }
      val deviceTestSuite = deviceMap[deviceKey]!!
      val device = deviceTestSuite.device
      val testSuite = deviceTestSuite.testSuite
      val testCaseProto = testResultProto.testCase
      val fullName = "${testCaseProto.testPackage}.${testCaseProto.testClass}#${testCaseProto.testMethod}"
      val iceboxArtifactRegrex = "snapshot-${testCaseProto.testClass}-${testCaseProto.testMethod}-failure[0-9]+\\.tar(\\.gz)?".toRegex()
      val iceboxArtifact = testResultProto.outputArtifactList.find {
        iceboxArtifactRegrex.matches(File(it.sourcePath?.path).name)
      }
      val iceboxInfo = testResultProto.outputArtifactList.find {
        it.label.label == "icebox.info" && it.label.namespace == "android"
      }
      val retentionArtifactFile = resolveFile(dir, iceboxArtifact?.sourcePath?.path)
      val iceboxInfoFile = resolveFile(dir, iceboxInfo?.sourcePath?.path)
      val testCase = AndroidTestCase(id = fullName,
                                     methodName = testCaseProto.testMethod,
                                     className = testCaseProto.testClass,
                                     packageName = testCaseProto.testPackage,
                                     retentionInfo = iceboxInfoFile,
                                     retentionSnapshot = retentionArtifactFile,
                                     result = when (testResultProto.testStatus) {
                                       TestStatusProto.TestStatus.PASSED -> AndroidTestCaseResult.PASSED
                                       TestStatusProto.TestStatus.FAILED -> AndroidTestCaseResult.FAILED
                                       else -> AndroidTestCaseResult.SKIPPED
                                     },
                                     startTimestampMillis = testCaseProto.startTime.millis(),
                                     endTimestampMillis = testCaseProto.endTime.millis()
      )
      if (testResultProto.testStatus == TestStatusProto.TestStatus.FAILED) {
        testSuite.result = AndroidTestSuiteResult.FAILED
      }
      listener.onTestCaseStarted(device, testSuite, testCase)
      listener.onTestCaseFinished(device, testSuite, testCase)
    }
  }

  private fun finishAll(listener: AndroidTestResultListener, deviceMap: DeviceMap) {
    for (deviceTestSuite in deviceMap.values) {
      listener.onTestSuiteFinished(deviceTestSuite.device, deviceTestSuite.testSuite)
    }
  }
}

private fun TestResultProto.TestResult.getDeviceInfo(dir: File): AndroidTestDeviceInfoProto.AndroidTestDeviceInfo? {
  val info_artifact = outputArtifactList.find { artifact ->
    artifact.label.label == DEVICE_INFO_LABEL && artifact.label.namespace == DEVICE_INFO_NAMESPACE
  }
  if (info_artifact == null) {
    return null
  } else {
    val deviceInfoFile = resolveFile(dir, info_artifact.sourcePath?.path)
    if (deviceInfoFile == null) {
      return null
    } else {
      return AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.parseFrom(deviceInfoFile.inputStream())
    }
  }
}

private fun AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.displayName(): String =
    if (gradleDslDeviceName.isNotEmpty()) "Gradle:$gradleDslDeviceName" else name

private fun AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.deviceType(): AndroidDeviceType = when {
    avdName.isEmpty() -> AndroidDeviceType.LOCAL_PHYSICAL_DEVICE
    gradleDslDeviceName.isEmpty() -> AndroidDeviceType.LOCAL_EMULATOR
    else -> AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR
}

// Try to find a file. The fallbacks of file path is as follows:
//
// (1) Try absolute path.
// (2) Try relative path.
// (3) Try to get the file name and use it as relative path. This is useful because currently UTP writes absolute path in the proto.
private fun resolveFile(dir: File, filename: String?): File? {
  if (filename == null) {
    return null
  }
  if (exists(filename)) {
    return File(filename)
  }
  val file2 = dir.resolve(filename)
  if (file2.exists()) {
    return file2
  }
  val file3 = dir.resolve(File(filename).name)
  if (file3.exists()) {
    return file3
  }
  return null
}

private fun Timestamp.millis(): Long {
  return seconds * 1000L + nanos / 1000000L
}