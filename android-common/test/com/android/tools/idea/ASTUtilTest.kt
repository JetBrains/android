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
package com.android.tools.idea

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class ASTUtilTest : LightJavaCodeInsightFixtureTestCase() {
    @Test
    fun testGetImports_java_noImports() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.java", """
      package com.android.test;

      class A {
      }
    """.trimIndent())

      assertThat(psiFile.node.getImportedClassNames().toList()).isEmpty()
    }

    @Test
    fun testGetImports_java_singleImport() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.java", """
      package com.android.test;

      import java.lang.List;

      class A {
      }
    """.trimIndent())

      assertThat(psiFile.node.getImportedClassNames().toList()).containsExactly("java.lang.List")
    }

    @Test
    fun testGetImports_java_multipleImports() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.java", """
      package com.android.test;

      import android.view.View;
      import android.widget.Button;

      class A {
      }
    """.trimIndent())

      assertThat(psiFile.node.getImportedClassNames().toList()).containsExactly("android.view.View", "android.widget.Button")
    }

    @Test
    fun testGetImports_kotlin_noImports() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      class A {
      }
    """.trimIndent())

      assertThat(psiFile.node.getImportedClassNames().toList()).isEmpty()
    }

    @Test
    fun testGetImports_kotlin_singleImport() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      import java.lang.List

      class A {
      }
    """.trimIndent())

      assertThat(psiFile.node.getImportedClassNames().toList()).containsExactly("java.lang.List")
    }

    @Test
    fun testGetImports_kotlin_multipleImports() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      import android.view.View
      import android.widget.Button

      class A {
      }
    """.trimIndent())

      assertThat(psiFile.node.getImportedClassNames().toList()).containsExactly("android.view.View", "android.widget.Button")
    }

    @Test
    fun testHasClassesExtending_kotlin_extendsNothing() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      class A {
      }
    """.trimIndent())

      assertFalse(psiFile.node.hasClassesExtending(setOf()))
    }

    @Test
    fun testHasClassesExtending_kotlin_extendsExplicitly() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      class A : com.a.b.C {
      }
    """.trimIndent())

      assertTrue(psiFile.node.hasClassesExtending(setOf("com.a.b.C")))
    }

    @Test
    fun testHasClassesExtending_kotlin_extendsImplicitly() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      import com.a.b.C

      class A : C {
      }
    """.trimIndent())

      assertTrue(psiFile.node.hasClassesExtending(setOf("com.a.b.C")))
    }

    @Test
    fun testHasClassesExtending_kotlin_extendsImplicitly_differentImport() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      import com.d.e.C

      class A : C {
      }
    """.trimIndent())

      assertFalse(psiFile.node.hasClassesExtending(setOf("com.a.b.C")))
    }

    @Test
    fun testHasClassesExtending_kotlin_extendsImplicitly_constructor() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.kt", """
      package com.android.test

      import com.a.b.C

      class A(int a) : C(a) {
      }
    """.trimIndent())

      assertTrue(psiFile.node.hasClassesExtending(setOf("com.a.b.C")))
    }

    fun testHasClassesExtending_java_extendsImplicitly() {
      val psiFile = myFixture.addFileToProject("src/com/android/test/A.java", """
      package com.android.test;

      import com.a.b.C;

      class A extends C {
        public A(int a):C(a) { }
      }
    """.trimIndent())

      assertTrue(psiFile.node.hasClassesExtending(setOf("com.a.b.C")))
    }
  }