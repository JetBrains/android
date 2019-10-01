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

import com.android.tools.idea.layoutinspector.model
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorModelTest {
  @Test
  fun testUpdatePropertiesOnly() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type") {
          view(VIEW3, 5, 6, 7, 8, "v3Type")
        }
        view(VIEW2, 8, 7, 6, 5, "v2Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val model2 = model {
      view(ROOT, 2, 4, 6, 8, "rootType") {
        view(VIEW1, 8, 6, 4, 2, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, "v2Type")
      }
    }

    val origNodes = model.root!!.flatten().associateBy { it.drawId }

    model.update(model2.root)
    // property change doesn't count as "modified."
    // TODO: confirm this behavior is as desired
    assertFalse(isModified)

    val newNodes = model.root!!.flatten().associateBy { it.drawId }
    for ((id, orig) in origNodes) {
      assertSame(orig, newNodes[id])
    }
    assertEquals(2, newNodes[ROOT]?.x)
    assertEquals(6, newNodes[VIEW3]?.height)
  }

  @Test
  fun testChildCreated() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val model2 = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
      }
    }

    val origNodes = model.root!!.flatten().associateBy { it.drawId }

    model.update(model2.root)
    assertTrue(isModified)

    val newNodes = model.root!!.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys, origNodes.keys.plus(VIEW3))
    assertSameElements(origNodes[VIEW1]?.children!!, newNodes[VIEW3])
  }

  @Test
  fun testNodeDeleted() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val model2 = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type")
      }
    }

    val origNodes = model.root!!.flatten().associateBy { it.drawId }

    model.update(model2.root)
    assertTrue(isModified)

    val newNodes = model.root!!.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys.plus(VIEW3), origNodes.keys)
    assertEquals(true, origNodes[VIEW1]?.children?.isEmpty())
  }

  @Test
  fun testNodeChanged() {
    val model = model {
      view(ROOT, 2, 4, 6, 8, "rootType") {
        view(VIEW1, 8, 6, 4, 2, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, "v2Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val model2 = model {
      view(ROOT, 2, 4, 6, 8, "rootType") {
        view(VIEW4, 8, 6, 4, 2, "v4Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, "v2Type")
      }
    }

    val origNodes = model.root!!.flatten().associateBy { it.drawId }

    model.update(model2.root)
    assertTrue(isModified)

    val newNodes = model.root!!.flatten().associateBy { it.drawId }

    assertSame(origNodes[ROOT], newNodes[ROOT])
    assertSame(origNodes[VIEW2], newNodes[VIEW2])

    assertNotSame(origNodes[VIEW1], newNodes[VIEW4])
    assertSameElements(model.root!!.children.map { it.drawId }, VIEW4, VIEW2)
    assertEquals("v4Type", newNodes[VIEW4]?.qualifiedName)
    assertEquals("v3Type", newNodes[VIEW3]?.qualifiedName)
    assertEquals(8, newNodes[VIEW3]?.y)
  }
}