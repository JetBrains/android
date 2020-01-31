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
package com.android.tools.idea.lang.multiDexKeep

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.*
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class MultiDexKeepReferenceTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
   super.setUp()
    StudioFlags.MULTI_DEX_KEEP_FILE_SUPPORT_ENABLED.override(true)

    myFixture.addClass(
      """
        package com.example.myapplication;

        public class MainActivity  {

        }

      """.trimIndent()
    )
    myFixture.addClass(
      """
        package com.example.myapplication;

        public class OtherClass  {

        }

      """.trimIndent()
    )
  }

  override fun tearDown() {
    try {
      StudioFlags.MULTI_DEX_KEEP_FILE_SUPPORT_ENABLED.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  fun testResolve() {
    myFixture.configureByText(
      MultiDexKeepFileType.INSTANCE,
      """
        com/example/myapplication/MainActivity.class${caret}
      """.trimIndent())

    assertThat(myFixture.elementAtCaret).isInstanceOf(PsiClass::class.java)
    val psiClass = myFixture.elementAtCaret as PsiClass
    assertThat(psiClass.qualifiedName).isEqualTo("com.example.myapplication.MainActivity")
  }

  fun testCodeCompletionOnEmptyFile() {
    myFixture.configureByText(
      MultiDexKeepFileType.INSTANCE,
      "")

    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "com/example/myapplication/MainActivity.class",
      "com/example/myapplication/OtherClass.class"
    )
  }

  fun testCodeCompletion() {
    myFixture.configureByText(
      MultiDexKeepFileType.INSTANCE,
      """
        com/example/myapplication${caret}
      """.trimIndent())

    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "com/example/myapplication/MainActivity.class",
      "com/example/myapplication/OtherClass.class"
    )
  }
}