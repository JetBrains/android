// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.actionDestination
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.Font
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class AddActionDialogTest : NavTestCase() {

  fun testCreate() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }

    val dialog = AddActionDialog(
      AddActionDialog.Defaults.NORMAL,
      null,
      model.find("f1")!!
    )
    val destinationCombo = dialog.dialog.myDestinationComboBox
    val f2 = model.find("f2")
    for (i in 0 until destinationCombo.itemCount) {
      if (destinationCombo.getItemAt(i)?.component == f2) {
        destinationCombo.selectedIndex = i
        break
      }
    }
    dialog.dialog.myIdTextField.text = "foo"
    dialog.close(0)
    dialog.writeUpdatedAction()

    val action = model.find("foo")!!
    assertEquals(model.find("f2"), action.actionDestination)
    assertEquals(model.find("f1"), dialog.source)
  }

  fun testCreateWithGeneratedId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }

    val dialog = AddActionDialog(
      AddActionDialog.Defaults.NORMAL,
      null,
      model.find("f1")!!
    )
    val destinationCombo = dialog.dialog.myDestinationComboBox
    val f2 = model.find("f2")
    for (i in 0 until destinationCombo.itemCount) {
      if (destinationCombo.getItemAt(i)?.component == f2) {
        destinationCombo.selectedIndex = i
        break
      }
    }
    dialog.close(0)
    dialog.writeUpdatedAction()

    val action = model.find("action_f1_to_f2")!!
    assertEquals(model.find("f2"), action.actionDestination)
    assertEquals(model.find("f1"), dialog.source)
  }

  fun testExisting() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2") {
            withAttribute(AUTO_URI, NavigationSchema.ATTR_ENTER_ANIM, "@anim/fade_in")
            withAttribute(AUTO_URI, NavigationSchema.ATTR_POP_UP_TO, "@id/f2")
            withAttribute(AUTO_URI, NavigationSchema.ATTR_POP_ENTER_ANIM, "@anim/fade_out")
          }
        }
        fragment("f2")
      }
    }

    val dialog = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        model.find("a1"),
        model.find("f1")!!
    )
    dialog.close(0)
    assertEquals(model.find("f2"), dialog.destination)
    assertEquals("@anim/fade_in", dialog.enterTransition)
    assertEquals("@anim/fade_out", dialog.popEnterTransition)
    assertEquals(model.find("f1"), dialog.source)
    assertEquals("f2", dialog.popTo)
    assertEquals("a1", dialog.id)
  }

  fun testExistingPop() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", popUpTo = "f2") {
            withAttribute(AUTO_URI, NavigationSchema.ATTR_ENTER_ANIM, "@anim/fade_in")
            withAttribute(AUTO_URI, NavigationSchema.ATTR_POP_ENTER_ANIM, "@anim/fade_out")
          }
        }
        fragment("f2")
      }
    }

    val dialog = AddActionDialog(
      AddActionDialog.Defaults.NORMAL,
      model.find("a1"),
      model.find("f1")!!
    )
    dialog.close(0)
    assertEquals("f2", dialog.popTo)
    assertEquals("@anim/fade_in", dialog.enterTransition)
    assertEquals("@anim/fade_out", dialog.popEnterTransition)
    assertEquals(model.find("f1"), dialog.source)
    assertEquals("f2", dialog.popTo)
    assertEquals("a1", dialog.id)
  }

  fun testContent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("f1")!!
    )
    val dialog = dialogWrapper.dialog

    dialog.myDestinationComboBox.selectedIndex = 3

    assertEquals(model.find("f1"), dialog.myFromComboBox.getItemAt(0))
    assertEquals(1, dialog.myFromComboBox.itemCount)
    assertFalse(dialog.myFromComboBox.isEnabled)

    assertEquals(null, dialog.myDestinationComboBox.getItemAt(0))
    assertTrue(dialog.myDestinationComboBox.getItemAt(1).isReturnToSource)
    assertTrue(dialog.myDestinationComboBox.getItemAt(2).isSeparator)
    assertEquals(model.find("f1"), dialog.myDestinationComboBox.getItemAt(3).component)
    assertEquals(model.find("root"), dialog.myDestinationComboBox.getItemAt(4).component)
    assertEquals(model.find("f2"), dialog.myDestinationComboBox.getItemAt(5).component)
    assertEquals(6, dialog.myDestinationComboBox.itemCount)
    assertTrue(dialog.myDestinationComboBox.isEnabled)

    for (combo in arrayOf(dialog.myEnterComboBox, dialog.myExitComboBox, dialog.myPopEnterComboBox, dialog.myPopExitComboBox)) {
      assertEquals(null, combo.getItemAt(0).value)
      assertEquals("@anim/fade_in", combo.getItemAt(1).value)
      assertEquals("@anim/fade_out", combo.getItemAt(2).value)
      assertEquals("@animator/test1", combo.getItemAt(3).value)
      assertEquals(4, combo.itemCount)
    }

    assertEquals(null, dialog.myPopToComboBox.getItemAt(0))
    assertEquals("f1", dialog.myPopToComboBox.getItemAt(1).component?.id)
    assertEquals("root", dialog.myPopToComboBox.getItemAt(2).component?.id)
    assertEquals("f2", dialog.myPopToComboBox.getItemAt(3).component?.id)
    assertEquals(4, dialog.myPopToComboBox.itemCount)

    assertEquals("action_f1_self", dialog.myIdTextField.text)

    dialogWrapper.close(0)
  }

  fun testReturnToSource() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("f1")!!
    )
    val dialog = dialogWrapper.dialog

    dialog.myDestinationComboBox.selectedIndex = 1

    val entry = dialog.myPopToComboBox.selectedItem as AddActionDialog.DestinationListEntry?
    assertEquals(model.find("f1"), entry?.component)
    assertFalse(dialog.myPopToComboBox.isEnabled)
    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertFalse(dialog.myInclusiveCheckBox.isEnabled)
    assertEquals("action_f1_pop", dialog.myIdTextField.text)
    assertTrue(dialog.myPopEnterComboBox.isEnabled)
    assertTrue(dialog.myPopExitComboBox.isEnabled)

    dialogWrapper.close(0)
  }

  fun testReturnToSourceRestoresPrevious() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("f1")!!
    )
    val dialog = dialogWrapper.dialog

    // Initial condition that will be restored
    dialog.myPopToComboBox.selectedIndex = 1

    // select "source"
    dialog.myDestinationComboBox.selectedIndex = 1

    var entry = dialog.myPopToComboBox.selectedItem as AddActionDialog.DestinationListEntry?
    assertEquals(model.find("f1"), entry?.component)
    assertFalse(dialog.myPopToComboBox.isEnabled)
    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertFalse(dialog.myInclusiveCheckBox.isEnabled)

    // Now select a different destination and the original state should be restored
    dialog.myDestinationComboBox.selectedIndex = 3

    entry = dialog.myPopToComboBox.selectedItem as AddActionDialog.DestinationListEntry?
    assertEquals(model.find("f1"), entry?.component)
    assertTrue(dialog.myPopToComboBox.isEnabled)
    assertFalse(dialog.myInclusiveCheckBox.isSelected)
    assertTrue(dialog.myInclusiveCheckBox.isEnabled)

    // Change the initial "inclusive" and make sure it's restored correctly
    dialog.myInclusiveCheckBox.isSelected = true

    dialog.myDestinationComboBox.selectedIndex = 1

    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertFalse(dialog.myInclusiveCheckBox.isEnabled)

    dialog.myDestinationComboBox.selectedIndex = 3
    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertTrue(dialog.myInclusiveCheckBox.isEnabled)

    // Select "source" and then "None" and verify popTo is reenabled
    dialog.myDestinationComboBox.selectedIndex = 1
    assertFalse(dialog.myPopToComboBox.isEnabled)
    dialog.myDestinationComboBox.selectedIndex = 0
    assertTrue(dialog.myPopToComboBox.isEnabled)

    dialogWrapper.close(0)
  }

  fun testDestinationsForNestedSubnav() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        navigation("othersubnav") {
          fragment("otherfragment1")
        }
        navigation("subnav1") {
          fragment("f2")
          fragment("f3")
          navigation("subnav2") {
            fragment("f4")
            navigation("subnav3") {
              fragment("f5")
            }
          }
        }
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("subnav2")!!
    )
    val dialog = dialogWrapper.dialog

    assertEquals(null, dialog.myDestinationComboBox.getItemAt(0))
    assertTrue(dialog.myDestinationComboBox.getItemAt(1).isReturnToSource)
    assertTrue(dialog.myDestinationComboBox.getItemAt(2).isSeparator)
    assertEquals(model.find("subnav2"), dialog.myDestinationComboBox.getItemAt(3).component)
    assertEquals(model.find("f4"), dialog.myDestinationComboBox.getItemAt(4).component)
    assertEquals(model.find("subnav3"), dialog.myDestinationComboBox.getItemAt(5).component)
    assertEquals(model.find("subnav1"), dialog.myDestinationComboBox.getItemAt(6).component)
    assertEquals(model.find("f2"), dialog.myDestinationComboBox.getItemAt(7).component)
    assertEquals(model.find("f3"), dialog.myDestinationComboBox.getItemAt(8).component)
    assertEquals(model.find("root"), dialog.myDestinationComboBox.getItemAt(9).component)
    assertEquals(model.find("f1"), dialog.myDestinationComboBox.getItemAt(10).component)
    assertEquals(model.find("othersubnav"), dialog.myDestinationComboBox.getItemAt(11).component)

    assertEquals(12, dialog.myDestinationComboBox.itemCount)
    dialogWrapper.close(0)
  }

  fun testDestinationsForNestedFragment() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        navigation("othersubnav") {
          fragment("otherfragment1")
        }
        navigation("subnav1") {
          fragment("f2")
          fragment("f3")
          navigation("subnav2") {
            fragment("f4")
            navigation("subnav3") {
              fragment("f5")
            }
          }
        }
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("f4")!!
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myDestinationComboBox
    assertEquals(null, combo.getItemAt(0))
    assertTrue(combo.getItemAt(1).isReturnToSource)
    assertTrue(combo.getItemAt(2).isSeparator)
    assertEquals(model.find("f4"), combo.getItemAt(3).component)
    assertEquals(model.find("subnav2"), combo.getItemAt(4).component)
    assertEquals(model.find("subnav3"), combo.getItemAt(5).component)
    assertEquals(model.find("subnav1"), combo.getItemAt(6).component)
    assertEquals(model.find("f2"), combo.getItemAt(7).component)
    assertEquals(model.find("f3"), combo.getItemAt(8).component)
    assertEquals(model.find("root"), combo.getItemAt(9).component)
    assertEquals(model.find("f1"), combo.getItemAt(10).component)
    assertEquals(model.find("othersubnav"), combo.getItemAt(11).component)

    assertEquals(12, combo.itemCount)
    dialogWrapper.close(0)
  }

  fun testDestinationsForRoot() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        navigation("subnav1") {
          fragment("otherfragment1")
        }
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("root")!!
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myDestinationComboBox
    assertEquals(null, combo.getItemAt(0))
    assertTrue(combo.getItemAt(1).isReturnToSource)
    assertTrue(combo.getItemAt(2).isSeparator)
    assertEquals(model.find("root"), combo.getItemAt(3).component)
    assertEquals(model.find("f1"), combo.getItemAt(4).component)
    assertEquals(model.find("subnav1"), combo.getItemAt(5).component)

    assertEquals(6, combo.itemCount)
    dialogWrapper.close(0)
  }

  fun testDestinationRendering() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        navigation("othersubnav")
        navigation("subnav1") {
          fragment("f2")
          fragment("f3")
          navigation("subnav2") {
            fragment("f4")
            navigation("subnav3")
          }
        }
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("subnav2")!!
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myDestinationComboBox
    val renderer = combo.renderer

    @Suppress("UNCHECKED_CAST")
    val list = mock(JList::class.java) as JList<out AddActionDialog.DestinationListEntry>
    val font = UIUtil.getListFont().deriveFont(Font.PLAIN)
    `when`(list.font).thenReturn(font)
    var rendererComponent = getRendererComponent(renderer, list, combo, 0)
    assertEquals("None", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 1)
    assertEquals("↵ Source", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    assertInstanceOf(renderer.getListCellRendererComponent(list, combo.getItemAt(2), 2, false, false), TitledSeparator::class.java)
    rendererComponent = getRendererComponent(renderer, list, combo, 3)
    assertEquals("subnav2 (Self)", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 4)
    assertEquals("  f4", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 5)
    assertEquals("  subnav3", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 6)
    assertEquals("subnav1", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 7)
    assertEquals("  f2", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 8)
    assertEquals("  f3", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 9)
    assertEquals("Root", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 10)
    assertEquals("  f1", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 11)
    assertEquals("  othersubnav", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)

    // Check that it doesn't have leading spaces when it's the selected item (not in the popup)
    rendererComponent = renderer.getListCellRendererComponent(list, combo.getItemAt(10), -1, false, false) as JLabel
    assertEquals("f1", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)

    dialogWrapper.close(0)

  }

  private fun <T> getRendererComponent(renderer: ListCellRenderer<in T>, list: JList<out T>, combo: JComboBox<T>, index: Int)
    = renderer.getListCellRendererComponent(list, combo.getItemAt(index), index, false, false) as JLabel

  fun testPopToRendering() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        navigation("othersubnav")
        navigation("subnav1") {
          fragment("f2")
          fragment("f3")
          navigation("subnav2") {
            fragment("f4")
            navigation("subnav3")
          }
        }
      }
    }

    val dialogWrapper = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        null,
        model.find("subnav2")!!
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myPopToComboBox
    val renderer = combo.renderer

    @Suppress("UNCHECKED_CAST")
    val list = mock(JList::class.java) as JList<out AddActionDialog.DestinationListEntry>
    val font = UIUtil.getListFont().deriveFont(Font.PLAIN)
    `when`(list.font).thenReturn(font)

    var rendererComponent = getRendererComponent(renderer, list, combo, 0)
    assertEquals("None", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 1)
    assertEquals("subnav2 (Self)", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 2)
    assertEquals("  f4", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 3)
    assertEquals("  subnav3", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 4)
    assertEquals("subnav1", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 5)
    assertEquals("  f2", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 6)
    assertEquals("  f3", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 7)
    assertEquals("Root", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 8)
    assertEquals("  f1", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 9)
    assertEquals("  othersubnav", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)

    // Check that it doesn't have leading spaces when it's the selected item (not in the popup)
    rendererComponent = renderer.getListCellRendererComponent(list, combo.getItemAt(2), -1, false, false) as JLabel
    assertEquals("f4", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)

    dialogWrapper.close(0)

  }

  fun testDefaults() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!

    var dialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f1)
    assertEquals(null, dialog.destination)
    assertEquals(f1, dialog.source)
    assertFalse(dialog.isInclusive)
    assertEquals(null, dialog.popTo)
    assertEquals("", dialog.id)
    dialog.close(0)

    dialog = AddActionDialog(AddActionDialog.Defaults.GLOBAL, null, f1)
    assertEquals(f1, dialog.destination)
    assertEquals(model.find("root"), dialog.source)
    assertFalse(dialog.isInclusive)
    assertEquals(null, dialog.popTo)
    assertEquals("action_global_f1", dialog.id)
    dialog.close(0)

    dialog = AddActionDialog(
        AddActionDialog.Defaults.RETURN_TO_SOURCE,
        null,
        f1
    )
    assertEquals(null, dialog.destination)
    assertEquals(f1, dialog.source)
    assertTrue(dialog.isInclusive)
    assertEquals("f1", dialog.popTo)
    assertEquals("action_f1_pop", dialog.id)
    dialog.close(0)
  }

  fun testIdUpdatesRespectfully() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!

    val dialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f1)
    assertEquals("", dialog.id)
    dialog.dialog.myDestinationComboBox.selectedIndex = 5
    assertEquals("action_f1_to_f2", dialog.id)
    dialog.dialog.myDestinationComboBox.selectedIndex = 3
    assertEquals("action_f1_self", dialog.id)
    dialog.dialog.myIdTextField.text = "foo"
    dialog.dialog.myDestinationComboBox.selectedIndex = 5
    assertEquals("foo", dialog.id)
    dialog.close(0)
  }
}