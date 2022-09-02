package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private suspend fun <P : ComposePreviewElement> PreviewElementProvider<P>.toDebugString(): String =
  previewElements().map { it.composableMethodFqn }.sorted().joinToString("\n")

private const val COMPOSABLE_ANNOTATION_PACKAGE = "androidx.compose.runtime"
private const val COMPOSABLE_ANNOTATION_FQN = "$COMPOSABLE_ANNOTATION_PACKAGE.Composable"
private const val PREVIEW_TOOLING_PACKAGE = "androidx.compose.ui.tooling.preview"

internal class PinnedComposePreviewElementManagerTest {
  @get:Rule
  val projectRule =
    ComposeProjectRule(
      previewAnnotationPackage = PREVIEW_TOOLING_PACKAGE,
      composableAnnotationPackage = COMPOSABLE_ANNOTATION_PACKAGE
    )
  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture
  private lateinit var file1: PsiFile
  private lateinit var file2: PsiFile

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PIN_PREVIEW.override(true)
    file1 =
      fixture.addFileToProjectAndInvalidate(
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

      """.trimIndent()
      )

    file2 =
      fixture.addFileToProjectAndInvalidate(
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

      """.trimIndent()
      )
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PIN_PREVIEW.clearOverride()
  }

  @Test
  fun `pin unpin preview successfully with notification`() = runBlocking {
    val elementsInFile1 =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val elementsInFile2 =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file2.virtualFile).toList()

    assertEquals(2, elementsInFile1.size)
    assertEquals(2, elementsInFile2.size)

    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val startModificationCount = pinnedPreviewManager.modificationCount
    val pinnedPreviewElementProvider =
      PinnedPreviewElementManager.getPreviewElementProvider(project)
    var modifications = 0
    pinnedPreviewManager.addListener { modifications++ }
    assertEquals(0, pinnedPreviewElementProvider.previewElements().count())
    assertEquals(startModificationCount, pinnedPreviewManager.modificationCount)
    // Pin Preview1
    pinnedPreviewManager.pin(elementsInFile1[0] as ComposePreviewElementInstance)
    assertEquals(1, pinnedPreviewElementProvider.previewElements().count())
    assertEquals(startModificationCount + 1, pinnedPreviewManager.modificationCount)

    // Pin Preview2
    pinnedPreviewManager.pin(elementsInFile2[0] as ComposePreviewElementInstance)
    assertEquals(startModificationCount + 2, pinnedPreviewManager.modificationCount)
    assertEquals(
      """
        TestKt.Preview1
        com.test.TestKt.Preview3
      """.trimIndent(),
      pinnedPreviewElementProvider.toDebugString()
    )
    assertEquals(2, modifications)

    pinnedPreviewManager.unpin(elementsInFile1[0] as ComposePreviewElementInstance)
    assertEquals(startModificationCount + 3, pinnedPreviewManager.modificationCount)
    assertEquals("com.test.TestKt.Preview3", pinnedPreviewElementProvider.toDebugString())
    assertEquals(3, modifications)

    pinnedPreviewManager.unpin(elementsInFile2[0] as ComposePreviewElementInstance)
    assertEquals(startModificationCount + 4, pinnedPreviewManager.modificationCount)
    assertEquals("", pinnedPreviewElementProvider.toDebugString())
    assertEquals(4, modifications)

    assertFalse(pinnedPreviewManager.unpinAll())
  }

  @Test
  fun `can not pin not existent preview`() = runBlocking {
    val elementsInFile1 =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val elementsInFile2 =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file2.virtualFile).toList()

    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val pinnedPreviewElementProvider =
      PinnedPreviewElementManager.getPreviewElementProvider(project)
    assertTrue(pinnedPreviewManager.pin(elementsInFile1[0] as ComposePreviewElementInstance))
    assertTrue(pinnedPreviewManager.pin(elementsInFile2[0] as ComposePreviewElementInstance))
    assertEquals(
      """
        TestKt.Preview1
        com.test.TestKt.Preview3
      """.trimIndent(),
      pinnedPreviewElementProvider.toDebugString()
    )

    invokeAndWaitIfNeeded { runWriteAction { file1.virtualFile.delete(this) } }
    assertEquals(
      """
        com.test.TestKt.Preview3
      """.trimIndent(),
      pinnedPreviewElementProvider.toDebugString()
    )
  }

  @Test
  fun `can not unpin preview that has not been pinned before`() = runBlocking {
    val elementsInFile =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val pinnedPreviewElementProvider =
      PinnedPreviewElementManager.getPreviewElementProvider(project)
    val startModificationCount = pinnedPreviewManager.modificationCount
    assertFalse(pinnedPreviewManager.unpin(elementsInFile[0] as ComposePreviewElementInstance))
    assertFalse(pinnedPreviewManager.isPinned(elementsInFile[0] as ComposePreviewElementInstance))
    assertFalse(pinnedPreviewManager.isPinned(file1))
    assertEquals(
      "There were no pinned elements, no modifications expected",
      startModificationCount,
      pinnedPreviewManager.modificationCount
    )
    assertEquals(0, pinnedPreviewElementProvider.previewElements().count())
    assertTrue(pinnedPreviewManager.pin(elementsInFile[0] as ComposePreviewElementInstance))
    assertTrue(pinnedPreviewManager.isPinned(elementsInFile[0] as ComposePreviewElementInstance))
    assertTrue(pinnedPreviewManager.isPinned(file1))
    assertEquals(startModificationCount + 1, pinnedPreviewManager.modificationCount)
    assertFalse(pinnedPreviewManager.unpin(elementsInFile[1] as ComposePreviewElementInstance))
    assertEquals(startModificationCount + 1, pinnedPreviewManager.modificationCount)
    assertEquals(1, pinnedPreviewElementProvider.previewElements().count())
  }

  @Test
  fun `multiple pin with notification`() = runBlocking {
    val elementsInFile =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    val pinnedPreviewElementProvider =
      PinnedPreviewElementManager.getPreviewElementProvider(project)
    var modifications = 0
    pinnedPreviewManager.addListener { modifications++ }
    assertFalse(
      pinnedPreviewManager.unpin(
        listOf(
          elementsInFile[0] as ComposePreviewElementInstance,
          elementsInFile[1] as ComposePreviewElementInstance
        )
      )
    )
    assertTrue(
      pinnedPreviewManager.pin(
        listOf(
          elementsInFile[0] as ComposePreviewElementInstance,
          elementsInFile[1] as ComposePreviewElementInstance
        )
      )
    )
    assertEquals("Only one modification expected for multiple pins", 1, modifications)
    assertEquals(2, pinnedPreviewElementProvider.previewElements().count())
    modifications = 0
    assertTrue(
      pinnedPreviewManager.unpin(
        listOf(
          elementsInFile[0] as ComposePreviewElementInstance,
          elementsInFile[1] as ComposePreviewElementInstance
        )
      )
    )
    assertEquals("Only one modification expected for multiple pins", 1, modifications)
    assertEquals(0, pinnedPreviewElementProvider.previewElements().count())
  }

  @Test
  fun `unpinAll removes all elements`() = runBlocking {
    val elementsInFile =
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, file1.virtualFile).toList()
    val pinnedPreviewManager = PinnedPreviewElementManager.getInstance(project)
    var modifications = 0
    pinnedPreviewManager.addListener { modifications++ }
    assertFalse(pinnedPreviewManager.unpinAll())
    assertEquals(
      "unpinAll should not trigger a modification when there are no elements",
      0,
      modifications
    )
    assertTrue(
      pinnedPreviewManager.pin(
        listOf(
          elementsInFile[0] as ComposePreviewElementInstance,
          elementsInFile[1] as ComposePreviewElementInstance
        )
      )
    )
    modifications = 0
    assertTrue(pinnedPreviewManager.isPinned(file1))
    assertTrue(pinnedPreviewManager.isPinned(elementsInFile[0] as ComposePreviewElementInstance))
    assertTrue(pinnedPreviewManager.isPinned(elementsInFile[1] as ComposePreviewElementInstance))
    assertTrue(pinnedPreviewManager.unpinAll())
    assertEquals("Only one modification expected for multiple pins", 1, modifications)
    assertFalse(pinnedPreviewManager.isPinned(file1))
    assertFalse(pinnedPreviewManager.isPinned(elementsInFile[0] as ComposePreviewElementInstance))
    assertFalse(pinnedPreviewManager.isPinned(elementsInFile[1] as ComposePreviewElementInstance))
    assertFalse(pinnedPreviewManager.unpinAll())
  }
}
