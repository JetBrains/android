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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerDataIndexerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  private val fakeIndexValue: IndexValue = mock()

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinNoContent() {
    val fileContent = createFileContent(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      fieldIndexers = listOf(DaggerConceptIndexer { _, indexEntries -> indexEntries["found"] = mutableSetOf(fakeIndexValue) }),
      methodIndexers = listOf(DaggerConceptIndexer { _, indexEntries -> indexEntries["found"] = mutableSetOf(fakeIndexValue) }),
    ))

    assertThat(indexer.map(fileContent)).isEmpty()
  }

  @Test
  fun kotlinPrimaryConstructor() {
    val fileContent = createFileContent(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      class CoffeeMaker() {}
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      methodIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (wrapper.getIsConstructor() && wrapper.getSimpleName() == "CoffeeMaker") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun kotlinSecondaryConstructor() {
    val fileContent = createFileContent(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      class CoffeeMaker() {
        constructor(arg1: Int) {}
      }
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      methodIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (wrapper.getIsConstructor() && wrapper.getSimpleName() == "CoffeeMaker" && wrapper.getParameters().size == 1)
          indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun kotlinClassFunction() {
    val fileContent = createFileContent(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      class CoffeeMaker() {
        fun foo() {}
      }
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      methodIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (!wrapper.getIsConstructor() && wrapper.getSimpleName() == "foo") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun kotlinPackageFunction() {
    val fileContent = createFileContent(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      fun foo() {}
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      methodIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (!wrapper.getIsConstructor() && wrapper.getSimpleName() == "foo") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun kotlinProperty() {
    val fileContent = createFileContent(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      class CoffeeMaker() {
        val foo: Int = 0
      }
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      fieldIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (wrapper.getSimpleName() == "foo") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun javaNoContent() {
    val fileContent = createFileContent(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      fieldIndexers = listOf(DaggerConceptIndexer { _, indexEntries -> indexEntries["found"] = mutableSetOf(fakeIndexValue) }),
      methodIndexers = listOf(DaggerConceptIndexer { _, indexEntries -> indexEntries["found"] = mutableSetOf(fakeIndexValue) }),
    ))

    assertThat(indexer.map(fileContent)).isEmpty()
  }

  @Test
  fun javaConstructor() {
    val fileContent = createFileContent(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      class CoffeeMaker {
        public CoffeeMaker() {}
      }
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      methodIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (wrapper.getIsConstructor() && wrapper.getSimpleName() == "CoffeeMaker") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun javaMethod() {
    val fileContent = createFileContent(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      class CoffeeMaker() {
        public void foo() {}
      }
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      methodIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (!wrapper.getIsConstructor() && wrapper.getSimpleName() == "foo") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  @Test
  fun javaField() {
    val fileContent = createFileContent(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      class CoffeeMaker() {
        public int foo;
      }
      """.trimIndent())

    val indexer = DaggerDataIndexer(DaggerConceptIndexers(
      fieldIndexers = listOf(DaggerConceptIndexer { wrapper, indexEntries ->
        if (wrapper.getSimpleName() == "foo") indexEntries["found"] = mutableSetOf(fakeIndexValue)
      }),
    ))

    assertThat(indexer.map(fileContent)).containsKey("found")
  }

  private fun createFileContent(fileType: FileType, text: String): FileContent {
    val psiFile = myFixture.configureByText(fileType, text)

    return mock<FileContent>().apply {
      whenever(this.fileType).thenReturn(fileType)
      whenever(this.psiFile).thenReturn(psiFile)
    }
  }
}
