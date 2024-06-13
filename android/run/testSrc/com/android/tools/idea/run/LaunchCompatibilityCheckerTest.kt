/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LaunchCompatibilityCheckerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel()

  @Test
  fun create() {
    val checker = LaunchCompatibilityCheckerImpl.create(projectRule.module.androidFacet!!)
      as? LaunchCompatibilityCheckerImpl
    assertThat(checker).isNotNull()
    // Verify that we got a non-default AndroidVersion from the model
    assertThat(checker!!.myMinSdkVersion.apiLevel).isGreaterThan(1)
  }
}