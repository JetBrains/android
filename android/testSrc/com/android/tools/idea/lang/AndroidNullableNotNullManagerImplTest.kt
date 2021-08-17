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
import com.intellij.codeInspection.nullable.NullableStuffInspection
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test


class AndroidNullableNotNullManagerImplTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @RunsInEdt
  @Test
  fun test() {
    myFixture.enableInspections(NullableStuffInspection::class.java)
    myFixture.addClass(
      //language=JAVA
      "package androidx.annotation; public @interface NonNull {} "
    )

    val file = myFixture.addFileToProject(
      "/src/test/MakeNonNull.java",
      //language=JAVA
      """
      package test;

      import androidx.annotation.NonNull;

      interface MakeNonNull {
        String getSnapshot(<warning descr="Overridden method parameters are not annotated">@NonN<caret>ull</warning> Integer arg);
      }

      class MakeNonNullImpl implements MakeNonNull {
        @Override
        public String getSnapshot(Integer <warning descr="Not annotated parameter overrides @NonNull parameter">arg</warning>) {
          return "1";
        }
      }

      class MakeNonNullImpl2 implements MakeNonNull {
        @Override
        public String getSnapshot(Integer <warning descr="Not annotated parameter overrides @NonNull parameter">arg</warning>) {
          return "1";
        }
      }
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.checkHighlighting()

    myFixture.getAllQuickFixes().find { it.text == "Annotate overridden method parameters as '@NonNull'" }!!
      .invoke(projectRule.project, myFixture.editor, myFixture.file)

    myFixture.checkResult(
      //language=JAVA
      """
      package test;

      import androidx.annotation.NonNull;

      interface MakeNonNull {
        String getSnapshot(@NonN<caret>ull Integer arg);
      }

      class MakeNonNullImpl implements MakeNonNull {
        @Override
        public String getSnapshot(@NonNull Integer arg) {
          return "1";
        }
      }

      class MakeNonNullImpl2 implements MakeNonNull {
        @Override
        public String getSnapshot(@NonNull Integer arg) {
          return "1";
        }
      }
      """.trimIndent()
    )
  }
}
