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
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto.AndroidTestDeviceInfo
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.protobuf.Timestamp
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.openapi.util.io.FileUtil.exists
import java.io.File
import java.nio.charset.Charset
import java.util.UUID


/**
 * An adapter to parse Unified Test Platform (UTP) result protobuf, and forward them to
 * AndroidTestResultListener.
 */
class UtpTestResultAdapter(private val protoFile: File) {

  private val resultProto: TestSuiteResultProto.TestSuiteResult by lazy {
    if (protoFile.extension == "textproto") {
      val builder = TestSuiteResultProto.TestSuiteResult.newBuilder()
      TextFormat.merge(protoFile.readText(Charset.defaultCharset()),
                       builder)
      builder.build()
    } else {
      protoFile.inputStream().use {
        TestSuiteResultProto.TestSuiteResult.parseFrom(it)
      }
    }
  }

  /**
   * The first package name in the test suite. Null if there is no package name.
   */
  val packageName: String? by lazy {
    resultProto.testResultList.mapNotNull {
      it?.testCase?.testPackage
    }.firstOrNull()
  }

  /**
   * Parse UTP test results and forward to the listener
   *
   * @param listener the listener to receive the test results
   */
  @WorkerThread
  fun forwardResults(listener: AndroidTestResultListener) {
    val artifactFileResolver = ArtifactFileResolver(protoFile.parentFile)
    val androidDeviceResolver = AndroidDeviceResolver(artifactFileResolver)
    val deviceToTestCountMap: MutableMap<AndroidDevice, Int> = sortedMapOf(compareBy { it.id })
    val deviceToTestSuiteMap: MutableMap<AndroidDevice, AndroidTestSuite> = sortedMapOf(compareBy { it.id })

    resultProto.testResultList.forEach { testResult ->
      val device = androidDeviceResolver.getAndroidDevice(testResult)
      deviceToTestCountMap.compute(device) { _, currentValue ->
        currentValue?.plus(1) ?: 1
      }
    }

    resultProto.testResultList.forEach { testResult ->
      val device = androidDeviceResolver.getAndroidDevice(testResult)
      val testSuite = deviceToTestSuiteMap.computeIfAbsent(device) { newDevice ->
        AndroidTestSuite(
          resultProto.testSuiteMetaData.testSuiteName,
          resultProto.testSuiteMetaData.testSuiteName,
          deviceToTestCountMap.getOrDefault(device, 0),
          AndroidTestSuiteResult.PASSED
        ).also {
          listener.onTestSuiteScheduled(newDevice)
          listener.onTestSuiteStarted(newDevice, it)
        }
      }

      val testCase = createTestCase(testResult, artifactFileResolver)
      if (testResult.testStatus == TestStatus.FAILED) {
        testSuite.result = AndroidTestSuiteResult.FAILED
      }

      listener.onTestCaseStarted(device, testSuite, testCase)
      listener.onTestCaseFinished(device, testSuite, testCase)
    }

    deviceToTestSuiteMap.forEach(listener::onTestSuiteFinished)
  }

  private fun createTestCase(testResult: TestResult, artifactFileResolver: ArtifactFileResolver): AndroidTestCase {
    val testCase = testResult.testCase
    return AndroidTestCase(
      id = "${testCase.testPackage}.${testCase.testClass}#${testCase.testMethod}",
      methodName = testCase.testMethod,
      className = testCase.testClass,
      packageName = testCase.testPackage,
      retentionInfo = artifactFileResolver.getArtifactFile(testResult, "icebox.info"),
      retentionSnapshot = getIceboxArtifact(testResult, artifactFileResolver),
      result = when (testResult.testStatus) {
        TestStatus.PASSED -> AndroidTestCaseResult.PASSED
        TestStatus.FAILED -> AndroidTestCaseResult.FAILED
        else -> AndroidTestCaseResult.SKIPPED
      },
      logcat = artifactFileResolver.getArtifactFile(testResult, "logcat")?.readText() ?: "",
      startTimestampMillis = testCase.startTime.millis(),
      endTimestampMillis = testCase.endTime.millis(),
    ).apply {
      setBenchmarkContextAndPrepareFiles(testResult, this) { outputArtifactPath ->
        artifactFileResolver.resolveFile(outputArtifactPath) ?: File(outputArtifactPath)
      }
    }
  }

  private fun getIceboxArtifact(testResult: TestResult, artifactFileResolver: ArtifactFileResolver): File? {
    val iceboxArtifactRegrex =
      "snapshot-${testResult.testCase.testClass}-${testResult.testCase.testMethod}-failure[0-9]+(\\.tar(\\.gz)?)?"
      .toRegex()
    return artifactFileResolver.getArtifactFile(testResult) { artifact ->
      iceboxArtifactRegrex.matches(File(artifact.sourcePath.path).name)
    }
  }

