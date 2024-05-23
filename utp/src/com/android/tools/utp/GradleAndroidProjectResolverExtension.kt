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
package com.android.tools.utp

import com.google.common.io.Resources
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.ResourceUtil
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

/**
 * Extension of GradleProjectResolver to enhance processing of test results from
 * DeviceProviderInstrumentedTestTask to Android Studio with Unified Test Platform.
 */
class GradleAndroidProjectResolverExtension : AbstractProjectResolverExtension() {

  companion object {
    /**
     * A Gradle project property to enable UTP test results reporting.
     */
    const val ENABLE_UTP_TEST_REPORT_PROPERTY: String = "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
  }

  private val LOG by lazy { Logger.getInstance(GradleAndroidProjectResolverExtension::class.java) }

  override fun enhanceTaskProcessing(
    project: Project?,
    taskNames: MutableList<String>,
    initScriptConsumer: Consumer<String>,
    parameters: MutableMap<String, String>) {
    try {
      val addTestListenerScript = ResourceUtil.getResource(
        GradleAndroidProjectResolverExtension::class.java, "utp", "addGradleAndroidTestListener.gradle")
      // Note: initScriptConsumer doesn't support Kotlin DSL.
      initScriptConsumer.consume(Resources.toString(addTestListenerScript, Charsets.UTF_8))
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }
}