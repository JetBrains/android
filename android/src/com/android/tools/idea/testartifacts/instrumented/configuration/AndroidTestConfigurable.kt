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
package com.android.tools.idea.testartifacts.instrumented.configuration

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel

class AndroidTestConfigurable : BoundSearchableConfigurable("Testing", "testing.instrumented.configuration") {
  override fun createPanel(): DialogPanel {
    val configuration = AndroidTestConfiguration.getInstance()
    return panel {
      row {
        checkBox("Enable test runs across multiple devices in parallel.",
                 configuration::ALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX)
          .comment("This option also enables a new UI optimized for displaying both single- and multi-device test results.")
        browserLink("Learn more", "https://d.android.com/r/studio-ui/test-matrix/help")
      }
    }
  }
}