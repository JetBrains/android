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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.UsefulTestCase
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val LAYOUT_SCREEN_SIMPLE = ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "screen_simple")
private val LAYOUT_APPCOMPAT_SCREEN_SIMPLE = ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
private val LAYOUT_MAIN = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")

class ViewNodeTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testFlatten() {
    val model = model {
      view(ROOT) {
        view(VIEW1) {
          view(VIEW3)
        }
        view(VIEW2)
      }
    }

    UsefulTestCase.assertSameElements(model[ROOT]!!.flatten().map { it.drawId }.toList(), ROOT, VIEW1, VIEW3, VIEW2)
    UsefulTestCase.assertSameElements(model[ROOT]!!.children[0].flatten().map { it.drawId }.toList(), VIEW1, VIEW3)
  }

  @Test
  fun testIsSystemNode() {
    val model = model {
      view(ROOT) {
        view(VIEW1, layout = LAYOUT_SCREEN_SIMPLE) {
          view(VIEW2, layout = LAYOUT_APPCOMPAT_SCREEN_SIMPLE) {
            view(VIEW3, layout = LAYOUT_MAIN)
          }
        }
      }
    }
    val system1 = model[ROOT]!!
    val system2 = model[VIEW1]!!
    val system3 = model[VIEW2]!!
    val user1 = model[VIEW3]!!

    assertThat(system1.isSystemNode).isTrue()
    assertThat(system2.isSystemNode).isTrue()
    assertThat(system3.isSystemNode).isTrue()
    assertThat(user1.isSystemNode).isFalse()

    TreeSettings.hideSystemNodes = true
    assertThat(system1.isInComponentTree).isFalse()
    assertThat(system2.isInComponentTree).isFalse()
    assertThat(system3.isInComponentTree).isFalse()
    assertThat(user1.isInComponentTree).isTrue()

    TreeSettings.hideSystemNodes = false
    assertThat(system1.isInComponentTree).isTrue()
    assertThat(system2.isInComponentTree).isTrue()
    assertThat(system3.isInComponentTree).isTrue()
    assertThat(user1.isInComponentTree).isTrue()
  }

  @Test
  fun testClosestUnfilteredNode() {
    val model = model {
      view(ROOT) {
        view(VIEW1, layout = LAYOUT_MAIN) {
          view(VIEW2, layout = LAYOUT_SCREEN_SIMPLE) {
            view(VIEW3, layout = LAYOUT_APPCOMPAT_SCREEN_SIMPLE) {
            }
          }
        }
      }
    }
    val root = model[ROOT]!!
    val view1 = model[VIEW1]!!
    val view2 = model[VIEW2]!!
    val view3 = model[VIEW3]!!
    assertThat(view3.findClosestUnfilteredNode()).isSameAs(view1)
    assertThat(view2.findClosestUnfilteredNode()).isSameAs(view1)
    assertThat(view1.findClosestUnfilteredNode()).isSameAs(view1)
    assertThat(root.findClosestUnfilteredNode()).isNull()
  }
}
