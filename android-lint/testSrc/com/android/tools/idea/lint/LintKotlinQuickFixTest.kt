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
package com.android.tools.idea.lint

import com.android.testutils.TestUtils
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.inspections.AndroidLintInlinedApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintNewApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintParcelCreatorInspection
import com.android.tools.idea.lint.inspections.AndroidLintSdCardPathInspection
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import junit.framework.Assert.*
import org.intellij.lang.annotations.Language
import java.io.IOException

// Migrated tests from org.jetbrains.kotlin.android.quickfix.AndroidLintQuickfixTestGenerated
class LintKotlinQuickFixTest : AbstractAndroidLintTest() {

  private fun check(
    @Language("kotlin") source: String,
    inspection: AndroidLintInspectionBase,
    fixPrefix: String = "",
    expectedDiff: String,
    stubs: List<String> = emptyList(),
  ) {
    val withoutCaret = source.replace("/*caret*/", "<caret>").trimIndent()

    for (i in stubs.indices) {
      createFile("src/stub$i.kt", stubs[i].trimIndent())
    }

    // Ideally, we'd have just
    //    val file = myFixture.configureByText("src/test.kt", trimIndent)
    // here. But it turns out that this fixture uses a temp file system which *doesn't* actually
    // put the files into the relative path shown above; it would be placed in the *root* project
    // folder, next to the default src/ and res/ folders. And because it's not inside src/,
    // Kotlin doesn't properly handle import management, and the below tests would leave quickfixes
    // with fully qualified imports instead of having shortened references and new imports.
    //
    // This wasn't a problem in AndroidLintQuickfixTestGenerated because they use the
    // copyFileToProject method on the test fixture instead, and *that* method *does* respect
    // relative paths. We can't use that here since we want to put the source files in line with
    // each test, so instead we work a little bit harder to replicate what copyFileToProject
    // does, but with the source passed in:

    val file = createFile("src/test.kt", withoutCaret)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.enableInspections(inspection)

    // Running highlighting seems to be important too; it has a side effect of making sure
    // for example the element.analyze() call in getKotlinSurrounder properly is able
    // to analyze method bodies.
    myFixture.doHighlighting()

    val original = file.text

    val actions = myFixture.filterAvailableIntentions(fixPrefix)
    if (actions.isEmpty()) {
      if (expectedDiff.isNotBlank()) {
        fail("Did not find quickfix with prefix $fixPrefix; available fixes are:\n${myFixture.availableIntentions.joinToString("\n") { it.text }}")
      }
      assertThat(expectedDiff.trim()).isEqualTo("")
      return
    }
    val action = actions.first()
    assertNotNull(myFixture.availableIntentions.joinToString("\n") { it.text }, action)

    myFixture.launchAction(action)

    val fixed = file.text
    val diff = TestUtils.getDiff(original, fixed, 1).trimTrailing()
    assertThat(diff.trim()).isEqualTo(expectedDiff.trimIndent())
  }

  // getDiff currently introduces trailing spaces when showing surrounding lines and those
  // lines are blank. This is a workaround until the utility is fixed.
  private fun String.trimTrailing(): String {
    return this.split("\n").joinToString("\n") { it.trimEnd() }
  }

  @Language("kt")
  private val requiresApiAnnotationStub = """
      package android.support.annotation
      annotation class RequiresApi(val api: Int)
      """.trimIndent()

  private fun createFile(relativePath: String, @Language("kt") source: String): PsiFile {
    val virtualFile = myFixture.tempDirFixture.createFile(relativePath)
    WriteAction.runAndWait<IOException> {
      virtualFile.setBinaryContent(source.toByteArray())
      FileDocumentManager.getInstance().reloadFiles(virtualFile)
    }
    return PsiManager.getInstance(project).findFile(virtualFile)!!
  }

  // Test Parcelable quickfixes

  fun testParcelableMissingCreator() {
    check(
      """
        import android.os.Parcel
        import android.os.Parcelable

        class /*caret*/MissingCreator : Parcelable {
            override fun writeToParcel(dest: Parcel?, flags: Int) {
                TODO("not implemented")
            }

            override fun describeContents(): Int {
                TODO("not implemented")
            }
        }
        """,
      AndroidLintParcelCreatorInspection(),
      "Add Parcelable Implementation",
      """
      @@ -4 +4

      - class MissingCreator : Parcelable {
      + class MissingCreator() : Parcelable {
      +     constructor(parcel: Parcel) : this() {
      +     }
      +
            override fun writeToParcel(dest: Parcel?, flags: Int) {
      @@ -12 +15
            }
      +
      +     companion object CREATOR : Parcelable.Creator<MissingCreator> {
      +         override fun createFromParcel(parcel: Parcel): MissingCreator {
      +             return MissingCreator(parcel)
      +         }
      +
      +         override fun newArray(size: Int): Array<MissingCreator?> {
      +             return arrayOfNulls(size)
      +         }
      +     }
        }
      """
    )
  }

