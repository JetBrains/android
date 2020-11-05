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

import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.window
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.junit.Assert
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
    assertSingleRoot(model)
  }

  @Test
  fun testChildCreated() {
    val image1 = ImageIO.read(File(TestUtils.getWorkspaceRoot(), "${TEST_DATA_PATH}/image1.png"))
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
        }
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys, origNodes.keys.plus(VIEW3))
    assertSameElements(origNodes[VIEW1]?.children!!, newNodes[VIEW3] ?: Assert.fail())
    assertSingleRoot(model)
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
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type")
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys.plus(VIEW3), origNodes.keys)
    assertEquals(true, origNodes[VIEW1]?.children?.isEmpty())
    assertSingleRoot(model)
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

    assertSame(origNodes[ROOT], model[ROOT])
    assertSame(origNodes[VIEW2], model[VIEW2])

    assertNotSame(origNodes[VIEW1], model[VIEW4])
    assertSameElements(model[ROOT]!!.children.map { it.drawId }, VIEW4, VIEW2)
    assertEquals("v4Type", model[VIEW4]?.qualifiedName)
    assertEquals("v3Type", model[VIEW3]?.qualifiedName)
    assertEquals(8, model[VIEW3]?.y)
    assertSingleRoot(model)
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

    // clear
    model.update(null, listOf<Any>(), 0)
    assertEmpty(model.root.children)
    assertTrue(model.isEmpty)
    assertSingleRoot(model)
  }

  private fun assertSingleRoot(model: InspectorModel) {
    ViewNode.readDrawChildren { drawChildren ->
      assertEquals(
        model.root.flatten()
          .flatMap { it.drawChildren().asSequence().map { drawChild -> drawChild.owner }.plus(it) }
          .map { it.parentSequence.last() }
          .distinct()
          .single(),
        model.root)
    }
  }
}