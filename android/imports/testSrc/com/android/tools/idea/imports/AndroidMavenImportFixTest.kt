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
package com.android.tools.idea.imports

import com.android.testutils.waitForCondition
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull as kotlinAssertNotNull
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.jetbrains.android.dom.inspections.AndroidUnresolvableTagInspection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class AndroidMavenImportFixTest {
  val projectRule = AndroidGradleProjectRule()
  @get:Rule val rule = RuleChain.outerRule(projectRule).around(EdtRule())
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }

  private val CodeInsightTestFixture.fileEditor: TextEditor
    get() = TextEditorProvider.getInstance().getTextEditor(this.editor)

  @Before
  fun setup() {
    ApplicationManager.getApplication()
      .replaceService(
        MavenClassRegistryManager::class.java,
        fakeMavenClassRegistryManager,
        fixture.testRootDisposable,
      )
    projectRule.loadProject(TestProjectPaths.ANDROIDX_SIMPLE)
  }

  @Test
  fun testSuggestedImport_unresolvedViewTag() {
    // Unresolved view class: <androidx.recyclerview.widget.RecyclerView .../>.
    val inspection = AndroidUnresolvableTagInspection()
    fixture.enableInspections(inspection)
    assertBuildGradle(project) {
      !it.contains("androidx.recyclerview:recyclerview:")
    } // not already using recyclerview

    fixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "androidx.recyclerview.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
    fixture.moveCaret("Recycler|View")
    val action =
      fixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!

    assertThat(action.isAvailable(project, fixture.editor, fixture.file)).isTrue()
    WriteCommandAction.runWriteCommandAction(project) {
      action.invoke(project, fixture.editor, fixture.file)
    }

    // Wait for the sync (this is redundant, but we can't get a handle on the internal sync
    // state of the first action)
    projectRule.requestSyncAndWait()

    assertBuildGradle(project) {
      it.contains("implementation 'androidx.recyclerview:recyclerview:1.1.0")
    }
  }

  @Test
  fun testSuggestedImport_unresolvedAttrName() {
    // Unresolved fragment class: <fragment
    // android:name="com.google.android.gms.maps.SupportMapFragment" .../>.
    val inspection = AndroidDomInspection()
    fixture.enableInspections(inspection)
    assertBuildGradle(project) {
      !it.contains("com.google.android.gms:play-services-maps:")
    } // not already using SupportMapFragment

    fixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <fragment xmlns:android="http://schemas.android.com/apk/res/android"
          android:name="com.google.${
        "android".highlightedAs(ERROR, "Unresolved package 'android'")
      }.${
        "gms".highlightedAs(ERROR, "Unresolved package 'gms'")
      }.${
        "maps".highlightedAs(ERROR, "Unresolved package 'maps'")
      }.${
        "SupportMapFragment".highlightedAs(ERROR, "Unresolved class 'SupportMapFragment'")
      }" android:layout_width="match_parent" android:layout_height="match_parent" />
      """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
    fixture.moveCaret("gm|s")
    val actionOnPackage =
      fixture.getIntentionAction("Add dependency on com.google.android.gms:play-services-maps")!!
    assertThat(actionOnPackage.isAvailable(project, fixture.editor, fixture.file)).isTrue()

    fixture.moveCaret("SupportMap|Fragment")
    val actionOnClass =
      fixture.getIntentionAction("Add dependency on com.google.android.gms:play-services-maps")!!
    assertThat(actionOnClass.isAvailable(project, fixture.editor, fixture.file)).isTrue()

    WriteCommandAction.runWriteCommandAction(project) {
      actionOnClass.invoke(project, fixture.editor, fixture.file)
    }

    // Wait for the sync (this is redundant, but we can't get a handle on the internal sync
    // state of the first action)
    projectRule.requestSyncAndWait()

    assertBuildGradle(project) {
      it.contains("implementation 'com.google.android.gms:play-services-maps:17.0.1")
    }
  }

  @Test
  fun testSuggestedImport_undo() {
    // Unresolved view class: <androidx.recyclerview.widget.RecyclerView .../>.
    val inspection = AndroidUnresolvableTagInspection()
    fixture.enableInspections(inspection)
    assertBuildGradle(project) {
      !it.contains("androidx.recyclerview:recyclerview:")
    } // not already using recyclerview

    fixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "androidx.recyclerview.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
    fixture.moveCaret("Recycler|View")
    val action =
      fixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!

    assertThat(action.isAvailable(project, fixture.editor, fixture.file)).isTrue()
    WriteCommandAction.runWriteCommandAction(project) {
      action.invoke(project, fixture.editor, fixture.file)
    }

    // Wait for the sync (this is redundant, but we can't get a handle on the internal sync
    // state of the first action)
    projectRule.requestSyncAndWait()
    assertBuildGradle(project) {
      it.contains("implementation 'androidx.recyclerview:recyclerview:")
    }

    // Undo.
    UndoManager.getInstance(project).undo(fixture.fileEditor)
    waitForCondition(1, TimeUnit.SECONDS) {
      checkBuildGradle(project, "app/build.gradle") {
        !it.contains("implementation 'androidx.recyclerview:recyclerview:1.1.0")
      }
    }
  }

  @Test
  fun testSuggestedImport_redo() {
    // Unresolved view class: <androidx.recyclerview.widget.RecyclerView .../>.
    val inspection = AndroidUnresolvableTagInspection()
    fixture.enableInspections(inspection)
    assertBuildGradle(project) {
      !it.contains("androidx.recyclerview:recyclerview:")
    } // not already using recyclerview

    fixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "androidx.recyclerview.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
    fixture.moveCaret("Recycler|View")
    val action =
      fixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!
    val undoManager = UndoManager.getInstance(project)

    assertThat(action.isAvailable(project, fixture.editor, fixture.file)).isTrue()
    WriteCommandAction.runWriteCommandAction(project) {
      action.invoke(project, fixture.editor, fixture.file)
    }

    // Undo.
    waitForCondition(1, TimeUnit.SECONDS) { undoManager.isUndoAvailable(fixture.fileEditor) }
    undoManager.undo(fixture.fileEditor)

    // Wait for the sync (this is redundant, but we can't get a handle on the internal sync
    // state of the first action)
    projectRule.requestSyncAndWait()
    assertBuildGradle(project) {
      !it.contains("implementation 'androidx.recyclerview:recyclerview:")
    }

    // Redo.
    undoManager.redo(fixture.fileEditor)
    waitForCondition(1, TimeUnit.SECONDS) {
      checkBuildGradle(project, "app/build.gradle") {
        it.contains("implementation 'androidx.recyclerview:recyclerview:1.1.0")
      }
    }
  }

  @Test
  fun testSuggestedImport_kotlinFile() {
    assertBuildGradle(project) {
      !it.contains("androidx.palette:palette:") && !it.contains("androidx.palette:palette-ktx:")
    }

    val paletteType = "Palette".highlightedAs(ERROR)
    fixture.loadNewFile(
      "app/src/main/java/Test.kt",
      // language=kotlin
      """
      package com.example

      fun foo(palette: $paletteType) {}
      """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
    fixture.moveCaret("Pale|tte")
    val action =
      fixture.getIntentionAction("Add dependency on androidx.palette:palette-ktx and import")
    kotlinAssertNotNull(action)

    assertThat(action.isAvailable(project, fixture.editor, fixture.file)).isTrue()
    WriteCommandAction.runWriteCommandAction(project) {
      action.invoke(project, fixture.editor, fixture.file)
    }

    // Wait for the sync (this is redundant, but we can't get a handle on the internal sync
    // state of the first action)
    projectRule.requestSyncAndWait()
    assertBuildGradle(project) { it.contains("implementation 'androidx.palette:palette-ktx:1.0.0") }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.palette.graphics.Palette

      fun foo(palette: Palette) {}
      """
        .trimIndent()
    )
  }

  @Test
  fun testSuggestedImport_javaFile() {
    assertBuildGradle(project) {
      !it.contains("androidx.palette:palette:") && !it.contains("androidx.palette:palette-ktx:")
    }

    val paletteType = "Palette".highlightedAs(ERROR)
    fixture.loadNewFile(
      "app/src/main/java/Test.java",
      // language=java
      """
      package com.example;

      class Test {
        public static void foo($paletteType palette) {}
      }
      """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
    fixture.moveCaret("Pale|tte")
    val action = fixture.getIntentionAction("Add dependency on androidx.palette:palette and import")
    kotlinAssertNotNull(action)

    assertThat(action.isAvailable(project, fixture.editor, fixture.file)).isTrue()
    WriteCommandAction.runWriteCommandAction(project) {
      action.invoke(project, fixture.editor, fixture.file)
    }

    // Wait for the sync (this is redundant, but we can't get a handle on the internal sync
    // state of the first action)
    projectRule.requestSyncAndWait()

    assertBuildGradle(project) { it.contains("implementation 'androidx.palette:palette:1.0.0") }

    fixture.checkResult(
      // language=java
      """
      package com.example;

      import androidx.palette.graphics.Palette;

      class Test {
        public static void foo(Palette palette) {}
      }
      """
        .trimIndent()
    )
  }
}
