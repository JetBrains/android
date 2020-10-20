package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private fun PreviewElementProvider.toDebugString(): String =
  previewElements.map { it.composableMethodFqn }.sorted().joinToString("\n")

private const val COMPOSABLE_ANNOTATION_PACKAGE =  "androidx.compose.runtime"
private const val COMPOSABLE_ANNOTATION_FQN =  "$COMPOSABLE_ANNOTATION_PACKAGE.Composable"
private const val PREVIEW_TOOLING_PACKAGE = "androidx.compose.ui.tooling.preview"

internal class PinnedPreviewElementManagerTest {
  @get:Rule
  val projectRule = ComposeProjectRule(previewAnnotationPackage = PREVIEW_TOOLING_PACKAGE,
                                       composableAnnotationPackage = COMPOSABLE_ANNOTATION_PACKAGE)
  private val project get() = projectRule.project
  private val fixture get() = projectRule.fixture
  private lateinit var file1: PsiFile
  private lateinit var file2: PsiFile

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PIN_PREVIEW.override(true)
    file1 = fixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        import $PREVIEW_TOOLING_PACKAGE.Devices
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2")
        fun Preview2() {
        }

      """.trimIndent())

    file2 = fixture.addFileToProject(
      "src/com/test/Test.kt",
      // language=kotlin
      """
        package com.test

        import $PREVIEW_TOOLING_PACKAGE.Devices
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview
        fun Preview3() {
        }

        @Composable
        @Preview
        fun Preview4() {
        }

      """.trimIndent())
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PIN_PREVIEW.clearOverride()
  }

  @Test
  fun `pin unpin preview successfully`() {
    val elementsInFile1 = AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val elementsInFile2 = AnnotationFilePreviewElementFinder.findPreviewMethods(project, file2.virtualFile).toList()

    assertEquals(2, elementsInFile1.size)
    assertEquals(2, elementsInFile2.size)

    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val pinnedPreviewElementProvider = PinnedPreviewElementManager.getPreviewElementProvider(project)
    assertEquals(0, pinnedPreviewElementProvider.previewElements.count())
    // Pin Preview1
    pinnedPreviewManager.pin(elementsInFile1[0] as PreviewElementInstance)
    assertEquals(1, pinnedPreviewElementProvider.previewElements.count())

    // Pin Preview2
    pinnedPreviewManager.pin(elementsInFile2[0] as PreviewElementInstance)
    assertEquals(
      """
        TestKt.Preview1
        com.test.TestKt.Preview3
      """.trimIndent(),
      pinnedPreviewElementProvider.toDebugString()
    )

    pinnedPreviewManager.unpin(elementsInFile1[0] as PreviewElementInstance)
    assertEquals(
      "com.test.TestKt.Preview3",
      pinnedPreviewElementProvider.toDebugString()
    )

    pinnedPreviewManager.unpin(elementsInFile2[0] as PreviewElementInstance)
    assertEquals(
      "",
      pinnedPreviewElementProvider.toDebugString()
    )
  }

  @Test
  fun `can not pin not existent preview`() {
    val elementsInFile1 = AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val elementsInFile2 = AnnotationFilePreviewElementFinder.findPreviewMethods(project, file2.virtualFile).toList()

    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val pinnedPreviewElementProvider = PinnedPreviewElementManager.getPreviewElementProvider(project)
    assertTrue(pinnedPreviewManager.pin(elementsInFile1[0] as PreviewElementInstance))
    assertTrue(pinnedPreviewManager.pin(elementsInFile2[0] as PreviewElementInstance))
    assertEquals(
      """
        TestKt.Preview1
        com.test.TestKt.Preview3
      """.trimIndent(),
      pinnedPreviewElementProvider.toDebugString()
    )

    invokeAndWaitIfNeeded {
      runWriteAction {
        file1.virtualFile.delete(this)
      }
    }
    assertEquals(
      """
        com.test.TestKt.Preview3
      """.trimIndent(),
      pinnedPreviewElementProvider.toDebugString()
    )
  }

  @Test
  fun `can not unpin preview that has not been pinned before`() {
    val elementsInFile = AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val pinnedPreviewElementProvider = PinnedPreviewElementManager.getPreviewElementProvider(project)
    assertFalse(pinnedPreviewManager.unpin(elementsInFile[0] as PreviewElementInstance))
    assertEquals(0, pinnedPreviewElementProvider.previewElements.count())
    assertTrue(pinnedPreviewManager.pin(elementsInFile[0] as PreviewElementInstance))
    assertFalse(pinnedPreviewManager.unpin(elementsInFile[1] as PreviewElementInstance))
    assertEquals(1, pinnedPreviewElementProvider.previewElements.count())
  }
}