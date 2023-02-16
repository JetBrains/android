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
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileBasedIndexTumbler
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexTest {

  @get:Rule val edtRule = EdtRule()

  companion object {
    @get:ClassRule @get:JvmStatic val projectRule = AndroidProjectRule.onDisk()

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      // Before the index implementation is complete, it's not registered in the real product.
      // Register here to allow testing.
      //
      // This can only be done once per test run, since the IntelliJ framework has some static logic
      // that effectively trashes the index's
      // name when it's disposed, such that it can't be used again. Thus this has to be done once in
      // a @BeforeClass method rather than in
      // a @Before method. This ugliness can go away when the index is enabled for real.
      FileBasedIndexExtension.EXTENSION_POINT_NAME.point.registerExtension(
        DaggerIndex(),
        projectRule.testRootDisposable
      )

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
   * Configures a file in the in-memory editor by calling
   * [com.intellij.testFramework.fixtures.CodeInsightTestFixture.configureByText].
   *
   * Normally this call would be made directly. But since the project is static and lives through
   * all test cases, any files that are created for one test case can survive through to following
   * cases. To help ensure test independence, this utility method stores a reference to any files
   * that are created, so that they can be deleted in the test teardown method.
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
      // language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Foo {
        @Inject
        public Foo() {}
      }
      """
        .trimIndent()
    )

    assertThat(DaggerIndex.getValues("com.example.Foo", projectRule.project.projectScope()))
      .containsExactly(
        InjectedConstructorIndexValue("com.example.Foo"),
      )
  }

  @Test
  fun basicIndexEntry_kotlin() {
    // TODO(b/265846405): Implement basic Kotlin test
    // I can't currently get a Kotlin test to correctly load values into the index, even though I've
    // experimentally verified that the index
    // correctly handles Kotlin in the project. I suspect there's some logic pending on the EDT or
    // something that I need to wait for,
    // potentially related to the hacky way the index is enabled in the above @BeforeClass method.
    // Deferring implementation until the index is enabled for real in the product and that hack is
    // removed.
  }

  @Test
  fun indexEntryWithMultipleValues() {
    configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Foo {
        @Inject
        public Foo(Bar bar) {}
      }
      """
        .trimIndent()
    )

    configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Bar {
        @Inject
        public Bar() {}
      }
      """
        .trimIndent()
    )

    configureByText(
      JavaFileType.INSTANCE,
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
        .trimIndent()
    )

    assertThat(DaggerIndex.getValues("Bar", projectRule.project.projectScope()))
      .containsExactly(
        ProvidesMethodIndexValue("com.example.BarModule", "provideBar"),
        InjectedConstructorParameterIndexValue("com.example.Foo", "bar"),
      )
  }
}

/**
 * Some unit tests don't actually require the index to be running. Keeping them in a separate class
 * for now allows them to run without the weirdness above around enabling the index.
 *
 * TODO(b/265846405): After Dagger index is enabled, move these tests into the main class above.
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexTestNotRequiringIndex {

  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun getIndexKeys_standardType() {
    assertThat(
        DaggerIndex.getIndexKeys(
          "com.example.Foo",
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .containsExactly("com.example.Foo", "Foo", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_typeWithoutPackage() {
    assertThat(DaggerIndex.getIndexKeys("Foo", myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Foo", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_typeWithAliasByAlias() {
    // Files need to be added to the project (not just configured as in other test cases) to ensure
    // the references between files work.
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
      package com.example
      class Foo
      """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("class F|oo")
    val basePsiType = (myFixture.elementAtCaret as KtClass).toPsiType()!!

    assertThat(
        DaggerIndex.getIndexKeys(
          "com.example.Foo",
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .containsExactly("com.example.Foo", "Foo", "")
      .inOrder()

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/FooAlias1.kt",
          // language=kotlin
          """
      package com.example
      typealias FooAlias1 = com.example.Foo

      val fooAlias1: FooAlias1 = FooAlias()
      """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("val fooAl|ias1")
    val alias1PsiType = (myFixture.elementAtCaret as KtProperty).psiType!!

    assertThat(alias1PsiType).isEqualTo(basePsiType)
    assertThat(
        DaggerIndex.getIndexKeys(
          "com.example.Foo",
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .containsExactly("com.example.Foo", "com.example.FooAlias1", "Foo", "FooAlias1", "")
      .inOrder()

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/FooAlias2.kt",
          // language=kotlin
          """
      package com.example
      typealias FooAlias2 = com.example.Foo

      val fooAlias2: FooAlias2 = FooAlias()
      """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("val fooAl|ias2")
    val alias2PsiType = (myFixture.elementAtCaret as KtProperty).psiType!!

    assertThat(alias2PsiType).isEqualTo(basePsiType)

    val indexKeysWithAlias2 =
      DaggerIndex.getIndexKeys(
        "com.example.Foo",
        myFixture.project,
        myFixture.project.projectScope()
      )
    assertThat(indexKeysWithAlias2)
      .containsExactly(
        "com.example.Foo",
        "com.example.FooAlias1",
        "com.example.FooAlias2",
        "Foo",
        "FooAlias1",
        "FooAlias2",
        ""
      )

    assertThat(indexKeysWithAlias2[0]).isEqualTo("com.example.Foo")
    assertThat(indexKeysWithAlias2.subList(1, 3))
      .containsExactly("com.example.FooAlias1", "com.example.FooAlias2")
    assertThat(indexKeysWithAlias2[3]).isEqualTo("Foo")
    assertThat(indexKeysWithAlias2.subList(4, 6)).containsExactly("FooAlias1", "FooAlias2")
    assertThat(indexKeysWithAlias2[6]).isEqualTo("")

    // Same short name as above, but different package
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/other/FooAlias2.kt",
          // language=kotlin
          """
      package com.other
      typealias FooAlias2 = com.example.Foo

      val fooAlias2: FooAlias2 = FooAlias()
      """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("val fooAl|ias2")
    val alias2OtherPsiType = (myFixture.elementAtCaret as KtProperty).psiType!!

    assertThat(alias2OtherPsiType).isEqualTo(basePsiType)

    val indexKeysWithAlias2Other =
      DaggerIndex.getIndexKeys(
        "com.example.Foo",
        myFixture.project,
        myFixture.project.projectScope()
      )
    assertThat(indexKeysWithAlias2Other)
      .containsExactly(
        "com.example.Foo",
        "com.example.FooAlias1",
        "com.example.FooAlias2",
        "com.other.FooAlias2",
        "Foo",
        "FooAlias1",
        "FooAlias2",
        ""
      )

    assertThat(indexKeysWithAlias2Other[0]).isEqualTo("com.example.Foo")
    assertThat(indexKeysWithAlias2Other.subList(1, 4))
      .containsExactly("com.example.FooAlias1", "com.example.FooAlias2", "com.other.FooAlias2")
    assertThat(indexKeysWithAlias2Other[4]).isEqualTo("Foo")
    assertThat(indexKeysWithAlias2Other.subList(5, 7)).containsExactly("FooAlias1", "FooAlias2")
    assertThat(indexKeysWithAlias2Other[7]).isEqualTo("")
  }
}
