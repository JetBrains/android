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
package com.android.tools.idea.lang.proguardR8

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProguardR8FileTypeTest : BasePlatformTestCase() {
  fun test() {
    var file = myFixture.configureByText("proguard.cfg", "")
    assertThat(file.fileType).isEqualTo(ProguardR8FileType.INSTANCE)

    file = myFixture.configureByText("proguard-file.txt", "")
    assertThat(file.fileType).isEqualTo(ProguardR8FileType.INSTANCE)

    file = myFixture.configureByText("proguard.pro", "")
    assertThat(file.fileType).isEqualTo(ProguardR8FileType.INSTANCE)
  }
}