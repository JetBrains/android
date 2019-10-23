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
package com.android.tools.idea.common.diagnostics

import org.jetbrains.android.AndroidTestBase

class PerfDebugHelperTest : AndroidTestBase() {

  fun testDefault() {
    val helper = PerfDebugHelper()
    assertFalse(helper.DEBUG)
  }

//  /**
//   * The helper only works if DEBUG == true. For production code DEBUG should never be turned on.
//   * Use this test as a sanity check when making changes to the codebase.
//   */
//  @Ignore
//  fun testAvg() {
//    val helper = PerfDebugHelper()
//    val testName = "hello"
//    val sleepAvg = 100L
//    val epsilon = 20L
//
//    for (i in 0..13) {
//      helper.start(testName)
//      Thread.sleep(sleepAvg)
//      helper.end(testName)
//    }
//
//    assertTrue(Math.abs(helper.avg(testName) - sleepAvg) < epsilon)
//  }
}