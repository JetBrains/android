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
package com.android.tools.idea.res

import com.android.tools.idea.res.ResourceFolderRepository.RunOnceInitializer
import com.android.tools.idea.res.ResourceFolderRepository.RunOnceWithReadLockInitializer
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.util.application
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ResourceFolderRepositoryRunOnceInitializerTest {
  @get:Rule val androidProjectRule = AndroidProjectRule.inMemory()

  @Test
  fun runOnceInitializer_runsOnlyOnce() {
    var invocations = 0
    val init = RunOnceInitializer { invocations++ }

    repeat(20) { init.run() }

    assertThat(invocations).isEqualTo(1)
  }

  @Test
  fun runOnceInitializer_doesNotHaveReadLock() {
    val init = RunOnceInitializer { assertThat(application.isReadAccessAllowed).isFalse() }

    init.run()
  }

  @Test
  fun runOnceWithReadLockInitializer_runsOnlyOnce() {
    var invocations = 0
    val init = RunOnceWithReadLockInitializer { invocations++ }

    repeat(20) { init.run() }

    assertThat(invocations).isEqualTo(1)
  }

  @Test
  fun runOnceWithReadLockInitializer_hasReadLock() {
    val init = RunOnceWithReadLockInitializer {
      assertThat(application.isReadAccessAllowed).isTrue()
    }

    init.run()
  }
}
