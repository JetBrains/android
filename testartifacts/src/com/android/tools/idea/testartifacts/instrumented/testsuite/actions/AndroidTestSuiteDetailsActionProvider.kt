/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions


import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Provider to get [AnAction] to be displayed in the details panel of the Android Test Suite results view.
 */
interface AndroidTestSuiteDetailsActionProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<AndroidTestSuiteDetailsActionProvider>(
      "com.android.tools.idea.testartifacts.instrumented.testsuite.AndroidTestSuiteActionProvider")

    fun getDetailsViewHeaderActions(runConfiguration: RunConfiguration?, testResults: AndroidTestResults?): List<AnAction> {
      return runConfiguration?.let {
        EP_NAME.extensionList.filter { it.isApplicable(runConfiguration) }
          .flatMap { it.getDetailsViewHeaderActions(testResults) }
      } ?: emptyList()
    }
  }

  fun isApplicable(runConfiguration: RunConfiguration): Boolean

  fun getDetailsViewHeaderActions(testResults: AndroidTestResults?): List<AnAction>
}