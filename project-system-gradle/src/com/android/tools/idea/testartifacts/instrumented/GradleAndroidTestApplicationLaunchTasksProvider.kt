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
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.tasks.getBaseDebuggerTask
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.methodTest
import com.google.common.collect.Lists
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * LaunchTasksProvider that provides GradleAndroidTestApplicationLaunchTasks for instrumentation tests
 */
class GradleAndroidTestApplicationLaunchTasksProvider(private val myRunConfig: AndroidRunConfigurationBase,
                                                      private val myEnv: ExecutionEnvironment,
                                                      private val facet: AndroidFacet,
                                                      applicationIdProvider: ApplicationIdProvider,
                                                      launchOptions: LaunchOptions,
                                                      testingType: Int,
                                                      packageName: String,
                                                      className: String,
                                                      methodName: String,
                                                      private val testRegex: String,
                                                      private val retentionConfiguration: RetentionConfiguration) : LaunchTasksProvider {
  private val myFacet: AndroidFacet = facet
  private val myApplicationIdProvider: ApplicationIdProvider = applicationIdProvider
  private val myLaunchOptions: LaunchOptions = launchOptions
  private val myProject: Project = facet.module.project
  private val myExtraInstrumentationOptions: String = if (myRunConfig is AndroidTestRunConfiguration) {
    myRunConfig.getExtraInstrumentationOptions(facet)
  } else ""
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker =
    GradleConnectedAndroidTestInvoker(
      getNumberOfSelectedDevices(),
      myEnv,
      requireNotNull(myRunConfig.configurationModule.module?.let {
        GradleUtil.findGradleModuleData(it)?.data
      })
    )
  private val myTestingType: Int = testingType
  private val myPackageName: String = packageName
  private val myClassName: String = className
  private val myMethodName: String = methodName

  private val myLogger: Logger = Logger.getInstance(GradleAndroidTestApplicationLaunchTasksProvider::class.java)

  @Throws(ExecutionException::class)
  override fun getTasks(device: IDevice): List<LaunchTask> {
    val launchTasks: MutableList<LaunchTask> = Lists.newArrayList()

    val testAppId: String? = try {
      myApplicationIdProvider.testPackageName
    }
    catch (e: ApkProvisionException) {
      throw ExecutionException("Unable to determine test package name")
    }

    if (testAppId.isNullOrEmpty()) {
      throw ExecutionException("Unable to determine test package name")
    }

    val appLaunchTask = when (myTestingType) {
      TEST_ALL_IN_MODULE -> {
        allInModuleTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          device,
          testRegex,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions)
      }

      TEST_ALL_IN_PACKAGE -> {
        allInPackageTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          device,
          myPackageName,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions)
      }

      TEST_CLASS -> {
        classTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          device,
          myClassName,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions)
      }

      TEST_METHOD -> {
        methodTest(
          myProject,
          requireNotNull(GradleAndroidModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          device,
          myClassName,
          myMethodName,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration,
          myExtraInstrumentationOptions)
      } else -> {
        throw RuntimeException("Unknown testing type is selected, testing type is $myTestingType")
      }
    }
    launchTasks.add(appLaunchTask)
    return launchTasks
  }

  override fun getConnectDebuggerTask(): ConnectDebuggerTask? {
    if (!myLaunchOptions.isDebug) {
      return null
    }
    val androidDebuggerContext = myRunConfig.androidDebuggerContext

    return getBaseDebuggerTask(androidDebuggerContext, myFacet, myEnv, timeoutSeconds = Int.MAX_VALUE) // No timeout. }
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