  fun testParcelableNoImplementation() {
    check(
      """
      import android.os.Parcelable

      class /*caret*/NoImplementation : Parcelable
        """,
      AndroidLintParcelCreatorInspection(),
      "Add Parcelable Implementation",
      """
      @@ -1 +1
      + import android.os.Parcel
        import android.os.Parcelable
      @@ -3 +4

      - class NoImplementation : Parcelable
      @@ -4 +4
      + class NoImplementation() : Parcelable {
      +     constructor(parcel: Parcel) : this() {
      +     }
      +
      +     override fun writeToParcel(parcel: Parcel, flags: Int) {
      +
      +     }
      +
      +     override fun describeContents(): Int {
      +         return 0
      +     }
      +
      +     companion object CREATOR : Parcelable.Creator<NoImplementation> {
      +         override fun createFromParcel(parcel: Parcel): NoImplementation {
      +             return NoImplementation(parcel)
      +         }
      +
      +         override fun newArray(size: Int): Array<NoImplementation?> {
      +             return arrayOfNulls(size)
      +         }
      +     }
      + }
      """
    )
  }

  // Test Add Requires Api quickfixes

  @Suppress("RemoveEmptyClassBody")
  fun testRequiresApiAnnotation() {
    check(
      source = """
      import android.graphics.drawable.VectorDrawable
      import kotlin.reflect.KClass

      annotation class SomeAnnotationWithClass(val cls: KClass<*>)

      @SomeAnnotationWithClass(/*caret*/VectorDrawable::class)
      class VectorDrawableProvider {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      expectedDiff = "" // fix should not be offered in this case
    )
  }

  fun testRequiresApiCompanion() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          companion object {
              val VECTOR_DRAWABLE = /*caret*/VectorDrawable()
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build
      + import android.support.annotation.RequiresApi

      @@ -5 +7
            companion object {
      +         @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                val VECTOR_DRAWABLE = VectorDrawable()
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  fun testRequiresApiDefaultParameter() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      fun withDefaultParameter(vector: VectorDrawable = /*caret*/VectorDrawable()) {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      expectedDiff = """
            @@ -2 +2
              import android.graphics.drawable.VectorDrawable
            + import android.os.Build
            + import android.support.annotation.RequiresApi

            @@ -3 +5

            + @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
              fun withDefaultParameter(vector: VectorDrawable = VectorDrawable()) {
            """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  @Suppress("RemoveEmptyClassBody")
  fun testRequiresApiExtend() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class MyVectorDrawable : /*caret*/VectorDrawable() {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      expectedDiff = """
        @@ -2 +2
          import android.graphics.drawable.VectorDrawable
        + import android.os.Build
        + import android.support.annotation.RequiresApi

        @@ -3 +5

        + @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
          class MyVectorDrawable : VectorDrawable() {
        """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  fun testRequiresApiFunctionLiteral() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable {
              with(this) {
                  return /*caret*/VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build
      + import android.support.annotation.RequiresApi

      @@ -4 +6
        class VectorDrawableProvider {
      +     @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            fun getVectorDrawable(): VectorDrawable {
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  fun testRequiresApiInlineConstant() {
    check(
      """
      class Test {
          fun foo(): Int {
              return android.R.attr./*caret*/windowTranslucentStatus
          }
      }
      """,
      inspection = AndroidLintInlinedApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -1 +1
      + import android.os.Build
      + import android.support.annotation.RequiresApi
      +
        class Test {
      @@ -2 +5
        class Test {
      +     @RequiresApi(Build.VERSION_CODES.KITKAT)
            fun foo(): Int {
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  fun testRequiresApiMethod() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable {
              return /*caret*/VectorDrawable()
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build
      + import android.support.annotation.RequiresApi

      @@ -4 +6
        class VectorDrawableProvider {
      +     @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            fun getVectorDrawable(): VectorDrawable {
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  @Suppress("PropertyName")
  fun testRequiresApiProperty() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val VECTOR_DRAWABLE = /*caret*/VectorDrawable()
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build
      + import android.support.annotation.RequiresApi

      @@ -4 +6
        class VectorDrawableProvider {
      +     @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            val VECTOR_DRAWABLE = VectorDrawable()
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  fun testRequiresApiTopLevelProperty() {
    check(
      """
      import android.app.Activity

      val top: Int
          get() = Activity()./*caret*/checkSelfPermission(READ_CONTACTS)
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -2 +2
        import android.app.Activity
      + import android.os.Build
      + import android.support.annotation.RequiresApi

      @@ -4 +6
        val top: Int
      +     @RequiresApi(Build.VERSION_CODES.M)
            get() = Activity().checkSelfPermission(READ_CONTACTS)
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  fun testRequiresApiWhen() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val flag = false
          fun getVectorDrawable(): VectorDrawable {
              return when (flag) {
                  true -> /*caret*/VectorDrawable()
                  else -> VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @RequiresApi",
      """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build
      + import android.support.annotation.RequiresApi

      @@ -5 +7
            val flag = false
      +     @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            fun getVectorDrawable(): VectorDrawable {
      """,
      stubs = listOf(requiresApiAnnotationStub)
    )
  }

  // Test Suppress Lint quickfixes

  fun testSuppressLintActivityMethod() {
    check(
      """
      import android.app.Activity
      import android.os.Environment

      class MainActivity : Activity() {
          fun getSdCard(fromEnvironment: Boolean) = if (fromEnvironment) Environment.getExternalStorageDirectory().path else "/*caret*//sdcard"
      }
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      """
      @@ -1 +1
      + import android.annotation.SuppressLint
        import android.app.Activity
      @@ -5 +6
        class MainActivity : Activity() {
      +     @SuppressLint("SdCardPath")
            fun getSdCard(fromEnvironment: Boolean) = if (fromEnvironment) Environment.getExternalStorageDirectory().path else "/sdcard"
      """
    )
  }

  fun testSuppressLintAddToExistingAnnotation() {
    check(
      """
      import android.annotation.SuppressLint
      import android.app.Activity
      import android.os.Environment

      class MainActivity : Activity() {
          @SuppressLint("Something")
          fun getSdCard(fromEnvironment: Boolean) = if (fromEnvironment) Environment.getExternalStorageDirectory().path else "/*caret*//sdcard"
      }
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -6 +6
        class MainActivity : Activity() {
      -     @SuppressLint("Something")
      +     @SuppressLint("Something", "SdCardPath")
            fun getSdCard(fromEnvironment: Boolean) = if (fromEnvironment) Environment.getExternalStorageDirectory().path else "/sdcard"
      """
    )
  }

  fun testSuppressLintConstructorParameter() {
    check(
      """
      class SdCard(val path: String = "/*caret*//sdcard")
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
        @@ -1 +1
        - class SdCard(val path: String = "/sdcard")
        @@ -2 +1
        + import android.annotation.SuppressLint
        +
        + class SdCard(@SuppressLint("SdCardPath") val path: String = "/sdcard")
        """
    )
  }

  fun testSuppressLintDestructuringDeclaration() {
    check(
      """
      fun foo() {
          val (a: String, b: String) = "/*caret*//sdcard"
      }

      operator fun CharSequence.component1(): String = "component1"
      operator fun CharSequence.component2(): String = "component2"
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.SuppressLint
      +
      + @SuppressLint("SdCardPath")
        fun foo() {
      """
    )
  }

  fun testSuppressLintLambdaArgument() {
    check(
      """
      fun foo(l: Any) = l

      fun bar() {
          foo() {
              "/*caret*//sdcard"
          }
      }
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.SuppressLint
      +
        fun foo(l: Any) = l
      @@ -3 +5

      + @SuppressLint("SdCardPath")
        fun bar() {
      """
    )
  }

  @Suppress("RemoveEmptyParenthesesFromLambdaCall")
  fun testSuppressLintLambdaArgumentProperty() {
    check(
      """
      fun foo(l: Any) = l

      val bar = foo() { "/*caret*//sdcard" }
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.SuppressLint
      +
        fun foo(l: Any) = l
      @@ -3 +5

      + @SuppressLint("SdCardPath")
        val bar = foo() { "/sdcard" }
      """
    )
  }

  fun testSuppressLintMethodParameter() {
    check(
      """
      fun foo(path: String = "/*caret*//sdcard") = path
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -1 +1
      - fun foo(path: String = "/sdcard") = path
      @@ -2 +1
      + import android.annotation.SuppressLint
      +
      + fun foo(@SuppressLint("SdCardPath") path: String = "/sdcard") = path
      """
    )
  }

  fun testSuppressLintPropertyWithLambda() {
    check(
      """
      val getPath = { "/*caret*//sdcard" }
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.SuppressLint
      +
      + @SuppressLint("SdCardPath")
        val getPath = { "/sdcard" }
      """
    )
  }

  @Suppress("MayBeConstant")
  fun testSuppressLintSimpleProperty() {
    check(
      """
      val path = "/*caret*//sdcard"
      """,
      inspection = AndroidLintSdCardPathInspection(),
      fixPrefix = "Suppress SdCardPath with an annotation",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.SuppressLint
      +
      + @SuppressLint("SdCardPath")
        val path = "/sdcard"
      """
    )
  }

  // Quickfixes for add target api annotation

  @Suppress("RemoveEmptyClassBody")
  fun testAddTargetApiAnnotation() {
    check(
      """
      import android.graphics.drawable.VectorDrawable
      import kotlin.reflect.KClass

      annotation class SomeAnnotationWithClass(val cls: KClass<*>)

      @SomeAnnotationWithClass(/*caret*/VectorDrawable::class)
      class VectorDrawableProvider {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = "" // No suggestion should be made
    )
  }


  fun testAddTargetApiCompanion() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          companion object {
              val VECTOR_DRAWABLE = /*caret*/VectorDrawable()
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -4 +6
        class VectorDrawableProvider {
      +     @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            companion object {
      """
    )
  }

  fun testAddTargetApiDefaultParameter() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      fun withDefaultParameter(vector: VectorDrawable = /*caret*/VectorDrawable()) {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -3 +5

      + @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        fun withDefaultParameter(vector: VectorDrawable = VectorDrawable()) {
      """
    )
  }

  @Suppress("RemoveEmptyClassBody")
  fun testAddTargetApiExtend() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class MyVectorDrawable : /*caret*/VectorDrawable() {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -3 +5

      + @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        class MyVectorDrawable : VectorDrawable() {
      """
    )
  }

  fun testAddTargetApiFunctionLiteral() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable {
              with(this) {
                  return /*caret*/VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -4 +6
        class VectorDrawableProvider {
      +     @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            fun getVectorDrawable(): VectorDrawable {
      """
    )
  }

  fun testAddTargetApiInlinedConstant() {
    check(
      """
      class Test {
          fun foo(): Int {
              return android.R.attr./*caret*/windowTranslucentStatus
          }
      }
      """,
      inspection = AndroidLintInlinedApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
      + import android.os.Build
      +
        class Test {
      @@ -2 +5
        class Test {
      +     @TargetApi(Build.VERSION_CODES.KITKAT)
            fun foo(): Int {
      """
    )
  }

  fun testAddTargetApiMethod() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable {
              return /*caret*/VectorDrawable()
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -4 +6
        class VectorDrawableProvider {
      +     @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            fun getVectorDrawable(): VectorDrawable {
      """
    )
  }

  @Suppress("PropertyName")
  fun testAddTargetApiProperty() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val VECTOR_DRAWABLE = /*caret*/VectorDrawable()
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -3 +5

      + @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        class VectorDrawableProvider {
      """
    )
  }

  @Suppress("PropertyName")
  fun testAddTargetApiTopLevelProperty() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val VECTOR_DRAWABLE = /*caret*/VectorDrawable()
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -3 +5

      + @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        class VectorDrawableProvider {
      """
    )
  }

  fun testAddTargetApiWhen() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val flag = false
          fun getVectorDrawable(): VectorDrawable {
              return when (flag) {
                  true -> /*caret*/VectorDrawable()
                  else -> VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Add @TargetApi",
      expectedDiff = """
      @@ -1 +1
      + import android.annotation.TargetApi
        import android.graphics.drawable.VectorDrawable
      @@ -2 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -5 +7
            val flag = false
      +     @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            fun getVectorDrawable(): VectorDrawable {
      """
    )
  }

  @Suppress("RemoveEmptyClassBody")
  fun testAddTargetVersionCheckAnnotation() {
    check(
      """
      import android.graphics.drawable.VectorDrawable
      import kotlin.reflect.KClass

      annotation class SomeAnnotationWithClass(val cls: KClass<*>)

      @SomeAnnotationWithClass(/*caret*/VectorDrawable::class)
      class VectorDrawableProvider {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = "" // no fix should be offered
    )
  }

  fun testAddTargetVersionCheckDefaultParameter() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      fun withDefaultParameter(vector: VectorDrawable = /*caret*/VectorDrawable()) {
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = "" // no fix should be offered
    )
  }

  fun testAddTargetVersionCheckDestructuringDeclaration() {
    check(
      """
      import android.app.Activity
      import android.graphics.drawable.VectorDrawable

      data class ValueProvider(var p1: VectorDrawable, val p2: Int)

      val activity = Activity()
      fun foo() {
          val (v1, v2) = ValueProvider(/*caret*/VectorDrawable(), 0)
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -3 +3
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -8 +9
        fun foo() {
      -     val (v1, v2) = ValueProvider(VectorDrawable(), 0)
      +     val (v1, v2) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +         ValueProvider(VectorDrawable(), 0)
      +     } else {
      +         TODO("VERSION.SDK_INT < LOLLIPOP")
      +     }
        }
      """
    )
  }

  fun testAddTargetVersionCheckExpressionBody() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable = /*caret*/VectorDrawable()
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -4 +5
        class VectorDrawableProvider {
      -     fun getVectorDrawable(): VectorDrawable = VectorDrawable()
      +     fun getVectorDrawable(): VectorDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +         VectorDrawable()
      +     } else {
      +         TODO("VERSION.SDK_INT < LOLLIPOP")
      +     }
        }
      """
    )
  }

  fun testAddTargetVersionCheckFunctionLiteral() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable {
              with(this) {
                  return /*caret*/VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -6 +7
                with(this) {
      -             return VectorDrawable()
      +             return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +                 VectorDrawable()
      +             } else {
      +                 TODO("VERSION.SDK_INT < LOLLIPOP")
      +             }
                }
      """
    )
  }

  fun testAddTargetVersionCheckGetterWithExpressionBody() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      val v: VectorDrawable
          get() = /*caret*/VectorDrawable()
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -4 +5
        val v: VectorDrawable
      -     get() = VectorDrawable()
      @@ -5 +5
      +     get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +         VectorDrawable()
      +     } else {
      +         TODO("VERSION.SDK_INT < LOLLIPOP")
      +     }
      """
    )
  }

  fun testAddTargetVersionCheckIf() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val flag = false
          fun getVectorDrawable(): VectorDrawable {
              if (flag)
                  return /*caret*/VectorDrawable()
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -7 +8
                if (flag)
      -             return VectorDrawable()
      +             return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +                 VectorDrawable()
      +             } else {
      +                 TODO("VERSION.SDK_INT < LOLLIPOP")
      +             }
            }
      """
    )
  }

  fun testAddTargetVersionCheckIfWithBlock() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val flag = false
          fun getVectorDrawable(): VectorDrawable {
              if (flag) {
                  return /*caret*/VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -7 +8
                if (flag) {
      -             return VectorDrawable()
      +             return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +                 VectorDrawable()
      +             } else {
      +                 TODO("VERSION.SDK_INT < LOLLIPOP")
      +             }
                }
      """
    )
  }

  fun testAddTargetVersionCheckInlinedConstant() {
    check(
      """
      class Test {
          fun foo(): Int {
              return android.R.attr./*caret*/windowTranslucentStatus
          }
      }
      """,
      inspection = AndroidLintInlinedApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -1 +1
      + import android.os.Build
      +
        class Test {
      @@ -3 +5
            fun foo(): Int {
      -         return android.R.attr.windowTranslucentStatus
      +         return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      +             android.R.attr.windowTranslucentStatus
      +         } else {
      +             TODO("VERSION.SDK_INT < KITKAT")
      +         }
            }
      """
    )
  }

  fun testAddTargetVersionCheckMethod() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          fun getVectorDrawable(): VectorDrawable {
              return /*caret*/VectorDrawable()
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -5 +6
            fun getVectorDrawable(): VectorDrawable {
      -         return VectorDrawable()
      +         return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +             VectorDrawable()
      +         } else {
      +             TODO("VERSION.SDK_INT < LOLLIPOP")
      +         }
            }
      """
    )
  }

  fun testAddTargetVersionCheckWhen() {
    check(
      """
      import android.graphics.drawable.VectorDrawable

      class VectorDrawableProvider {
          val flag = false
          fun getVectorDrawable(): VectorDrawable {
              return when (flag) {
                  true -> /*caret*/VectorDrawable()
                  else -> VectorDrawable()
              }
          }
      }
      """,
      inspection = AndroidLintNewApiInspection(),
      fixPrefix = "Surround with if (VERSION.SDK_INT",
      expectedDiff = """
      @@ -2 +2
        import android.graphics.drawable.VectorDrawable
      + import android.os.Build

      @@ -7 +8
                return when (flag) {
      -             true -> VectorDrawable()
      +             true -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      +                 VectorDrawable()
      +             } else {
      +                 TODO("VERSION.SDK_INT < LOLLIPOP")
      +             }
      +
                    else -> VectorDrawable()
      """
    )
  }
}