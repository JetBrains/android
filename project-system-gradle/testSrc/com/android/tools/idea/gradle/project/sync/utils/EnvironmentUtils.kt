/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.utils

import com.android.tools.idea.gradle.project.sync.utils.environment.TestSystemEnvironment
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import org.mockito.Mockito
import org.mockito.kotlin.whenever

object EnvironmentUtils {

  fun overrideEnvironmentVariables(environmentVariablesMap: Map<String, String?>, disposable: Disposable) {
    val systemEnvironment = TestSystemEnvironment()
    ApplicationManager.getApplication().replaceService(Environment::class.java, systemEnvironment, disposable)
    systemEnvironment.variables(*environmentVariablesMap.toList().toTypedArray())

    handleSpecialCasesEnvironmentVariables(environmentVariablesMap, disposable)
  }

  private fun handleSpecialCasesEnvironmentVariables(environmentVariablesMap: Map<String, String?>, disposable: Disposable) {
    if (environmentVariablesMap.containsKey(JAVA_HOME)) {
      val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
      whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(environmentVariablesMap[JAVA_HOME])
      ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, disposable)
    }
    if (environmentVariablesMap.containsKey(JDK_LOCATION_ENV_VARIABLE_NAME)) {
      IdeSdks.getInstance().overrideJdkEnvVariable(environmentVariablesMap[JDK_LOCATION_ENV_VARIABLE_NAME])
      Disposer.register(disposable) {
        IdeSdks.getInstance().overrideJdkEnvVariable(null)
      }
    }
  }
}