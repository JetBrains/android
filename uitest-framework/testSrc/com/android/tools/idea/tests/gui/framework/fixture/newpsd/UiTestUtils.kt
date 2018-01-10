// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.adtui.HtmlLabel
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.util.ToolkitProvider
import sun.awt.SunToolkit

private const val WAIT_FOR_IDLE_TIMEOUT_MS: Int = 10_000

fun HtmlLabel.plainText(): String = document.getText(0, document.length)

fun waitForIdle() {
  val start = System.currentTimeMillis()
  while (System.currentTimeMillis() - start < WAIT_FOR_IDLE_TIMEOUT_MS) {
    try {
      (ToolkitProvider.instance().defaultToolkit() as SunToolkit).realSync()
      return
    }
    catch (_: SunToolkit.InfiniteLoop) {
      // The implementation of SunToolkit.realSync() allows up to 20 events to be processed in a batch.
      // We often have more than 20 events primarily caused by invokeLater() invocations.
    }
  }
  throw WaitTimedOutError("Timed out waiting for idle.")
}
