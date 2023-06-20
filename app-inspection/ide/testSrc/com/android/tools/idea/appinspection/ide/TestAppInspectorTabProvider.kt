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
package com.android.tools.idea.appinspection.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.api.test.StubTestAppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTabProvider
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import javax.swing.JPanel

/**
 * A dummy provider, of which we plan to create multiple instances of, to test that an app
 * inspection view can own multiple tabs.
 */
open class StubTestAppInspectorTabProvider(
  override val inspectorId: String,
  override val inspectorLaunchParams: AppInspectorLaunchParams =
    FrameworkInspectorLaunchParams(TEST_JAR)
) : SingleAppInspectorTabProvider() {
  override val displayName
    get() = inspectorId

  override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messenger: AppInspectorMessenger,
    parentDisposable: Disposable
  ): AppInspectorTab {
    return object : SingleAppInspectorTab(StubTestAppInspectorMessenger()) {
      override val component = TestAppInspectorTabComponent()
    }
  }
}

/**
 * A test JPanel used in verifying whether the rendered tab is indeed created by the stub provider.
 */
class TestAppInspectorTabComponent : JPanel()
