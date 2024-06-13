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
package com.android.tools.idea.res

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.testing.getEnclosing
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.application.options.CodeStyle
import com.intellij.testFramework.RunsInEdt
import kotlin.test.fail
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlineNamedFunctionHandler
import org.jetbrains.kotlin.idea.util.ClassImportFilter.ClassInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NestedResourceClassImportFilterTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk().onEdt()
  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture }

  /** Tests the actual behavior in the editor as specified by b/254492800. */
  @Test
  @RunsInEdt
  fun innerResourceClassNotImported_otherImport() {
    CodeStyle.getSettings(project).kotlinCustomSettings.IMPORT_NESTED_CLASSES = true
    configureStringResources()

    fixture.addFileToProject(
      "src/com/example/sub/MyDataClass.kt",
      // language=kotlin
      """
      package com.example.sub
      data class MyDataClass(resId: Int)
      """
        .trimIndent(),
    )

    val file =
      fixture.addFileToProject(
        "src/com/example/MyFile.kt",
        // language=kotlin
        """
        package com.example

        fun myGreatFunction() {
          val resString = MyDataClass(R.string.foo)
        }
        """
          .trimIndent(),
      )

    fixture.openFileInEditor(file.virtualFile)
    fixture.moveCaret("MyData|Class")
    val action =
      fixture.availableIntentions.singleOrNull { it.text == "Import class 'MyDataClass'" }
        ?: fail("Could not find action. Found: ${fixture.availableIntentions.map {it.text}}")
    action.invoke(project, fixture.editor, fixture.file)

    // We do not want to be sensitive to the way the formatter behaves after the import, so just
    // make some basic assertions.
    with(fixture.editor.document) {
      // Check that the desired import happened.
      assertThat(text).contains("import com.example.sub.MyDataClass")
      // We should NOT have imported com.example.R.string.
      assertThat(text).doesNotContain("import com.example.R.string")
    }
  }

  /** Tests the actual behavior in the editor as specified by b/254502853. */
  @Test
  @RunsInEdt
  fun innerResourceClassNotImported_inlineMethod() {
    CodeStyle.getSettings(project).kotlinCustomSettings.IMPORT_NESTED_CLASSES = true
    configureStringResources()

    val file =
      fixture.addFileToProject(
        "src/com/example/MyFile.kt",
        // language=kotlin
        """
        package com.example

        fun myGreatFunction() {
            val id = myOtherGreatFunction()
        }

        fun myOtherGreatFunction() = R.string.foo
        """
          .trimIndent(),
      )
    fixture.openFileInEditor(file.virtualFile)

    val myOtherGreatFunction = fixture.getEnclosing<KtNamedFunction>("fun myOther")
    KotlinInlineNamedFunctionHandler()
      .inlineKotlinFunction(project, fixture.editor, myOtherGreatFunction)

    // The refactor does some weird things with formatting that we don't want this test to rely on,
    // so just make some basic assertions.
    with(fixture.editor.document) {
      // Check that the refactor actually happened.
      assertThat(text).contains("val id = R.string.foo")
      // We should NOT have imported com.example.R.string
      assertThat(text).doesNotContain("import com.example.R.string")
    }
  }

  /** More of a unit test for the very specific behavior of the filter. */
  @Test
  fun allowImport() {
    val ktFile: KtFile = mock()
    val visibilityThatDoesNotMatter =
      object : Visibility("doesn't matter", false) {
        override fun mustCheckInImports() = false // doesn't matter
      }

    val nestedRClassInfo =
      ClassInfo(
        FqName("com.example.R.fnord"),
        ClassKind.CLASS,
        Modality.FINAL,
        visibilityThatDoesNotMatter,
        isNested = true,
      )

    val filter = NestedResourceClassImportFilter()
    assertThat(filter.allowClassImport(nestedRClassInfo, ktFile)).isFalse()

    val notNested = nestedRClassInfo.copy(isNested = false)
    assertThat(filter.allowClassImport(notNested, ktFile)).isTrue()

    val notClass = nestedRClassInfo.copy(classKind = ClassKind.INTERFACE)
    assertThat(filter.allowClassImport(notClass, ktFile)).isTrue()

    val notR = nestedRClassInfo.copy(fqName = FqName("com.example.S.fnord"))
    assertThat(filter.allowClassImport(notR, ktFile)).isTrue()

    val notLowercase = nestedRClassInfo.copy(fqName = FqName("com.example.S.Fnord"))
    assertThat(filter.allowClassImport(notLowercase, ktFile)).isTrue()
  }

  private fun configureStringResources() {
    addManifest(fixture)
    val contents =
      // language=XML
      """
      <resources>
      <string name="foo">Foo!</string>
      </resources>
      """
        .trimIndent()

    fixture.addFileToProject("res/values/strings.xml", contents)
    projectRule.projectRule.waitForResourceRepositoryUpdates()
  }
}
