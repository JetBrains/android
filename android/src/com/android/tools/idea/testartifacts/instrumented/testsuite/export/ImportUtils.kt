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
package com.android.tools.idea.testartifacts.instrumented.testsuite.export

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.HistoryTestRunnableState
import com.intellij.execution.testframework.sm.SmRunnerBundle
import com.intellij.execution.testframework.sm.runner.history.ImportedTestRunnableState
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.xml.parsers.SAXParserFactory

/**
 * Imports AndroidTestMatrixResult results from given [xmlFile].
 *
 * @param onExecutionStarted a callback func which is called after an execution of test import run profile started.
 * @return true if a given xmlFile has `androidTestMatrix` element, otherwise false.
 */
@UiThread
fun importAndroidTestMatrixResultXmlFile(project: Project, xmlFile: VirtualFile,
                                         onExecutionStarted: (ExecutionEnvironment) -> Unit = {}): Boolean {
  val rootElement = JDOMUtil.load(VfsUtilCore.virtualToIoFile(xmlFile))
  if (rootElement.getChild("androidTestMatrix") == null) {
    return false
  }

  try {
    val runProfile = ImportAndroidTestMatrixRunProfile(xmlFile, project)
    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
                   ?: return false
    val builder = ExecutionEnvironmentBuilder.create(project, executor, runProfile)
    runProfile.target?.let { builder.target(it) }
    val runner = ProgramRunner.getRunner(executor.id, runProfile) ?: object : GenericProgramRunner<RunnerSettings>() {
      override fun canRun(executorId: String, profile: RunProfile) = true
      override fun getRunnerId() = "AndroidTestMatrixResultXmlFileRunner"
    }
    builder.runner(runner)
    val env = builder.build()
    env.runner.execute(env)
    onExecutionStarted(env)
  } catch (e: ExecutionException) {
    Messages.showErrorDialog(
      project, e.message,
      SmRunnerBundle.message("sm.test.runner.abstract.import.test.error.title"))
  }

  return true
}

/**
 * Returns a timestamp in millis when the first test case execution started.
 * If no test cases found, it falls back to [File.lastModified].
 */
@Slow
fun getTestStartTime(xmlFile: File): Long {
  val rootElement = JDOMUtil.load(xmlFile)
  val testMatrixElement = rootElement.getChild("androidTestMatrix") ?: return xmlFile.lastModified()
  val firstTestCaseElement = testMatrixElement.getChild("testsuite")?.getChild("testcase") ?: return xmlFile.lastModified()
  return firstTestCaseElement.getAttribute("startTimestampMillis")?.value?.toLongOrNull() ?: xmlFile.lastModified()
}

private class ImportAndroidTestMatrixRunProfile(private val historyXmlFile: VirtualFile, project: Project)
  : AbstractImportTestsAction.ImportRunProfile(historyXmlFile, project) {
  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    val state = super.getState(executor, environment)
    return if (state is ImportedTestRunnableState) {
      ImportAndroidTestMatrixRunProfileState(this, VfsUtilCore.virtualToIoFile(historyXmlFile))
    } else {
      state
    }
  }
}

private class ImportAndroidTestMatrixRunProfileState(
  private val importRunProfile: ImportAndroidTestMatrixRunProfile,
  private val historyXmlFile: File)
  : RunProfileState, HistoryTestRunnableState {
  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
    val handler = object: ProcessHandler() {
      override fun destroyProcessImpl() {}
      override fun detachProcessImpl() { notifyProcessTerminated(0) }
      override fun detachIsDefault(): Boolean = false
      override fun getProcessInput(): OutputStream? = null
    }
    val console = AndroidTestSuiteView(
      importRunProfile.project, importRunProfile.project, null, executor.toolWindowId)
    console.attachToProcess(handler)
    handler.detachProcess()

    ApplicationManager.getApplication().executeOnPooledThread {
      val saxParser = SAXParserFactory.newInstance().newSAXParser()
      saxParser.parse(
        InputSource(InputStreamReader(FileInputStream(historyXmlFile), StandardCharsets.UTF_8)),
        object: DefaultHandler() {

          private var myProcessingAndroidTestMatrixElement: Boolean = false
          private val myDevices: MutableMap<String, AndroidDevice> = mutableMapOf()
          private var myCurrentTargetDevice: AndroidDevice? = null
          private var myCurrentTestSuite: AndroidTestSuite? = null

          override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (!myProcessingAndroidTestMatrixElement) {
              if (qName != "androidTestMatrix") {
                return
              }
              myProcessingAndroidTestMatrixElement = true
              attributes.getValue("executionDuration")?.toLongOrNull()?.let {
                console.testExecutionDurationOverride = Duration.ofMillis(it)
              }
            }
            when(qName) {
              "device" -> {
                val device = AndroidDevice(
                  attributes.getValue("id"),
                  attributes.getValue("deviceName"),
                  attributes.getValue("deviceName"),
                  AndroidDeviceType.valueOf(attributes.getValue("deviceType")),
                  AndroidVersion(attributes.getValue("version").toInt())
                )
                myDevices[device.id] = device
                myCurrentTargetDevice = device
              }

              "additionalInfo" -> {
                requireNotNull(myCurrentTargetDevice)
                  .additionalInfo[attributes.getValue("key")] = attributes.getValue("value")
              }

              "testsuite" -> {
                val device = requireNotNull(myDevices[attributes.getValue("deviceId")])
                val testSuite = AndroidTestSuite(
                  device.id,
                  device.id,
                  attributes.getValue("testCount").toInt(),
                  AndroidTestSuiteResult.valueOf(attributes.getValue("result")))
                myCurrentTargetDevice = device
                myCurrentTestSuite = testSuite
                console.onTestSuiteStarted(device, testSuite)
              }

              "testcase" -> {
                val testcase = AndroidTestCase(
                  attributes.getValue("id"),
                  attributes.getValue("methodName"),
                  attributes.getValue("className"),
                  attributes.getValue("packageName"),
                  AndroidTestCaseResult.valueOf(attributes.getValue("result")),
                  attributes.getValue("logcat"),
                  attributes.getValue("errorStackTrace"),
                  attributes.getValue("startTimestampMillis").toLong(),
                  attributes.getValue("endTimestampMillis").toLong(),
                  attributes.getValue("benchmark")
                )
                val device = requireNotNull(myCurrentTargetDevice)
                val testsuite = requireNotNull(myCurrentTestSuite)
                console.onTestCaseStarted(device, testsuite, testcase)
                console.onTestCaseFinished(device, testsuite, testcase)
              }
            }
          }

          override fun endElement(uri: String, localName: String, qName: String) {
            if (!myProcessingAndroidTestMatrixElement) {
              return
            }
            when(qName) {
              "androidTestMatrix" -> {
                myProcessingAndroidTestMatrixElement = false
              }

              "device" -> {
                console.onTestSuiteScheduled(requireNotNull(myCurrentTargetDevice))
              }

              "testsuite" -> {
                console.onTestSuiteFinished(
                  requireNotNull(myCurrentTargetDevice),
                  requireNotNull(myCurrentTestSuite))
              }
            }
          }
        })
    }

    return DefaultExecutionResult(console, handler)
  }
}
