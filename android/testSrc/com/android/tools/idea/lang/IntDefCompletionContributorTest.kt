/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lang

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestBase
import org.junit.Before
import org.junit.Rule

abstract class IntDefCompletionContributorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  protected val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    val classesRoot = JarFileSystem.getInstance().refreshAndFindFileByPath(
      AndroidTestBase.getTestDataPath() + "/libs/IntDefCompletionContributorsTesting_jar/IntDefCompletionContributorsTesting.jar!/")
    PsiTestUtil.addProjectLibrary(myFixture.module, "mylib", listOf(classesRoot), listOf(classesRoot))

    myFixture.addClass(
      //language=JAVA
      """
      package androidx.annotation;

      import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
      import static java.lang.annotation.RetentionPolicy.SOURCE;

      import java.lang.annotation.Retention;
      import java.lang.annotation.Target;

      @Retention(SOURCE)
      @Target({ANNOTATION_TYPE})
      public @interface IntDef {
          int[] value() default {};
          boolean flag() default false;
      }
    """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package test;

      import androidx.annotation.IntDef;

      public class Modes {
        public static final int NAVIGATION_MODE_STANDARD = 0;
        public static final int NAVIGATION_MODE_LIST = 1;
        public static final int NAVIGATION_MODE_TABS = 2;

        @IntDef({NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS})
        public @interface NavigationMode {}
      }

      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/test/NavigationModeKotlin.kt",
      //language=kotlin
      """
      package test

      import androidx.annotation.IntDef

      const val NAVIGATION_MODE_STANDARD = 0
      const val NAVIGATION_MODE_LIST = 1
      const val NAVIGATION_MODE_TABS = 2

      @IntDef(value = [NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS])
      annotation class NavigationModeKotlin
    """.trimIndent()
    )

    myFixture.addClass(
      //language=Java
      """
      package test;

      public @interface PreviewJava {
          @NavigationModeKotlin int value();
       }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/test/Preview.kt",
      """
      package test

      annotation class Preview(
          @NavigationModeKotlin val uiMode: Int = 0
      )
    """.trimIndent()
    )

    myFixture.addClass(
      //language=Java
      """
      package test;

      public class MyClassWithModeParameter {
        public MyClassWithModeParameter(@NavigationModeKotlin int value) {}
       }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/test/KtClassWithModeParameter.kt",
      """
      package test

      class KtClassWithModeParameter(@NavigationModeKotlin val uiMode: Int = 0)
    """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/test/SetMode.kt",
      //language=kotlin
      """
        package test

        fun setMode(@Modes.NavigationMode mode:Int) {}

        fun setModeKotlin(@NavigationModeKotlin mode:Int) {}
      """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyJavaClass {
        public static void setMode(@Modes.NavigationMode int mode){}
        public static void setModeKotlin(@NavigationModeKotlin int mode){}
      }
    """.trimIndent()
    )
  }

  protected fun checkTopCompletionElements() {
    myFixture.completeBasic()
    val lookupElements = myFixture.lookupElementStrings!!.subList(0, 3)
    Truth.assertThat(lookupElements).containsAllIn(listOf("NAVIGATION_MODE_STANDARD", "NAVIGATION_MODE_LIST", "NAVIGATION_MODE_TABS"))
  }
}