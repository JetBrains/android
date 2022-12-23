/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.kotlin.android.quickfix

abstract class AndroidQuickFixMultiFileTest : AbstractAndroidQuickFixMultiFileTest() {

  companion object {
    private const val TEST_DIR = "idea-android/testData/android/quickfix"
  }

  @org.junit.Ignore("b/263890870")
  class AutoImports : AndroidQuickFixMultiFileTest() {
    fun testAndroidRImport() = doTest("$TEST_DIR/autoImports/androidRImport")
  }

  class ViewConstructor : AndroidQuickFixMultiFileTest() {
    fun testIndirect() = doTest("$TEST_DIR/viewConstructor/indirect")
    fun testSimple() = doTest("$TEST_DIR/viewConstructor/simple")
  }
}
