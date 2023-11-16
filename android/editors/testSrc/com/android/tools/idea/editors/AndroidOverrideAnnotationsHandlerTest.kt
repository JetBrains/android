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
package com.android.tools.idea.editors

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParameter
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase

/**
 * Test[AndroidOverrideAnnotationsHandler]. Each test overrides a method from a class called "MySuper"
 * in a subclass called "MyOverride". In each test we vary, via the setup() function, what is available
 * in the classpath, and then we check that the override is modified as expected.
 */
class AndroidOverrideAnnotationsHandlerTest : AndroidTestCase() {
  fun testNoAnnotations() {
    // Here we just wipe out the @RecentlyNullable annotation: there's no nullness annotation
    // in the classpath
    val expected = """
      package test.pkg;

      public class MyOverride extends MySuper {
          @Override
          public void method(Object parameter) {
              super.method(parameter);
          }
      }
      """

    check({ }, expected)
  }

  fun testHaveAndroidX() {
    val expected = """
      package test.pkg;

      public class MyOverride extends MySuper {
          @Override
          public void method(@androidx.annotation.Nullable Object parameter) {
              super.method(parameter);
          }
      }
      """

    check({ addNullnessAnnotation("androidx.annotation", "Nullable") }, expected)
  }

  fun testHaveSupportAnnotations() {
    // In a project that depends on android.support.annotation instead of androidx.annotation,
    // we map to those annotations instead
    val expected = """
      package test.pkg;

      public class MyOverride extends MySuper {
          @Override
          public void method(@android.support.annotation.Nullable Object parameter) {
              super.method(parameter);
          }
      }
      """

    check({ addNullnessAnnotation("android.support.annotation", "Nullable") }, expected)
  }

  fun testGetAnnotations() {
    val javaFile = myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}")
    val handler = AndroidOverrideAnnotationsHandler()
    assertTrue(handler.getAnnotations(javaFile).contains("androidx.annotation.Nullable"))
    assertTrue(handler.getAnnotations(javaFile).contains("android.support.annotation.NonNull"))
    assertTrue(handler.getAnnotations(javaFile).contains("androidx.annotation.RecentlyNullable"))
    assertTrue(handler.getAnnotations(javaFile).contains("android.annotation.Nullable"))
  }

  // --- Only test infrastructure below ---

  /**
   * Given a setup method, it will create our super class and our overriding class with
   * the recently nullable class copied into the overriding class, and then it runs the
   * cleanup method of the override handler and checks that after running it, the overridden
   * class has the expected source contents.
   */
  private fun check(setup: () -> Unit, @Language("JAVA") after: String) {
    addNullnessAnnotation("androidx.annotation", "RecentlyNullable")
    setup.invoke()

    val sourceFile = addClass("src/test/pkg/MySuper.java", """
      package test.pkg;

      import androidx.annotation.RecentlyNullable;

      public class MySuper {
          public void method(@Nullable Object parameter) {
          }
      }
      """)
    val sourceModifier = getFirstParameter(sourceFile)
    assertNotNull(sourceModifier)

    val targetFile = addClass("src/test/pkg/MyOverride.java", """
      package test.pkg;

      public class MyOverride extends MySuper {
          @Override
          public void method(@androidx.annotation.RecentlyNullable Object parameter) {
              super.method(parameter);
          }
      }
      """)
    val targetModifier = getFirstParameter(targetFile)

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val handler = AndroidOverrideAnnotationsHandler()
      handler.cleanup(sourceModifier, null, targetModifier)
    }
    val contents = targetFile.text
    assertEquals(after.trimIndent(), contents)
  }

  private fun addNullnessAnnotation(pkg: String, name: String) {
    addClass("src/${pkg.replace('.', '/')}/$name.java", """
      package $pkg;

      import static java.lang.annotation.ElementType.FIELD;
      import static java.lang.annotation.ElementType.METHOD;
      import static java.lang.annotation.ElementType.PARAMETER;
      import static java.lang.annotation.ElementType.TYPE_USE;
      import static java.lang.annotation.RetentionPolicy.CLASS;
      import java.lang.annotation.Retention;
      import java.lang.annotation.Target;
      @Retention(CLASS)
      @Target({METHOD, PARAMETER, FIELD})
      public @interface $name {}
      """)
  }

  private fun addClass(path: String, @Language("java") code: String): PsiFile {
    return myFixture.addFileToProject(path, code.trimIndent())
  }

  private fun getFirstParameter(file: PsiFile): PsiParameter {
    var first: PsiParameter? = null
    file.accept(object : JavaRecursiveElementVisitor() {
      override fun visitParameter(parameter: PsiParameter) {
        if (first == null) {
          first = parameter
        }
        super.visitParameter(parameter)
      }
    })
    return first!!
  }
}
