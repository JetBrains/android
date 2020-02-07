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

import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.TEST_JAR
import javax.swing.JPanel

/**
 * A dummy provider, of which we plan to create multiple instances of, to test that an app inspection view can own multiple tabs.
 */
class StubTestAppInspectorTabProvider : AppInspectorTabProvider {
  override val inspectorId = INSPECTOR_ID
  override val displayName = "TEST"
  override val inspectorAgentJar = TEST_JAR

  override fun createTab(messenger: AppInspectorClient.CommandMessenger): AppInspectorTab {
    return object : AppInspectorTab {
      override val client: AppInspectorClient
        get() = TODO("not implemented")
      override val component = JPanel()
    }
  }
}