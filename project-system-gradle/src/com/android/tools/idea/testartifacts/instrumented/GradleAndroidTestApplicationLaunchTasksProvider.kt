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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.methodTest
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * LaunchTasksProvider that provides GradleAndroidTestApplicationLaunchTasks for instrumentation tests
 */
class GradleAndroidTestApplicationLaunchTasksProvider(
  private val myEnv: ExecutionEnvironment,
  private val facet: AndroidFacet,
  applicationIdProvider: ApplicationIdProvider
) {
  private val runConfiguration: AndroidTestRunConfiguration = myEnv.runProfile as AndroidTestRunConfiguration
  private val isDebug = myEnv.executor.id == DefaultDebugExecutor.EXECUTOR_ID
  private val myApplicationIdProvider: ApplicationIdProvider = applicationIdProvider
  private val myProject: Project = facet.module.project
  private val myExtraInstrumentationOptions: String = runConfiguration.getExtraInstrumentationOptions(facet)
  private val myTestingType: Int = runConfiguration.TESTING_TYPE
  private val myPackageName: String = runConfiguration.PACKAGE_NAME
  private val myClassName: String = runConfiguration.CLASS_NAME
  private val myMethodName: String = runConfiguration.METHOD_NAME
  private val testRegex: String = runConfiguration.TEST_NAME_REGEX
  private val retentionConfiguration: RetentionConfiguration = RetentionConfiguration(
    runConfiguration.RETENTION_ENABLED,
    runConfiguration.RETENTION_MAX_SNAPSHOTS,
    runConfiguration.RETENTION_COMPRESS_SNAPSHOTS
  )

  @Throws(ExecutionException::class)
  fun getTask(device: IDevice): GradleAndroidTestApplicationLaunchTask {
    val gradleConnectedAndroidTestInvoker =
      GradleConnectedAndroidTestInvoker(
        getNumberOfSelectedDevices(),
        myEnv,
        requireNotNull(runConfiguration.configurationModule.module?.let {
          GradleUtil.findGradleModuleData(it)?.data
        })
      )

    val testAppId: String? = try {
      myApplicationIdProvider.testPackageName
    } catch (e: ApkProvisionException) {
      throw ExecutionException("Unable to determine test package name")
    }

    if (testAppId.isNullOrEmpty()) {
      throw ExecutionException("Unable to determine test package name")
    }

    val appLaunchTask = when (myTestingType) {
      TEST_ALL_IN_MODULE -> {
        allInModuleTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(facet)),
          testAppId,
          isDebug,
          device,
          testRegex,
          gradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions
        )
      }

      TEST_ALL_IN_PACKAGE -> {
        allInPackageTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(facet)),
          testAppId,
          isDebug,
          device,
          myPackageName,
          gradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions
        )
      }

      TEST_CLASS -> {
        classTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(facet)),
          testAppId,
          isDebug,
          device,
          myClassName,
          gradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions
        )
      }

      TEST_METHOD -> {
        methodTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(facet)),
          testAppId,
          isDebug,
          device,
          myClassName,
          myMethodName,
          gradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions
        )
      }

      else -> {
        throw RuntimeException("Unknown testing type is selected, testing type is $myTestingType")
      }
    }
    return appLaunchTask
  }

  private fun getNumberOfSelectedDevices(): Int {
    return (myEnv.getCopyableUserData(DeviceFutures.KEY) ?: error("'DeviceFutures.KEY' not found")).devices.size
  }

  companion object {
    private const val TEST_ALL_IN_MODULE = 0
    private const val TEST_ALL_IN_PACKAGE = 1
    private const val TEST_CLASS = 2
    private const val TEST_METHOD = 3
  }
}
