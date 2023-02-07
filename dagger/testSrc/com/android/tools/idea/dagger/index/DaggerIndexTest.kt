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
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileBasedIndexTumbler
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.junit.After
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DaggerIndexTest {

  @get:Rule
  val edtRule = EdtRule()

  companion object {
    @get:ClassRule
    @get:JvmStatic
    val projectRule = AndroidProjectRule.onDisk()

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      // Before the index implementation is complete, it's not registered in the real product. Register here to allow testing.
      //
      // This can only be done once per test run, since the IntelliJ framework has some static logic that effectively trashes the index's
      // name when it's disposed, such that it can't be used again. Thus this has to be done once in a @BeforeClass method rather than in
      // a @Before method. This ugliness can go away when the index is enabled for real.
      FileBasedIndexExtension.EXTENSION_POINT_NAME.point.registerExtension(DaggerIndex(), projectRule.testRootDisposable)

      // The tumbler allows shutting down indexing and then reinitializing it.
      runInEdt {
        with(FileBasedIndexTumbler("test")) {
          turnOff()
          turnOn()
        }
      }
    }
  }

  /**
   * Configures a file in the in-memory editor by calling [com.intellij.testFramework.fixtures.CodeInsightTestFixture.configureByText].
   *
   * Normally this call would be made directly. But since the project is static and lives through all test cases, any files that are created
   * for one test case can survive through to following cases. To help ensure test independence, this utility method stores a reference to
   * any files that are created, so that they can be deleted in the test teardown method.
   */
  fun configureByText(fileType: FileType, text: String) {
    val file = projectRule.fixture.configureByText(fileType, text)
    configuredFiles.add(file.virtualFile)
  }

  private val configuredFiles: MutableList<VirtualFile> = mutableListOf()

  @After
  fun tearDown() {
    runWriteAction { configuredFiles.forEach { it.delete(this) } }
    configuredFiles.clear()
  }

  @Test
  fun emptyIndex() {
    assertThat(DaggerIndex.getValues("", projectRule.project.projectScope())).isEmpty()
  }

  @Test
  fun basicIndexEntry_java() {
    configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Foo {
        @Inject
        public Foo() {}
      }
      """.trimIndent())

    assertThat(DaggerIndex.getValues("com.example.Foo", projectRule.project.projectScope())).containsExactly(
      InjectedConstructorIndexValue("com.example.Foo"),
    )
  }

  @Test
  fun basicIndexEntry_kotlin() {
    // TODO(b/265846405): Implement basic Kotlin test
    // I can't currently get a Kotlin test to correctly load values into the index, even though I've experimentally verified that the index
    // correctly handles Kotlin in the project. I suspect there's some logic pending on the EDT or something that I need to wait for,
    // potentially related to the hacky way the index is enabled in the above @BeforeClass method.
    // Deferring implementation until the index is enabled for real in the product and that hack is removed.
  }

  @Test
  fun indexEntryWithMultipleValues() {
    configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Foo {
        @Inject
        public Foo(Bar bar) {}
      }
      """.trimIndent())

    configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Bar {
        @Inject
        public Bar() {}
      }
      """.trimIndent())

    configureByText(
      JavaFileType.INSTANCE,
      //language=java
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
      """.trimIndent())

    assertThat(DaggerIndex.getValues("Bar", projectRule.project.projectScope())).containsExactly(
      ProvidesMethodIndexValue("com.example.BarModule", "provideBar"),
      InjectedConstructorParameterIndexValue("com.example.Foo", "bar"),
    )
  }
}
