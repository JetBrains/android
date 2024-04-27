/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.Test
import kotlin.test.assertEquals

class AnimationPreviewTest : InspectorTests() {
  /**
   * [AnimationPreview] uses [MoreExecutors.directExecutor] instead of
   * [AppExecutorUtil.createBoundedApplicationPoolExecutor] in unit tests (see
   * [AnimationPreview.updateAnimationStatesExecutor]).
   *
   * The check before used to pass, but stopped after IDEA merge. So now there is assumption what
   * isDispatchThread is true for [MoreExecutors.directExecutor].
   *
   * This test exist to check if something will change in the future.
   */
  @Test
  fun directExecutorIsNotDispatchThread() {
    ApplicationManager.getApplication().invokeAndWait {
      MoreExecutors.directExecutor().execute {
        // isDispatchThread was false before IDEA 231.4840.387 update
        // isDispatchThread is true now after IDEA 231.4840.387 update
        // See b/278929658 for details.
        assertEquals(true, ApplicationManager.getApplication().isDispatchThread)
      }
    }
  }
}
