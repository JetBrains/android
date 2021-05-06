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

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * [AppInspectorTabProvider] allows defining multiple inspectors, but it is common for a tab to be
 * associated with only a single inspector, which this class enforces and provides a simplified
 * API for.
 */
abstract class SingleAppInspectorTabProvider : AppInspectorTabProvider {
  abstract val inspectorId: String
  abstract val inspectorLaunchParams: AppInspectorLaunchParams

  final override val launchConfigs
    get() = listOf(AppInspectorLaunchConfig(inspectorId, inspectorLaunchParams))

  final override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messengers: Iterable<AppInspectorMessenger?>,
    parentDisposable: Disposable): AppInspectorTab {
    return createTab(project, ideServices, processDescriptor, messengers.single()!!, parentDisposable)
  }

  protected abstract fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messenger: AppInspectorMessenger,
    parentDisposable: Disposable): AppInspectorTab
}