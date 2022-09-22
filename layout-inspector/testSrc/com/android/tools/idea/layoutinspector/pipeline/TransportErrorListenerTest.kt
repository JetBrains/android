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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class TransportErrorListenerTest {

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testErrorShowsBanner() {
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    val transportErrorListener = TransportErrorListener(projectRule.project)

    transportErrorListener.onStartTransportDaemonServerFail(mock(), mock())

    assertThat(bannerService.notification?.message).isEqualTo(LayoutInspectorBundle.message("two.versions.of.studio.running"))
    assertThat(bannerService.notification?.actions).isEmpty()

    transportErrorListener.onPreTransportDaemonStart(mock())

    assertThat(bannerService.notification).isNull()
  }
}