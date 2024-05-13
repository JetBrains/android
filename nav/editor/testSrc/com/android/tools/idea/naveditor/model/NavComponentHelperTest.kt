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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavDestinationInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.command.WriteCommandAction
import icons.StudioIcons.NavEditor.Properties.ACTION
import icons.StudioIcons.NavEditor.Tree.ACTIVITY
import icons.StudioIcons.NavEditor.Tree.FRAGMENT
import icons.StudioIcons.NavEditor.Tree.INCLUDE_GRAPH
import icons.StudioIcons.NavEditor.Tree.NESTED_GRAPH
import icons.StudioIcons.NavEditor.Tree.PLACEHOLDER
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import javax.swing.Icon

class NavComponentHelperTest {

  @Test
  fun testUiName() {
    val component = mock(NlComponent::class.java)
    whenever(component.id).thenCallRealMethod()
    whenever(component.tagName).thenReturn("myTag")
    assertEquals("myTag", component.uiName)
    whenever(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("com.example.Foo")
    assertEquals("Foo", component.uiName)
    whenever(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("Bar")
    assertEquals("Bar", component.uiName)
    whenever(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)).thenReturn("@+id/myId")
    assertEquals("myId", component.uiName)
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

    val root = model.treeReader.find("root")
    val f1 = model.treeReader.find("f1")
    val f2 = model.treeReader.find("f2")
    val f3 = model.treeReader.find("f3")
    val f4 = model.treeReader.find("f4")
    val f5 = model.treeReader.find("f5")
    val a1 = model.treeReader.find("activity1")
    val subnav1 = model.treeReader.find("subnav1")
    val subnav2 = model.treeReader.find("subnav2")
    val subsubnav = model.treeReader.find("subsubnav")

    var map = f1!!.visibleDestinations
    assertSameElements(map.keys, setOf(f1, root))
    assertSameElements(map[f1]!!, listOf())
    assertSameElements(map[root]!!, listOf(a1, subnav1, subnav2))

    map = f2!!.visibleDestinations
    assertSameElements(map.keys, listOf(f2, subnav1, root))
    assertSameElements(map[f2]!!, listOf())
    assertSameElements(map[subnav1]!!, listOf(f3))
    assertSameElements(map[root]!!, listOf(a1, f1, subnav2))

    map = f4!!.visibleDestinations
    assertSameElements(map.keys, listOf(f4, subnav2, root))
    assertSameElements(map[f4]!!, listOf())
    assertSameElements(map[subnav2]!!, listOf(subsubnav))
    assertSameElements(map[root]!!, listOf(f1, a1, subnav1))

    map = f5!!.visibleDestinations
    assertSameElements(map.keys, listOf(f5, subsubnav, subnav2, root))
    assertSameElements(map[f5]!!, listOf())
    assertSameElements(map[subsubnav]!!, listOf())
    assertSameElements(map[subnav2]!!, listOf(f4))
    assertSameElements(map[root]!!, listOf(f1, a1, subnav1))

    map = root!!.visibleDestinations
    assertSameElements(map.keys, listOf(root))
    assertSameElements(map[root]!!, listOf(f1, a1, subnav1, subnav2))

    map = subnav1!!.visibleDestinations
    assertSameElements(map.keys, listOf(subnav1, root))
    assertSameElements(map[subnav1]!!, listOf(f2, f3))
    assertSameElements(map[root]!!, listOf(f1, a1, subnav2))
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

    assertEquals(model.treeReader.components[0].getChild(0), model.treeReader.find("activity1")!!.findVisibleDestination("f1"))
    assertEquals(model.treeReader.components[0].getChild(0), model.treeReader.find("f3")!!.findVisibleDestination("f1"))
    assertEquals(model.treeReader.find("subnav2")!!.getChild(0), model.treeReader.find("f5")!!.findVisibleDestination("f1"))
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

    assertEquals("val1", model.treeReader.find("a1")?.actionDestination?.getAttribute(null, "test1"))
    assertEquals("val2", model.treeReader.find("a2")?.actionDestination?.getAttribute(null, "test2"))
  }

  fun testActionDestinationId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1")
        }
      }
    }

    val action = model.treeReader.find("a1")!!
    val fragment = model.treeReader.find("f1")!!

    assertNull(action.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) { action.actionDestinationId = "f1" }
    assertEquals(fragment, action.actionDestination)
    assertEquals("f1", action.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) { action.actionDestinationId = null }
    assertNull(action.actionDestination)
    assertNull(action.actionDestinationId)
  }

  fun testEffectiveDestination() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
        navigation("nav1") {
          fragment("f3") {
            action("a1", popUpTo = "f1")
            action("a2", popUpTo = "f1", inclusive = true)
            action("a3", destination = "f1", popUpTo = "f2")
          }
        }
      }
    }

    val action1 = model.treeReader.find("a1")!!
    assertEquals(action1.effectiveDestination, model.treeReader.find("f1"))
    val action2 = model.treeReader.find("a2")!!
    assertNull(action2.effectiveDestination)
    val action3 = model.treeReader.find("a3")!!
    assertEquals(action3.effectiveDestination, model.treeReader.find("f1"))
  }

  fun testEffectiveDestinationId() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
        navigation("nav1") {
          fragment("f3") {
            action("a1", popUpTo = "f1")
            action("a2", popUpTo = "f1", inclusive = true)
            action("a3", destination = "f1", popUpTo = "f2")
          }
        }
      }
    }

    val action1 = model.treeReader.find("a1")!!
    assertEquals(action1.effectiveDestinationId, "f1")
    val action2 = model.treeReader.find("a2")!!
    assertNull(action2.effectiveDestinationId)
    val action3 = model.treeReader.find("a3")!!
    assertEquals(action3.effectiveDestinationId, "f1")
  }

  fun testDefaultActionIds() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.treeReader.find("f1")!!
    val root = model.treeReader.components[0]!!
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_f1_to_f2", f1.createAction("f2")?.id) }
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_f1_self", f1.createAction("f1")?.id) }
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_f1_self2", f1.createAction("f1")?.id) }
    WriteCommandAction.runWriteCommandAction(project) {
      assertEquals(
          "action_f1_pop",
          f1.createAction {
            popUpTo = "f1"
            inclusive = true
          }?.id)
    }
    WriteCommandAction.runWriteCommandAction(project) {
      assertEquals(
          "action_f1_pop_including_f2",
          f1.createAction {
            popUpTo = "f2"
            inclusive = true
          }?.id)
    }
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_global_f1", root.createAction("f1")?.id) }
  }

  fun testGenerateActionId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        navigation("subnav")
      }
    }

    assertEquals("action_f1_self", generateActionId(model.treeReader.find("f1")!!, "f1", null, false))
    assertEquals("action_f1_self", generateActionId(model.treeReader.find("f1")!!, "f1", "f2", false))
    assertEquals("action_f1_self", generateActionId(model.treeReader.find("f1")!!, "f1", "f2", true))

    assertEquals("action_subnav_self", generateActionId(model.treeReader.find("subnav")!!, "subnav", "f2", true))

    assertEquals("action_f1_to_f2", generateActionId(model.treeReader.find("f1")!!, "f2", null, false))
    assertEquals("action_f1_to_f2", generateActionId(model.treeReader.find("f1")!!, "f2", "f1", false))
    assertEquals("action_f1_to_f2", generateActionId(model.treeReader.find("f1")!!, "f2", "f3", true))

    assertEquals("action_global_f1", generateActionId(model.treeReader.find("subnav")!!, "f1", null, false))
    assertEquals("action_global_f1", generateActionId(model.treeReader.find("subnav")!!, "f1", "f2", false))
    assertEquals("action_global_f1", generateActionId(model.treeReader.find("subnav")!!, null, "f1", false))

    assertEquals("action_f1_pop", generateActionId(model.treeReader.find("f1")!!, null, "f1", true))
    assertEquals("action_f1_pop_including_f2", generateActionId(model.treeReader.find("f1")!!, null, "f2", true))

    assertEquals("action_nav_pop_including_f1", generateActionId(model.treeReader.components[0]!!, null, "f1", true))

    assertEquals("", generateActionId(model.treeReader.find("f1")!!, null, null, true))
  }

  fun testCreateAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        navigation("subnav")
      }
    }

    val f1 = model.treeReader.find("f1")!!
    WriteCommandAction.runWriteCommandAction(project) { f1.createAction("f2") }
    var newAction = model.treeReader.find("action_f1_to_f2")!!
    assertEquals(f1, newAction.parent)
    assertEquals("f2", newAction.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) {
      f1.createAction {
        popUpTo = "f1"
        inclusive = true
      }
    }
    newAction = model.treeReader.find("action_f1_pop")!!
    assertEquals(f1, newAction.parent)
    assertNull(newAction.actionDestinationId)
    assertEquals("f1", newAction.popUpTo)
  }

  fun testDeleteMetrics() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1", layout = "foo") {
          action("action", destination = "f1")
          argument("arg")
        }
        navigation("subnav") {
          deeplink("deepLink","foo")
        }
        include("navigation")
      }
    }

    TestNavUsageTracker.create(model).use { tracker ->
      model.delete(listOf(model.treeReader.find("action")!!))
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.DELETE_ACTION)
                                 .setActionInfo(NavActionInfo.newBuilder()
                                                  .setCountSame(1)
                                                  .setType(NavActionInfo.ActionType.SELF)
                                                  .setCountToDestination(1)
                                                  .setCountFromSource(1))
                                 .build())

      model.delete(listOf(model.treeReader.find("f1")!!.children[0]))
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.DELETE_ARGUMENT)
                                 .build())

      model.delete(listOf(model.treeReader.find("f1")!!))
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.DELETE_DESTINATION)
                                 .setDestinationInfo(NavDestinationInfo.newBuilder()
                                                       .setType(NavDestinationInfo.DestinationType.FRAGMENT)
                                                       .setHasLayout(true))
                                 .build())

      model.delete(listOf(model.treeReader.find("subnav")!!.children[0]))
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.DELETE_DEEPLINK)
                                 .build())

      model.delete(listOf(model.treeReader.find("subnav")!!))
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.DELETE_NESTED)
                                 .build())

      model.delete(listOf(model.treeReader.find("nav")!!))
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.DELETE_INCLUDE)
                                 .build())
    }
  }

  fun testIcon() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", name = "myClass") {
          action("action", destination = "fragment2")
        }
        fragment("fragment2")
        activity("activity", name = "myClass2")
        navigation("nav")
        include("navigation")
      }
    }

    testIcon(model, "fragment1", FRAGMENT)
    testIcon(model, "fragment2", PLACEHOLDER)
    testIcon(model, "activity", ACTIVITY)
    testIcon(model, "action", ACTION)
    testIcon(model, "nav", NESTED_GRAPH)

    val root = model.treeReader.find("root")!!
    val include = root.children.first { it.isInclude }
    testIcon(include, INCLUDE_GRAPH)
  }

  private fun testIcon(model: SyncNlModel, name: String, expected: Icon) {
    val component = model.treeReader.find(name)!!
    testIcon(component, expected)
  }

  private fun testIcon(component: NlComponent, expected: Icon) {
    val actual = component.mixin?.icon
    assertEquals(expected, actual)
  }

  fun testGetArguments() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", name = "myClass") {
          argument("argument1")
          argument("argument2")
          argument("argument3")
        }
      }
    }

    val fragment1 = model.treeReader.find("fragment1")!!
    val arguments = fragment1.getArgumentNames()
    Truth.assertThat(arguments).containsExactlyElementsIn(arrayOf("argument1", "argument2", "argument3"))
  }
}
