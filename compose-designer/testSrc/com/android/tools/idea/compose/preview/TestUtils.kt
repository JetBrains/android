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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod

/**
 * Relative paths to some useful files in the SimpleComposeApplication (
 * [SIMPLE_COMPOSE_PROJECT_PATH]) test project
 */
internal enum class SimpleComposeAppPaths(val path: String) {
  APP_MAIN_ACTIVITY("app/src/main/java/google/simpleapplication/MainActivity.kt"),
  APP_OTHER_PREVIEWS("app/src/main/java/google/simpleapplication/OtherPreviews.kt"),
  APP_PARAMETRIZED_PREVIEWS("app/src/main/java/google/simpleapplication/ParametrizedPreviews.kt"),
  APP_RENDER_ERROR("app/src/main/java/google/simpleapplication/RenderError.kt"),
  APP_PREVIEWS_ANDROID_TEST("app/src/androidTest/java/google/simpleapplication/AndroidPreviews.kt"),
  APP_PREVIEWS_UNIT_TEST("app/src/test/java/google/simpleapplication/UnitPreviews.kt"),
  APP_SIMPLE_APPLICATION_DIR("app/src/test/java/google/simpleapplication"),
  LIB_PREVIEWS("lib/src/main/java/google/simpleapplicationlib/Previews.kt"),
  LIB_PREVIEWS_ANDROID_TEST(
    "lib/src/androidTest/java/google/simpleapplicationlib/AndroidPreviews.kt"
  ),
  LIB_PREVIEWS_UNIT_TEST("lib/src/test/java/google/simpleapplicationlib/UnitPreviews.kt")
}

/**
 * List of variations of namespaces to be tested by the Compose tests. This is done to support the
 * name migration. We test the old/new preview annotation names with the old/new composable
 * annotation names.
 */
internal val namespaceVariations =
  listOf(
    arrayOf("androidx.compose.ui.tooling.preview", "androidx.compose"),
    arrayOf("androidx.compose.ui.tooling.preview", "androidx.compose.runtime")
  )

internal fun UFile.declaredMethods(): Sequence<UMethod> =
  classes.asSequence().flatMap { it.methods.asSequence() }

internal fun UFile.method(name: String): UMethod? =
  declaredMethods().filter { it.name == name }.singleOrNull()

/** Returns the [HighlightInfo] description adding the relative line number */
internal fun HighlightInfo.descriptionWithLineNumber() =
  ReadAction.compute<String, Throwable> {
    "${StringUtil.offsetToLineNumber(highlighter!!.document.text, startOffset)}: ${description}"
  }

/**
 * Runs the [action] and waits for a build to happen. It returns the number of builds triggered by
 * [action].
 */
internal fun runAndWaitForBuildToComplete(
  projectRule: AndroidGradleProjectRule,
  action: () -> Unit
) =
  runBlocking(AndroidDispatchers.workerThread) {
    val buildComplete = CompletableDeferred<Unit>()
    val disposable =
      Disposer.newDisposable(projectRule.fixture.testRootDisposable, "Build Listener disposable")
    var readyToListen = false
    try {
      setupBuildListener(
        projectRule.project,
        object : BuildListener {
          override fun startedListening() {
            readyToListen = true
          }

          override fun buildFailed() {
            if (!readyToListen) return
            buildComplete.complete(Unit)
          }

          override fun buildSucceeded() {
            if (!readyToListen) return
            buildComplete.complete(Unit)
          }
        },
        disposable
      )

      action()
      buildComplete.await()
    } finally {
      Disposer.dispose(disposable)
    }
  }

/**
 * Simulates the initialization of an editor and returns the corresponding [PreviewRepresentation].
 */
internal fun getRepresentationForFile(
  file: PsiFile,
  project: Project,
  fixture: CodeInsightTestFixture,
  previewProvider: ComposePreviewRepresentationProvider
): PreviewRepresentation {
  ApplicationManager.getApplication().invokeAndWait {
    runWriteAction { fixture.configureFromExistingVirtualFile(file.virtualFile) }
    val textEditor =
      TextEditorProvider.getInstance().createEditor(project, file.virtualFile) as TextEditor
    Disposer.register(fixture.testRootDisposable, textEditor)
  }

  val multiRepresentationPreview =
    MultiRepresentationPreview(
      file,
      fixture.editor,
      listOf(previewProvider),
      AndroidCoroutineScope(fixture.testRootDisposable)
    )
  Disposer.register(fixture.testRootDisposable, multiRepresentationPreview)

  runBlocking { multiRepresentationPreview.onInit() }
  return multiRepresentationPreview.currentRepresentation!!
}
