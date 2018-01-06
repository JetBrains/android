/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NavComponentHelperTest {

  @Test
  fun testUiName() {
    val component = mock(NlComponent::class.java)
    `when`(component.id).thenCallRealMethod()
    `when`(component.tagName).thenReturn("myTag")
    assertEquals("myTag", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("com.example.Foo")
    assertEquals("Foo", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("Bar")
    assertEquals("Bar", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)).thenReturn("@+id/myId")
    assertEquals("myId", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL)).thenReturn("myLabel")
    assertEquals("myLabel", component.getUiName(null))
  }

  @Test
  fun testUiNameWithResources() {
    val resolver = mock(ResourceResolver::class.java)
    val value = mock(ResourceValue::class.java)
    `when`(value.value).thenReturn("resolvedValue")
    `when`(resolver.findResValue("myLabel", false)).thenReturn(value)
    `when`(resolver.resolveResValue(value)).thenReturn(value)

    val component = mock(NlComponent::class.java)
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL)).thenReturn("myLabel")
    assertEquals("resolvedValue", component.getUiName(resolver))
  }
}

class NavComponentHelperTest2 : NavTestCase() {

  fun testVisibleDestinations() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          action("a1", destination = "subnav1")
          action("a2", destination = "activity1")
        }
        activity("activity1")
        navigation("subnav1") {
          fragment("f2")
          fragment("f3")
        }
        navigation("subnav2") {
          fragment("f4")
          navigation("subsubnav") {
            fragment("f5")
          }
        }
      }
    }

    val root = model.find("root")
    val f1 = model.find("f1")
    val f2 = model.find("f2")
    val f3 = model.find("f3")
    val f4 = model.find("f4")
    val f5 = model.find("f5")
    val a1 = model.find("activity1")
    val subnav1 = model.find("subnav1")
    val subnav2 = model.find("subnav2")
    val subsubnav = model.find("subsubnav")

    assertSameElements(f1!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2))
    assertSameElements(f2!!.visibleDestinations, listOf(root, f1, a1, f2, f3, subnav1, subnav2))
    assertSameElements(f4!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2, f4, subsubnav))
    assertSameElements(f5!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2, f4, subsubnav, f5))
    assertSameElements(model.find("root")!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2))
    assertSameElements(subnav1!!.visibleDestinations, listOf(root, f1, a1, subnav1, f2, f3, subnav2))
  }

  fun testFindVisibleDestination() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        activity("activity1")
        navigation("subnav1") {
          fragment("f3")
        }
        navigation("subnav2") {
          fragment("f1")
          navigation("subsubnav") {
            fragment("f5")
          }
        }
      }
    }

    assertEquals(model.components[0].getChild(0), model.find("activity1")!!.findVisibleDestination("f1"))
    assertEquals(model.components[0].getChild(0), model.find("f3")!!.findVisibleDestination("f1"))
    assertEquals(model.find("subnav2")!!.getChild(0), model.find("f5")!!.findVisibleDestination("f1"))
  }

  fun testActionDestination() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          withAttribute("test2", "val2")
        }
        activity("activity1")
        navigation("subnav1") {
          fragment("f3") {
            action("a2", destination = "f1")
          }
        }
        navigation("subnav2") {
          fragment("f1") {
            withAttribute("test1", "val1")
          }
          navigation("subsubnav") {
            fragment("f5") {
              action("a1", destination = "f1")
            }
          }
        }
      }
    }

    assertEquals("val1", model.find("a1")?.actionDestination?.getAttribute(null, "test1"))
    assertEquals("val2", model.find("a2")?.actionDestination?.getAttribute(null, "test2"))
  }

  fun testActionDestinationId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1")
        }
      }
    }

    val action = model.find("a1")!!
    val fragment = model.find("f1")!!

    assertNull(action.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) { action.actionDestinationId = "f1" }
    assertEquals(fragment, action.actionDestination)
    assertEquals("f1", action.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) { action.actionDestinationId = null }
    assertNull(action.actionDestination)
    assertNull(action.actionDestinationId)
  }
}