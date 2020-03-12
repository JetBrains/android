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
package com.android.tools.idea.appinspection.inspector.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface AppInspectorTabProvider {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<AppInspectorTabProvider>(
      "com.android.tools.idea.appinspection.inspector.ide.appInspectorTabProvider"
    )
  }

  val inspectorId: String
  val displayName: String
  val inspectorAgentJar: AppInspectorJar
  fun isApplicable(): Boolean = true
  fun createTab(project: Project, messenger: AppInspectorClient.CommandMessenger): AppInspectorTab
}
