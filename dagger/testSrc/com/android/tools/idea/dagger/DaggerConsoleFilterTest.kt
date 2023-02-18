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
package com.android.tools.idea.dagger

import com.android.tools.idea.dagger.DaggerConsoleFilter.Companion.ERROR_PREFIX
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.filters.Filter
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.registerServiceInstance
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class DaggerConsoleFilterTest : DaggerTestCase() {
  private fun navigateToResultInLine(filter: Filter, line: String, resultNum: Int = 0) {
    val result = filter.applyFilter(line, line.length)
    val info = result!!.resultItems[resultNum]!!.getHyperlinkInfo()
    info!!.navigate(project)
  }

  fun testHighlightRange() {
    val filter = DaggerConsoleFilter()
    filter.applyFilter(ERROR_PREFIX, ERROR_PREFIX.length)

    val classText = "a.b"
    var result =
      filter.applyFilter(classText, ERROR_PREFIX.length + classText.length)!!.resultItems.first()!!
    assertThat(result.highlightStartOffset).isEqualTo(ERROR_PREFIX.length)
    assertThat(result.highlightEndOffset).isEqualTo(ERROR_PREFIX.length + 3)

    val methodText = "a.b()"
    result =
      filter.applyFilter(methodText, ERROR_PREFIX.length + classText.length + methodText.length)!!
        .resultItems.first()!!
    assertThat(result.highlightStartOffset).isEqualTo(ERROR_PREFIX.length + classText.length)
    assertThat(result.highlightEndOffset).isEqualTo(ERROR_PREFIX.length + classText.length + 3)
  }

  fun testClassNames() {
    val clazz =
      myFixture.addClass(
        // language=JAVA
        """
      package example;
      
      class MyClass {
        class Inner {}
      }
    """.trimIndent()
      )

    val innerClass = clazz.findInnerClassByName("Inner", false)!!

    myFixture.configureFromExistingVirtualFile(clazz.containingFile.virtualFile)

    val filter = DaggerConsoleFilter()

    navigateToResultInLine(filter, "$ERROR_PREFIX example.MyClass")
    var elementAtCaret = myFixture.elementAtCaret as? PsiClass
    assertThat(elementAtCaret!!.qualifiedName).isEqualTo(clazz.qualifiedName)

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "example.MyClass")
    elementAtCaret = myFixture.elementAtCaret as? PsiClass
    assertThat(elementAtCaret!!.qualifiedName).isEqualTo(clazz.qualifiedName)

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "example.NotExistingClass some text example.MyClass", 1)
    elementAtCaret = myFixture.elementAtCaret as? PsiClass
    assertThat(elementAtCaret!!.qualifiedName).isEqualTo(clazz.qualifiedName)

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "some text example.MyClass.Inner")
    elementAtCaret = myFixture.elementAtCaret as? PsiClass
    assertThat(elementAtCaret!!.qualifiedName).isEqualTo(innerClass.qualifiedName)
  }

  fun testMethod() {
    val method =
      myFixture
        .addClass(
          // language=JAVA
          """
      package example;
      
      class MyClass {
        public void myMethod() {}
        
        class Inner {
          Inner() {}
        }
      }
    """.trimIndent()
        )
        .findMethodsByName("myMethod", true)
        .first()!!

    myFixture.configureFromExistingVirtualFile(method.containingFile.virtualFile)

    val filter = DaggerConsoleFilter()

    navigateToResultInLine(filter, "$ERROR_PREFIX example.MyClass.myMethod()")
    var elementAtCaret = myFixture.elementAtCaret as? PsiMethod
    assertThat(elementAtCaret!!.name).isEqualTo(method.name)

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "example.MyClass.myMethod()")
    elementAtCaret = myFixture.elementAtCaret as? PsiMethod
    assertThat(elementAtCaret!!.name).isEqualTo(method.name)

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(
      filter,
      "example.NotExistingClass some text example.MyClass.myMethod()",
      1
    )
    elementAtCaret = myFixture.elementAtCaret as? PsiMethod
    assertThat(elementAtCaret!!.name).isEqualTo(method.name)

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "example.MyClass.Inner()")
    elementAtCaret = myFixture.elementAtCaret as? PsiMethod
    val innerClassConstructor =
      JavaPsiFacade.getInstance(project)
          .findClass("example.MyClass.Inner", GlobalSearchScope.allScope(project))!!
        .constructors.first()
    assertThat(elementAtCaret).isEqualTo(innerClassConstructor)
  }

  fun testNavigateToKotlin() {
    myFixture.loadNewFile(
      "example/MyClass.kt",
      // language=kotlin
      """
        package example
        
        class MyClass {
          fun myMethod() {}
          class Inner {}
        }
    """.trimIndent()
    )

    val filter = DaggerConsoleFilter()

    navigateToResultInLine(filter, "$ERROR_PREFIX example.MyClass.myMethod()")
    var elementAtCaret: PsiElement = myFixture.elementAtCaret
    assertThat((elementAtCaret as KtNamedFunction).name).isEqualTo("myMethod")

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "example.MyClass()")
    elementAtCaret = myFixture.elementAtCaret
    // Without explicit constructor navigates to class
    assertThat((elementAtCaret as KtClass).getQualifiedName()).isEqualTo("example.MyClass")

    myFixture.moveCaret("examp|le")

    navigateToResultInLine(filter, "example.MyClass.Inner")
    elementAtCaret = myFixture.elementAtCaret
    assertThat((elementAtCaret as KtClass).getQualifiedName()).isEqualTo("example.MyClass.Inner")
  }

  fun testAnalyticsTracker() {
    val trackerService = TestDaggerAnalyticsTracker()
    project.registerServiceInstance(DaggerAnalyticsTracker::class.java, trackerService)

    val filter = DaggerConsoleFilter()

    navigateToResultInLine(filter, "$ERROR_PREFIX example.MyClass.myMethod()")

    assertThat(trackerService.calledMethods).hasSize(1)
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackOpenLinkFromError")
  }
}
