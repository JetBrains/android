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
package com.android.tools.idea.run.deployment.liveedit

import com.android.testutils.TestResources
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LiveEditAppTest {
  @Test
  fun testFallbackMinApp(){
    val expectedMinApi = 24
    val liveEditApp = LiveEditApp(emptySet(), expectedMinApi)
    val actualMinApi = liveEditApp.minAPI
    Truth.assertThat(expectedMinApi).isEqualTo(actualMinApi)
  }

  @Test
  fun testMinAPIRetrieval() {
    val minApiDevice = -1
    val actualApkDesugaredMinApi = 24
    val liveEditApp = LiveEditApp(setOf(TestResources.getFile("/WearableTestApk.apk").toPath()), minApiDevice)
    Truth.assertThat(liveEditApp.minAPI).isEqualTo(actualApkDesugaredMinApi)
  }
}