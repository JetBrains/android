import com.android.tools.idea.compose.preview.navigation.ComposeViewInfo
import com.android.tools.idea.compose.preview.navigation.PxBounds
import com.android.tools.idea.compose.preview.navigation.SourceLocation
import com.android.tools.idea.compose.preview.navigation.area
import com.android.tools.idea.compose.preview.navigation.containsPoint
import com.android.tools.idea.compose.preview.navigation.findDeepestHits
import com.android.tools.idea.compose.preview.navigation.findHitWithDepth
import com.android.tools.idea.compose.preview.navigation.isEmpty
import com.android.tools.idea.compose.preview.navigation.isNotEmpty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class TestSourceLocation(
  override val className: String,
  override val methodName: String = "",
  override val fileName: String = "",
  override val lineNumber: Int = -1,
  override val packageHash: Int = -1
) : SourceLocation

private fun ComposeViewInfo.serializeHits(x: Int, y: Int): String =
  findHitWithDepth(x, y).sortedBy { it.first }.joinToString("\n") {
    "${it.first}: ${it.second.sourceLocation.className}"
  }

class ComposeViewInfoTest {
  @Test
  fun checkBoundHits() {
    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("child1"),
              PxBounds(0, 0, 0, 0),
              children = listOf()
            ),
            ComposeViewInfo(
              TestSourceLocation("child2"),
              PxBounds(100, 100, 500, 300),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("child2.2"),
                    PxBounds(250, 250, 500, 300),
                    children = listOf()
                  )
                )
            ),
            ComposeViewInfo(
              TestSourceLocation("child3"),
              PxBounds(400, 200, 1000, 300),
              children = listOf()
            )
          )
      )

    assertTrue(
      "2000, 2000 should not hit any components",
      root.findHitWithDepth(2000, 2000).isEmpty()
    )
    assertEquals("0: root".trimMargin(), root.serializeHits(0, 0))
    assertEquals(
      """0: root
                   |1: child2""".trimMargin(),
      root.serializeHits(125, 125)
    )
    assertEquals("child2", root.findDeepestHits(125, 125).single().sourceLocation.className)
    assertEquals(
      """0: root
                   |1: child2
                   |2: child2.2""".trimMargin(),
      root.serializeHits(260, 260)
    )
    assertEquals("child2.2", root.findDeepestHits(260, 260).single().sourceLocation.className)
    assertEquals(
      """0: root
                   |1: child2
                   |1: child3
                   |2: child2.2""".trimMargin(),
      root.serializeHits(450, 260)
    )
    assertEquals("child2.2", root.findDeepestHits(450, 260).single().sourceLocation.className)
  }

  @Test
  fun checkBoundsMethods() {
    assertTrue(PxBounds(0, 0, 0, 0).isEmpty())
    assertFalse(PxBounds(0, 0, 0, 0).isNotEmpty())
    assertTrue(PxBounds(0, 0, 0, 1).isEmpty())
    assertFalse(PxBounds(0, 0, 0, 1).isNotEmpty())
    assertTrue(PxBounds(0, 0, 1, 1).isNotEmpty())
    assertFalse(PxBounds(0, 0, 1, 1).isEmpty())

    val testBounds = PxBounds(20, 30, 50, 100)
    assertTrue(testBounds.containsPoint(50, 100))
    assertTrue(testBounds.containsPoint(35, 100))
    assertTrue(testBounds.containsPoint(20, 30))
    assertEquals(2100, testBounds.area())
  }
}
