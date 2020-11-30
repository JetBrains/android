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
package com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.androidSql.referenceAtCaret
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat

class ProguardR8UsageTypeProviderTest : ProguardR8TestCase() {

  fun testUsageType() {
    myFixture.addClass(
      //language=JAVA
      """
        class MyClass {}
      """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class MyCla${caret}ss {}
    """.trimIndent()
    )

    val usageType = ProguardR8UsageTypeProvider().getUsageType(myFixture.referenceAtCaret.element)
    assertThat(usageType.toString()).isEqualTo("Referenced in Shrinker Config files")
  }

  fun testUsageViewTreeTextRepresentation() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyC${caret}lass {
        }
    """.trimIndent()
    )

    val info = myFixture.findUsages(myFixture.elementAtCaret)

    val representation = myFixture.getUsageViewTreeTextRepresentation(info)

    assertThat(representation).contains("Referenced in Shrinker Config files (1)")
  }
}