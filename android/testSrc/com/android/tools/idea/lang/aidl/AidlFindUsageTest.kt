/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl

import com.android.tools.idea.lang.aidl.psi.AidlParcelableDeclaration
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.android.AndroidTestCase

class AidlFindUsageTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(
      "src/Interface.java",
        //language=Java
       """// This is the skeleton of generated file of Interface AIDL file.
        public interface Interface {
          public static abstract class Stub implements Interface {
            public Stub() {
            }
      
            private static class Proxy implements Interface {
              @Override
              public void foo(int a) {
              }
            }
          }
          void foo(int a);
        }
        """.trimIndent())
    myFixture.addFileToProject(
      "src/Main.java",
       //language=Java
       """
        class Main {
          static void main(String []args) {
            Interface i = new Interface.Proxy();
            i.foo(1);
          }
        }
        """.trimIndent())
  }

  fun testFindMethodUsage() {
    myFixture.configureByText(
      "file.aidl",
      """interface Interface {
        void fo<caret>o(int a);
      }""")
    val elementAtCaret = myFixture.elementAtCaret
    val foundElements = myFixture.findUsages(elementAtCaret)
    assertThat(foundElements).hasSize(1)
    val element = foundElements.first().element!!
    assertThat(element).isInstanceOf(PsiReferenceExpression::class.java)
    assertThat(element.containingFile.name).isEqualTo("Main.java")
  }

  fun testFindInterfaceUsage() {
    myFixture.configureByText(
      "file.aidl",
      """interface Inter<caret>face {
        void foo(int a);
      }""")
    val elementAtCaret = myFixture.elementAtCaret
    val foundElements = myFixture.findUsages(elementAtCaret)
    assertThat(foundElements).isNotNull()
    val counts = foundElements.groupingBy { it.element?.containingFile!!.name }.eachCount()
    assertThat(counts["Interface.java"]).isEqualTo(2)
    assertThat(counts["Main.java"]).isEqualTo(2)
  }

  fun testParcelableUsage() {
    val file = myFixture.addFileToProject(
      "src/Rect.java",
      //language=Java
      """
      class Rect extends Parcelable {}
      """.trimIndent())
    myFixture.configureByText("file.aidl",
                              """parcelable Rec<caret>t;""")
    val elementAtCaret = myFixture.elementAtCaret.parent
    assertThat(elementAtCaret).isInstanceOf(AidlParcelableDeclaration::class.java)
    assertThat((elementAtCaret as AidlParcelableDeclaration).generatedPsiElement!!.containingFile).isEqualTo(file)
  }
}
