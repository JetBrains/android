/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.index

import com.android.tools.idea.dagger.concepts.InjectedConstructorIndexValue
import com.android.tools.idea.dagger.concepts.InjectedConstructorParameterIndexValue
import com.android.tools.idea.dagger.concepts.ProvidesMethodIndexValue
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }
  private val myProject: Project by lazy { myFixture.project }

  @Test
  fun emptyIndex() {
    assertThat(DaggerIndex.getValues("", myProject.projectScope())).isEmpty()
  }

  @Test
  fun basicIndexEntry_java() {
    myFixture.addFileToProject(
      "src/com/example/Foo.java",
      // language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Foo {
        @Inject
        public Foo() {}
      }
      """
        .trimIndent(),
    )

    assertThat(DaggerIndex.getValues("com.example.Foo", myProject.projectScope()))
      .containsExactly(InjectedConstructorIndexValue(ClassId.fromString("com/example/Foo")))
  }

  @Test
  fun basicIndexEntry_kotlin() {
    myFixture.addFileToProject(
      "src/com/example/Foo.kt",
      // language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class Foo @Inject constructor()
      """
        .trimIndent(),
    )

    assertThat(DaggerIndex.getValues("com.example.Foo", myProject.projectScope()))
      .containsExactly(InjectedConstructorIndexValue(ClassId.fromString("com/example/Foo")))
  }

  @Test
  fun indexEntryWithMultipleValues() {
    myFixture.addFileToProject(
      "src/com/example/Foo.java",
      // language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Foo {
        @Inject
        public Foo(Bar bar) {}
      }
      """
        .trimIndent(),
    )

    myFixture.addFileToProject(
      "src/com/example/Bar.java",
      // language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Bar {
        @Inject
        public Bar() {}
      }
      """
        .trimIndent(),
    )

    myFixture.addFileToProject(
      "src/com/example/BarModule.java",
      // language=java
      """
      package com.example;
      import dagger.Module;
      import dagger.Provides;

      @Module
      public interface BarModule {
        @Provides Bar provideBar() {
          return new Bar();
        }
      }
      """
        .trimIndent(),
    )

    assertThat(DaggerIndex.getValues("Bar", projectRule.project.projectScope()))
      .containsExactly(
        ProvidesMethodIndexValue(ClassId.fromString("com/example/BarModule"), "provideBar"),
        InjectedConstructorParameterIndexValue(ClassId.fromString("com/example/Foo"), "bar"),
      )
  }
}
