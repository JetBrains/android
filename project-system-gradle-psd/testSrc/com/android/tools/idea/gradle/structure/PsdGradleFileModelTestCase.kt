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
package com.android.tools.idea.gradle.structure

import com.android.tools.idea.gradle.GradleFileModelTestCase
import org.jetbrains.android.AndroidTestBase
import org.junit.Before
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Ignore // Needs to be ignored so bazel doesn't try to run this class as a test and fail with "No tests found".
@RunWith(Parameterized::class)
abstract class PsdGradleFileModelTestCase : GradleFileModelTestCase() {
  @Before
  fun setUpTestDataPath() {
    testDataPath = AndroidTestBase.getTestDataPath() + "/psd"
  }
}