  private fun Timestamp.millis(): Long {
    return seconds * 1000L + nanos / 1000000L
  }
}

/**
 * Resolves associated AndroidDevice for a given test result.
 */
private class AndroidDeviceResolver(private val artifactFileResolver: ArtifactFileResolver) {

  companion object {
    private const val DEFAULT_DEVICE_NAME = "Unknown device"
    private val DEFAULT_DEVICE_TYPE = AndroidDeviceType.LOCAL_PHYSICAL_DEVICE

    private val DEFAULT_ANDROID_DEVICE = AndroidDevice(
      DEFAULT_DEVICE_NAME,
      DEFAULT_DEVICE_NAME,
      DEFAULT_DEVICE_NAME,
      DEFAULT_DEVICE_TYPE,
      AndroidVersion.DEFAULT
    )
  }

  private val deviceMap: MutableMap<File, AndroidDevice> = mutableMapOf()

  fun getAndroidDevice(testResult: TestResult): AndroidDevice {
    val resolvedFile = artifactFileResolver.getArtifactFile(testResult, "device-info") ?: return DEFAULT_ANDROID_DEVICE
    return deviceMap.computeIfAbsent(resolvedFile) {
      val deviceInfo = resolvedFile.inputStream().use { inputStream ->
        AndroidTestDeviceInfo.parseFrom(inputStream)
      }
      createAndroidDevice(deviceInfo)
    }
  }

  private fun createAndroidDevice(deviceInfo: AndroidTestDeviceInfo): AndroidDevice {
    val deviceType = when {
      deviceInfo.avdName.isEmpty() -> AndroidDeviceType.LOCAL_PHYSICAL_DEVICE
      deviceInfo.gradleDslDeviceName.isEmpty() -> AndroidDeviceType.LOCAL_EMULATOR
      else -> AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR
    }
    return AndroidDevice(
      UUID.randomUUID().toString(),
      deviceInfo.displayName(),
      deviceInfo.avdName,
      deviceType,
      AndroidVersion(deviceInfo.apiLevel),
    ).apply {
      if (deviceInfo.manufacturer.isNotBlank()) {
        additionalInfo["Manufacturer"] = deviceInfo.manufacturer
      }
      if (deviceInfo.model.isNotBlank()) {
        additionalInfo["Model"] = deviceInfo.model
      }
      if (deviceInfo.processorsCount > 0) {
        additionalInfo["Processor"] = deviceInfo.processorsList.joinToString("\n")
      }
      if (deviceInfo.ramInBytes > 0) {
        additionalInfo["RAM"] = String.format("%.1f GB", deviceInfo.ramInBytes.toFloat() / 1000 / 1000 / 1000)
      }
    }
  }

  private fun AndroidTestDeviceInfo.displayName(): String {
    return if (gradleDslDeviceName.isNotEmpty()) {
      "Gradle:$gradleDslDeviceName"
    } else {
      name
    }
  }
}

/**
 * Resolves an output artifact file from a given test result.
 */
private class ArtifactFileResolver(private val parentDir: File) {
  private val relativePathToFile: MutableMap<String, File?> = mutableMapOf()

  fun getArtifactFile(testResult: TestResult, label: String, namespace: String = "android"): File? {
    return getArtifactFile(testResult) { artifact ->
      artifact.label.label == label &&
      artifact.label.namespace == namespace &&
      artifact.sourcePath.path.isNotBlank()
    }
  }

  fun getArtifactFile(testResult: TestResult, filterFunc: (Artifact) -> Boolean): File? {
    val relativePath = getRelativePath(testResult, filterFunc) ?: return null
    return relativePathToFile.computeIfAbsent(relativePath) {
      resolveFile(it)
    }
  }

  private fun getRelativePath(testResult: TestResult, filterFunc: (Artifact) -> Boolean): String? {
    return testResult.outputArtifactList.asSequence()
      .filter(filterFunc)
      .map { artifact ->
        artifact.sourcePath.path
      }
      .firstOrNull()
  }

  /**
   * Try to find a file. The fallbacks of file path is as follows:
   *
   * (1) Try absolute path.
   * (2) Try relative path.
   * (3) Try to get the file name and use it as relative path. This is useful because currently UTP writes absolute path in the proto.
   */
  fun resolveFile(relativePath: String): File? {
    if (exists(relativePath)) {
      return File(relativePath)
    }
    val file2 = parentDir.resolve(relativePath)
    if (file2.exists()) {
      return file2
    }
    val file3 = parentDir.resolve(File(relativePath).name)
    if (file3.exists()) {
      return file3
    }
    return null
  }
}
