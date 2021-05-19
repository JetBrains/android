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
import com.android.tools.idea.testing.findModule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.intellij.openapi.project.Project
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

private const val defaultPackageName = "com.example"

// TODO: Add tests that uses other configuration values
class ConfigurationBuilder(private val strings: TestStringTable) {

  fun makeSampleConfiguration(project: Project): LayoutInspectorProto.ResourceConfiguration {
    val packageName = runInEdtAndGet { getAppPackageName(project) }
    return LayoutInspectorProto.ResourceConfiguration.newBuilder().apply {
      appPackageName = strings.add(packageName)
      theme = strings.add(ResourceReference.style(ResourceNamespace.fromPackageName(packageName), "AppTheme"))
    }.build()
  }

  private fun getAppPackageName(project: Project): String {
    val module = project.findModule("app")
    val facet = AndroidFacet.getInstance(module)
    return Manifest.getMainManifest(facet)?.`package`?.value ?: defaultPackageName
  }
}
