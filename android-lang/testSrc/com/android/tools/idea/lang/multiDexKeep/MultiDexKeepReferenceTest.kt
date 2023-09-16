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

import com.android.tools.idea.testing.caret
import com.android.tools.tests.AdtTestProjectDescriptors
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiClass
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.android.AndroidTestCase

class MultiDexKeepReferenceTest : AndroidTestCase() {
  override fun setUp() {
    // These tests check if classes listed in multidex keep file are indeed kept.
    // Inputs are .class files, and thus technically neither java nor kotlin.
    // But using kotlin descriptor includes Kotlin stdlib, hence java instead.
    // TODO(b/300170256): Remove this once 2023.3 merges
    myProjectDescriptor = AdtTestProjectDescriptors.java()

    super.setUp()
    PsiTestUtil.addLibrary(myModule, "mylib", "", myFixture.testDataPath + "/maven/myjar/myjar-1.0.jar")

    myFixture.addClass(
      """
        package com.example.myapplication;

        public class MainActivity  {
          public static class Inner {}
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

  fun testResolve_innerClass() {
    myFixture.configureByText(
      MultiDexKeepFileType.INSTANCE,
      """
        com/example/myapplication/MainActivity${'$'}Inner.class${caret}
      """.trimIndent())

    assertThat(myFixture.elementAtCaret).isInstanceOf(PsiClass::class.java)
    val psiClass = myFixture.elementAtCaret as PsiClass
    assertThat(psiClass.qualifiedName).isEqualTo("com.example.myapplication.MainActivity.Inner")
  }

  fun testCodeCompletionOnEmptyFile() {
    myFixture.configureByText(
      MultiDexKeepFileType.INSTANCE,
      "")

    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "com/example/myapplication/MainActivity.class",
      "com/example/myapplication/MainActivity${'$'}Inner.class",
      "com/example/myapplication/OtherClass.class",
      "p1/p2/R.class",
      "com/myjar/MyJarClass.class"
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
      "com/example/myapplication/MainActivity${'$'}Inner.class",
      "com/example/myapplication/OtherClass.class"
    )
  }
}