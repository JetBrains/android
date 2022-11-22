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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.testFramework.replaceService

object EnvironmentUtils {

  fun overrideEnvironmentVariables(environmentVariablesMap: Map<String, String?>, disposable: Disposable) {
    val systemEnvironment = TestSystemEnvironment()
    ApplicationManager.getApplication().replaceService(Environment::class.java, systemEnvironment, disposable)
    systemEnvironment.variables(*environmentVariablesMap.toList().toTypedArray())
  }
}