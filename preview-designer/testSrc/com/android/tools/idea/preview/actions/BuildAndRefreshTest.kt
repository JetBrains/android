/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.intellij.openapi.application.runInEdt
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.CompletableFuture
import org.junit.Rule
import org.junit.Test

class BuildAndRefreshTest {

  @get:Rule val applicationRule = ApplicationRule()

  // Regression test for b/353503495
  @Test
  fun `BuildAndRefresh file provider runs with read access in a background thread`() {
    val invoked = CompletableFuture<Unit>()
    val action = BuildAndRefresh {
      ThreadingAssertions.assertReadAccess()
      ThreadingAssertions.assertBackgroundThread()
      invoked.complete(Unit)
      null
    }

    runInEdt { action.actionPerformed(createTestEvent()) }

    invoked.join()
  }
}
