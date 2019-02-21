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
      view("rootId", 1, 2, 3, 4, "rootType") {
        view("v1", 4, 3, 2, 1, "v1Type") {
          view("v3", 5, 6, 7, 8, "v3Type")
        }
        view("v2", 8, 7, 6, 5, "v2Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _ -> isModified = true }

    val model2 = model {
      view("rootId", 2, 4, 6, 8, "rootType") {
        view("v1", 8, 6, 4, 2, "v1Type") {
          view("v3", 9, 8, 7, 6, "v3Type")
        }
        view("v2", 6, 7, 8, 9, "v2Type")
      }
    }

    val origNodes = model.root.flatten().associateBy { it.id }

    model.update(model2.root)
    // property change doesn't count as "modified."
    // TODO: confirm this behavior is as desired
    assertFalse(isModified)

    val newNodes = model.root.flatten().associateBy { it.id }
    for ((id, orig) in origNodes) {
      assertSame(orig, newNodes[id])
    }
    assertEquals(2, newNodes["rootId"]?.x)
    assertEquals(6, newNodes["v3"]?.height)
  }

  @Test
  fun testChildCreated() {
    val model = model {
      view("rootId", 1, 2, 3, 4, "rootType") {
        view("v1", 4, 3, 2, 1, "v1Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _ -> isModified = true }

    val model2 = model {
      view("rootId", 1, 2, 3, 4, "rootType") {
        view("v1", 4, 3, 2, 1, "v1Type") {
          view("v3", 9, 8, 7, 6, "v3Type")
        }
      }
    }

    val origNodes = model.root.flatten().associateBy { it.id }

    model.update(model2.root)
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.id }
    assertSameElements(newNodes.keys, origNodes.keys.plus("v3"))
    assertSameElements(origNodes["v1"]?.children?.values!!, newNodes["v3"])
  }

  @Test
  fun testNodeDeleted() {
    val model = model {
      view("rootId", 1, 2, 3, 4, "rootType") {
        view("v1", 4, 3, 2, 1, "v1Type") {
          view("v3", 9, 8, 7, 6, "v3Type")
        }
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _ -> isModified = true }

    val model2 = model {
      view("rootId", 1, 2, 3, 4, "rootType") {
        view("v1", 4, 3, 2, 1, "v1Type")
      }
    }

    val origNodes = model.root.flatten().associateBy { it.id }

    model.update(model2.root)
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.id }
    assertSameElements(newNodes.keys.plus("v3"), origNodes.keys)
    assertEquals(true, origNodes["v1"]?.children?.isEmpty())
  }

  @Test
  fun testNodeChanged() {
    val model = model {
      view("rootId", 2, 4, 6, 8, "rootType") {
        view("v1", 8, 6, 4, 2, "v1Type") {
          view("v3", 9, 8, 7, 6, "v3Type")
        }
        view("v2", 6, 7, 8, 9, "v2Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _ -> isModified = true }

    val model2 = model {
      view("rootId", 2, 4, 6, 8, "rootType") {
        view("v1a", 8, 6, 4, 2, "v1Type") {
          view("v3", 9, 8, 7, 6, "v3Type")
        }
        view("v2", 6, 7, 8, 9, "v2Type")
      }
    }

    val origNodes = model.root.flatten().associateBy { it.id }

    model.update(model2.root)
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.id }

    assertSame(origNodes["rootId"], newNodes["rootId"])
    assertSame(origNodes["v2"], newNodes["v2"])

    assertNotSame(origNodes["v1"], newNodes["v1a"])
    assertSameElements(model.root.children.keys, "v1a", "v2")
    assertEquals("v1Type", newNodes["v1a"]?.type)
    assertEquals("v3Type", newNodes["v3"]?.type)
    assertEquals(8, newNodes["v3"]?.y)
  }
}