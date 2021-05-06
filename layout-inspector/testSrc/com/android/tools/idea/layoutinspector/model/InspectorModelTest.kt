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
package com.android.tools.idea.layoutinspector.model

import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.fail

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class InspectorModelTest {
  @Test
  fun testUpdatePropertiesOnly() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          view(VIEW3, 5, 6, 7, 8, qualifiedName = "v3Type")
        }
        view(VIEW2, 8, 7, 6, 5, qualifiedName = "v2Type")
      }
    }
    val origRoot = model.root.children[0]
    var isModified = false
    var newRootReported: ViewNode? = null
    model.modificationListeners.add { _, newWindow, structuralChange ->
      newRootReported = newWindow?.root
      isModified = structuralChange
    }

    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }


    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    // property change doesn't count as "modified."
    // TODO: confirm this behavior is as desired
    assertFalse(isModified)

    for ((id, orig) in origNodes) {
      assertSame(orig, model[id])
    }
    assertEquals(2, model[ROOT]?.x)
    assertEquals(6, model[VIEW3]?.height)
    assertSame(origRoot, newRootReported)
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testChildCreated() {
    val image1 = ImageIO.read(File(getWorkspaceRoot().toFile(), "${TEST_DATA_PATH}/image1.png"))
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
        }
      }
    }
    var isModified = false
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }
    // Clear the draw view children, since this test isn't concerned with them and update won't work correctly with them in place.
    ViewNode.writeDrawChildren { drawChildren -> origNodes.values.forEach { it.drawChildren().clear() } }

    model.update(newWindow, listOf(ROOT), 0)
    assertTrue(isModified)
    val view1 = model[VIEW1]!!
    assertSame(view1, model.selection)
    assertSame(view1, model.hoveredNode)

    val newNodes = model.root.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys, origNodes.keys.plus(VIEW3))
    assertSameElements(origNodes[VIEW1]?.children!!, newNodes[VIEW3] ?: fail())
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testNodeDeleted() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
      }
    }
    var isModified = false
    model.setSelection(model[VIEW3], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW3]
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type")
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    assertTrue(isModified)
    assertNull(model.selection)
    assertNull(model.hoveredNode)

    val newNodes = model.root.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys.plus(VIEW3), origNodes.keys)
    assertEquals(true, origNodes[VIEW1]?.children?.isEmpty())
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testNodeChanged() {
    val model = model {
      view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }
    }
    var isModified = false
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW4, 8, 6, 4, 2, qualifiedName = "v4Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    assertTrue(isModified)
    assertNull(model.selection)
    assertNull(model.hoveredNode)

    assertSame(origNodes[ROOT], model[ROOT])
    assertSame(origNodes[VIEW2], model[VIEW2])

    assertNotSame(origNodes[VIEW1], model[VIEW4])
    assertSameElements(model[ROOT]!!.children.map { it.drawId }, VIEW4, VIEW2)
    assertEquals("v4Type", model[VIEW4]?.qualifiedName)
    assertEquals("v3Type", model[VIEW3]?.qualifiedName)
    assertEquals(8, model[VIEW3]?.y)
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testWindows() {
    val model = InspectorModel(mock(Project::class.java))
    assertTrue(model.isEmpty)

    // add first window
    val newWindow = window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
      view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type")
    }
    model.update(newWindow, listOf(ROOT), 0)
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    assertFalse(model.isEmpty)
    assertNotNull(model[VIEW1])
    assertEquals(listOf(ROOT), model.root.children.map { it.drawId })

    // add second window
    var window2 = window(VIEW2, VIEW2, 2, 4, 6, 8, rootViewQualifiedName = "root2Type") {
      view(VIEW3, 8, 6, 4, 2, qualifiedName = "v3Type")
    }
    model.update(window2, listOf(ROOT, VIEW2), 0)
    assertFalse(model.isEmpty)
    assertNotNull(model[VIEW1])
    assertNotNull(model[VIEW3])
    assertSame(model[VIEW1], model.selection)
    assertSame(model[VIEW1], model.hoveredNode)
    assertEquals(listOf(ROOT, VIEW2), model.root.children.map { it.drawId })

    // reverse order of windows
    // same content but new instances, so model.update sees a change
    window2 = window(VIEW2, VIEW2, 2, 4, 6, 8, rootViewQualifiedName = "root2Type") {
      view(VIEW3, 8, 6, 4, 2, qualifiedName = "v3Type")
    }
    model.update(window2, listOf(VIEW2, ROOT), 1)
    assertEquals(listOf(VIEW2, ROOT), model.root.children.map { it.drawId })

    // remove a window
    model.update(null, listOf(VIEW2), 0)
    assertEquals(listOf(VIEW2), model.root.children.map { it.drawId })
    assertNull(model[VIEW1])
    assertNotNull(model[VIEW3])
    assertNull(model.selection)
    assertNull(model.hoveredNode)

    // clear
    model.update(null, listOf<Any>(), 0)
    assertEmpty(model.root.children)
    assertTrue(model.isEmpty)
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testDrawTreeOnInitialCreateAndUpdate() {
    val model = model { }
    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }
    // Clear out the drawChildren added by the test fixture so we only get the ones generated by production code
    ViewNode.writeDrawChildren { drawChildren ->
      model.root.flatten().forEach { node -> node.drawChildren().clear() }
    }
    model.update(newWindow, listOf(ROOT), 0)

    // Verify that drawChildren are created corresponding to the tree
    ViewNode.readDrawChildren { drawChildren ->
      model.root.flatten().forEach { node ->
        assertThat(node.drawChildren().map { (it as DrawViewChild).unfilteredOwner }).containsExactlyElementsIn(node.children)
      }
    }

    val view2 = model[VIEW2]
    val newWindow2 =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW4, 10, 11, 12, 13, qualifiedName = "v4Type")
      }

    // Now update the model and verify that the draw tree is still as it was before.
    model.update(newWindow2, listOf(ROOT), 0)

    ViewNode.readDrawChildren { drawChildren ->
      model.root.flatten().forEach { node ->
        assertThat(node.drawChildren().map { (it as DrawViewChild).unfilteredOwner }).containsExactlyElementsIn(node.children.map {
          if (it.drawId == VIEW4) view2 else it
        })
      }
    }
  }


  private fun DrawViewNode.flattenDrawChildren(drawChildren: ViewNode.() -> List<DrawViewNode>): List<DrawViewNode> =
    listOf(this).plus(this.unfilteredOwner.drawChildren().flatMap { it.flattenDrawChildren(drawChildren) })

  private fun assertSingleRoot(model: InspectorModel, treeSettings: TreeSettings) {
    ViewNode.readDrawChildren { drawChildren ->
      val allDrawChildren = model.root.drawChildren().flatMap { it.flattenDrawChildren(drawChildren) }
      // Check that the drawView tree contains exactly the drawChildren of every element of the view tree
      assertThat(allDrawChildren).containsExactlyElementsIn(
        model.root.flatten().flatMap { it.drawChildren().asSequence() }.toList())
      // Check that the unfiltered owners of the drawViews are all in the view tree
      assertThat(model.root.flatten().toList()).containsAllIn(allDrawChildren.map { it.unfilteredOwner })
      // Check that the owners of the drawViews are all in the view tree or null
      assertThat(model.root.flatten().plus(null).toList()).containsAllIn(allDrawChildren.map { it.findFilteredOwner(treeSettings) })
    }
  }
}