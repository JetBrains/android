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
package com.android.tools.idea.testartifacts.testsuite.temp

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * This extension point is for internal use only.
 * It will be retired once the Gradle test results console view can be customised, see
 * https://youtrack.jetbrains.com/issue/IDEA-368796
 */
@ApiStatus.Internal
interface TestSuiteViewAdaptorProvider {
  companion object {
    @JvmStatic
    val ADAPTOR_PROVIDER_EP_NAME: ExtensionPointName<TestSuiteViewAdaptorProvider> = ExtensionPointName.create(
      "com.android.tools.idea.testartifacts.testsuite.temp.testSuiteViewAdaptorProvider")

    @JvmStatic
    fun firstNonNullAdaptor(runConfiguration: RunConfiguration?): TestSuiteViewAdaptor? {
      val adaptors = ADAPTOR_PROVIDER_EP_NAME.extensionList.mapNotNull {
        it.getAdaptor(runConfiguration)
      }
      if (adaptors.size > 1) {
        Logger.getInstance(TestSuiteViewAdaptorProvider::class.java).warn(
          "Multiple 'TestSuiteViewAdaptorProvider's found for $runConfiguration: $adaptors")
      }

      return adaptors.firstOrNull()
    }
  }

  /**
   * Provides an [TestSuiteViewAdaptor] for the given [runConfiguration].
   *
   * @param runConfiguration The run configuration for which an adaptor is requested.
   * @return An instance of [TestSuiteViewAdaptor] if this provider handles
   *         the given configuration, or `null` otherwise.
   */
  fun getAdaptor(runConfiguration: RunConfiguration?): TestSuiteViewAdaptor?
}