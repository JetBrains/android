/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.util

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.testing.findModule
import com.intellij.openapi.project.Project
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

private const val defaultPackageName = "com.example"

// TODO: Add tests that uses other configuration values
class ConfigurationParamsBuilder(private val strings: TestStringTable) {

  fun makeSampleContext(project: Project): AppContext {
    val packageName = runInEdtAndGet { getAppPackageName(project) }
    return AppContext(
      theme = strings.add(ResourceReference.style(ResourceNamespace.fromPackageName(packageName), "AppTheme"))!!,
      screenWidth = 1080,
      screenHeight = 1920
    )
  }

  fun makeSampleProcess(project: Project): ProcessDescriptor {
    return object : ProcessDescriptor {
      override val device = MODERN_DEVICE
      override val abiCpuArch = "x86"
      override val name = getAppPackageName(project)
      override val isRunning = true
      override val pid = 123
      override val streamId = 123456L
    }
  }

  private fun getAppPackageName(project: Project): String {
    val module = project.findModule("app")
    val facet = AndroidFacet.getInstance(module)
    return Manifest.getMainManifest(facet)?.`package`?.value ?: defaultPackageName
  }
}
