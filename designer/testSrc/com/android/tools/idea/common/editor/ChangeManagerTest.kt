package com.android.tools.idea.common.editor

import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.editors.setupOnSaveListener
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.intellij.lang.annotations.Language
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase
import org.junit.Assert.assertEquals

/** Extension to run operations on the [Document] associated to the given [PsiFile] */
private fun PsiFile.runOnDocument(runnable: (PsiDocumentManager, Document) -> Unit) {
  val documentManager = PsiDocumentManager.getInstance(project)
  val document = documentManager.getDocument(this)!!

  WriteCommandAction.runWriteCommandAction(project) { runnable(documentManager, document) }
}
/** Extension to replace the first occurrence of the [find] string to [replace] */
private fun PsiFile.replaceStringOnce(find: String, replace: String) =
  runOnDocument { documentManager, document ->
    documentManager.commitDocument(document)

    val index = text.indexOf(find)
    assert(index != -1) { "\"$find\" not found in the given file" }

    document.replaceString(index, index + find.length, replace)
    documentManager.commitDocument(document)
  }
/** Helper class do test change tracking and asserting on specific types of changes. */
private class ChangeTracker {
  private var refreshCounter = 0

  private fun reset() {
    refreshCounter = 0
  }

  /** Called when a non-code change happens an a refresh would be required. */
  fun onRefresh(lastUpdatedNanos: Long) {
    refreshCounter++
  }

  fun onRefresh() {
    refreshCounter++
  }

  private fun assertWithCounters(refresh: Int, runnable: () -> Unit) {
    reset()
    runnable()
    // Dispatch any invokeLater actions that the runnable might have generated
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals(refresh, refreshCounter)
  }

  /** Asserts that the given [runnable] triggers refresh notification. */
  fun assertRefreshed(runnable: () -> Unit) = assertWithCounters(refresh = 1, runnable = runnable)

  /** Asserts that the given [runnable] does not trigger refresh notification. */
  fun assertDoesNotRefresh(runnable: () -> Unit) =
    assertWithCounters(refresh = 0, runnable = runnable)
}

class ChangeManagerTest : LightJavaCodeInsightFixtureAdtTestCase() {
  fun testSingleFileChangeTests() {
    @Language("kotlin")
    val startFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @Composable
      @Preview
      fun Preview1() {
      }

      @Composable
      @Preview(name = "preview2", apiLevel = 12)
      fun Preview2() {
        NoComposablePreview("hello")
      }

      @Composable
      @Preview(name = "preview3", widthDp = 1, heightDp = 2, fontScale = 0.2f)
      fun Preview3() {
          NoComposablePreview("Preview3")
          NoComposablePreview("Preview3 line 2")
      }

      @Composable
      fun NoPreviewComposable() {

      }

      @Preview
      fun NoComposablePreview(label: String) {

      }
    """
        .trimIndent()

    val composeTest = myFixture.addFileToProject("src/Test.kt", startFileContent)

    val tracker = ChangeTracker()
    val testMergeQueue =
      MergingUpdateQueue("Document change queue", 0, true, null, testRootDisposable).apply {
        isPassThrough = true
      }
    setupChangeListener(
      project,
      composeTest,
      tracker::onRefresh,
      testRootDisposable,
      mergeQueue = testMergeQueue
    )

    tracker.assertRefreshed {
      composeTest.replaceStringOnce("name = \"preview2\"", "name = \"preview2B\"")
    }
    tracker.assertRefreshed { composeTest.replaceStringOnce("heightDp = 2", "heightDp = 50") }
    tracker.assertRefreshed { composeTest.replaceStringOnce("@Preview", "//@Preview") }

    tracker.assertRefreshed {
      composeTest.replaceStringOnce(
        "NoComposablePreview(\"hello\")",
        "NoComposablePreview(\"bye\")"
      )
    }
    tracker.assertRefreshed {
      composeTest.replaceStringOnce("NoComposablePreview(\"bye\")", "NoPreviewComposable()")
    }
    tracker.assertRefreshed {
      // This currently triggers a code change although we should be able to ignore it
      composeTest.runOnDocument { _, document ->
        document.insertString(0, "// Just a comment\n")
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }
  }

  fun testOnSaveTriggers() {
    @Language("kotlin")
    val startFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @Composable
      @Preview
      fun Preview1() {
      }
    """
        .trimIndent()

    val composeTest = myFixture.addFileToProject("src/Test.kt", startFileContent)

    val testMergeQueue =
      MergingUpdateQueue("Document change queue", 0, true, null, testRootDisposable).apply {
        isPassThrough = true
      }
    var saveCount = 0
    setupOnSaveListener(
      project,
      composeTest,
      { saveCount++ },
      testRootDisposable,
      mergeQueue = testMergeQueue
    )
    assertEquals(0, saveCount)
    FileDocumentManager.getInstance().saveAllDocuments()
    // No pending changes
    assertEquals(0, saveCount)

    composeTest.runOnDocument { _, document -> document.insertString(0, "// Just a comment\n") }
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(1, saveCount)
  }

  fun testLookupDelaysChange() {
    @Language("kotlin")
    val startFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @Composable
      @Preview
      fun Preview1() {
      }
    """
        .trimIndent()

    val composeTest = myFixture.addFileToProject("src/Test.kt", startFileContent)
    myFixture.configureFromExistingVirtualFile(composeTest.virtualFile)

    val tracker = ChangeTracker()
    val testMergeQueue =
      MergingUpdateQueue("Document change queue", 0, true, null, testRootDisposable).apply {
        isPassThrough = true
      }
    setupChangeListener(
      project,
      composeTest,
      tracker::onRefresh,
      testRootDisposable,
      mergeQueue = testMergeQueue
    )
    val lookupManager = LookupManager.getInstance(project)

    run {
      val lookup =
        lookupManager.showLookup(
          myFixture.editor,
          LookupElementBuilder.create("Test"),
          LookupElementBuilder.create("TestB")
        )!!

      // Perform guarded change ensures that the completion popup is not closed on change.
      lookup.performGuardedChange {
        tracker.assertDoesNotRefresh { composeTest.replaceStringOnce("Preview1", "Preview2") }

        // Pending refresh will be executed on hide
        tracker.assertRefreshed { LookupManager.getInstance(project).hideActiveLookup() }
        tracker.assertRefreshed { composeTest.replaceStringOnce("Preview2", "Preview1") }
      }
    }

    // Verify that opening and closing lookup does not trigger a refresh
    run {
      var lookup: LookupEx? = null
      tracker.assertDoesNotRefresh {
        lookup =
          lookupManager.showLookup(
            myFixture.editor,
            LookupElementBuilder.create("Test"),
            LookupElementBuilder.create("TestB")
          )
      }
      // Perform guarded change ensures that the completion popup is not closed on change.
      tracker.assertDoesNotRefresh { LookupManager.getInstance(project).hideActiveLookup() }
    }
  }
}
