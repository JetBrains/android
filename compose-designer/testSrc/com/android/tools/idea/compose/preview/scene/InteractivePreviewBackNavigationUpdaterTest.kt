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
package com.android.tools.idea.compose.preview.scene

import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private class FakeComposeViewAdapter() {
  @Suppress("unused") // This property is called via reflection
  private val FakeOnBackPressedDispatcherOwner =
    object : Any() {
      val onBackPressedDispatcher = {}
    }
}

class InteractivePreviewBackNavigationUpdaterTest {

  val composable =
    SingleComposePreviewElementInstance(
      "composableMethodName",
      PreviewDisplaySettings(
        name = "a name",
        baseName = "BaseName",
        parameterName = "ParameterName",
        group = null,
        showDecoration = false,
        showBackground = false,
        backgroundColor = null,
        organizationGroup = "organizationGroup",
      ),
      null,
      null,
      PreviewConfiguration.cleanAndGet(),
    )

  @Test
  fun `check backDispatcher is only set in interactive mode`() {
    val previewManager = TestComposePreviewManager().apply { setMode(PreviewMode.Default()) }
    val fakeComposeViewAdapter = FakeComposeViewAdapter()
    InteractivePreviewBackNavigationUpdater.update(
      fakeComposeViewAdapter,
      previewManager,
      composable,
    )
    assertThat(composable.backPressedDispatcher).isNull()

    previewManager.setMode(PreviewMode.Interactive(composable))
    InteractivePreviewBackNavigationUpdater.update(
      fakeComposeViewAdapter,
      previewManager,
      composable,
    )
    assertThat(composable.backPressedDispatcher).isNotNull()
  }
}
