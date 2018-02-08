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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
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
  fun testExisting() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2") {
            withAttribute(AUTO_URI, NavigationSchema.ATTR_ENTER_ANIM, "@anim/fade_in")
            withAttribute(AUTO_URI, NavigationSchema.ATTR_POP_UP_TO, "@id/f2")
            withAttribute(AUTO_URI, NavigationSchema.ATTR_CLEAR_TASK, "true")
          }
        }
        fragment("f2")
      }
    }

    val dialog = AddActionDialog(
        AddActionDialog.Defaults.NORMAL,
        model.find("a1"),
        model.find("f1")!!,
        null
    )
    dialog.close(0)
    assertEquals(model.find("f2"), dialog.destination)
    assertEquals("@anim/fade_in", dialog.enterTransition)
    assertTrue(dialog.isClearTask)
    assertEquals(model.find("f1"), dialog.source)
    assertEquals("@id/f2", dialog.popTo)
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
        model.find("f1")!!,
        null
    )
    val dialog = dialogWrapper.dialog

    dialog.myDestinationComboBox.selectedIndex = 2

    assertEquals(model.find("f1"), dialog.myFromComboBox.getItemAt(0))
    assertEquals(1, dialog.myFromComboBox.itemCount)
    assertFalse(dialog.myFromComboBox.isEnabled)

    assertEquals(null, dialog.myDestinationComboBox.getItemAt(0))
    assertEquals(model.find("root"), dialog.myDestinationComboBox.getItemAt(1).component)
    assertEquals(model.find("f1"), dialog.myDestinationComboBox.getItemAt(2).component)
    assertEquals(model.find("f2"), dialog.myDestinationComboBox.getItemAt(3).component)
    assertTrue(dialog.myDestinationComboBox.getItemAt(4).isReturnToSource)
    assertEquals(5, dialog.myDestinationComboBox.itemCount)
    assertTrue(dialog.myDestinationComboBox.isEnabled)

    assertEquals(null, dialog.myEnterComboBox.getItemAt(0).value)
    assertEquals("@anim/fade_in", dialog.myEnterComboBox.getItemAt(1).value)
    assertEquals("@anim/fade_out", dialog.myEnterComboBox.getItemAt(2).value)
    assertEquals("@animator/test1", dialog.myEnterComboBox.getItemAt(3).value)
    assertEquals(4, dialog.myEnterComboBox.itemCount)

    assertEquals(null, dialog.myExitComboBox.getItemAt(0).value)
    assertEquals("@anim/fade_in", dialog.myExitComboBox.getItemAt(1).value)
    assertEquals("@anim/fade_out", dialog.myExitComboBox.getItemAt(2).value)
    assertEquals("@animator/test1", dialog.myExitComboBox.getItemAt(3).value)
    assertEquals(4, dialog.myExitComboBox.itemCount)

    assertEquals(null, dialog.myPopToComboBox.getItemAt(0))
    assertEquals("root", dialog.myPopToComboBox.getItemAt(1).id)
    assertEquals("f1", dialog.myPopToComboBox.getItemAt(2).id)
    assertEquals("f2", dialog.myPopToComboBox.getItemAt(3).id)
    assertEquals(4, dialog.myPopToComboBox.itemCount)

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
        model.find("f1")!!,
        null
    )
    val dialog = dialogWrapper.dialog

    // Initial condition that will be restored
    dialog.myPopToComboBox.selectedIndex = 2

    // select "source"
    dialog.myDestinationComboBox.selectedIndex = 4

    assertEquals(model.find("f1"), dialog.myPopToComboBox.selectedItem)
    assertFalse(dialog.myPopToComboBox.isEnabled)
    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertFalse(dialog.myInclusiveCheckBox.isEnabled)

    // Now select a different destination and the original state should be restored
    dialog.myDestinationComboBox.selectedIndex = 3

    assertEquals(model.find("f1"), dialog.myPopToComboBox.selectedItem)
    assertTrue(dialog.myPopToComboBox.isEnabled)
    assertFalse(dialog.myInclusiveCheckBox.isSelected)
    assertTrue(dialog.myInclusiveCheckBox.isEnabled)

    // Change the initial "inclusive" and make sure it's restored correctly
    dialog.myInclusiveCheckBox.isSelected = true

    dialog.myDestinationComboBox.selectedIndex = 4

    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertFalse(dialog.myInclusiveCheckBox.isEnabled)

    dialog.myDestinationComboBox.selectedIndex = 3
    assertTrue(dialog.myInclusiveCheckBox.isSelected)
    assertTrue(dialog.myInclusiveCheckBox.isEnabled)

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
        model.find("subnav2")!!,
        null
    )
    val dialog = dialogWrapper.dialog

    assertEquals(null, dialog.myDestinationComboBox.getItemAt(0))
    assertEquals(model.find("subnav1"), dialog.myDestinationComboBox.getItemAt(1).component)
    assertEquals(model.find("f2"), dialog.myDestinationComboBox.getItemAt(2).component)
    assertEquals(model.find("f3"), dialog.myDestinationComboBox.getItemAt(3).component)
    assertEquals(model.find("subnav2"), dialog.myDestinationComboBox.getItemAt(4).component)
    assertTrue(dialog.myDestinationComboBox.getItemAt(5).isReturnToSource)
    assertTrue(dialog.myDestinationComboBox.getItemAt(6).isSeparator)
    assertEquals(model.find("f4"), dialog.myDestinationComboBox.getItemAt(7).component)
    assertEquals(model.find("subnav3"), dialog.myDestinationComboBox.getItemAt(8).component)
    assertTrue(dialog.myDestinationComboBox.getItemAt(9).isSeparator)
    assertEquals(model.find("root"), dialog.myDestinationComboBox.getItemAt(10).component)
    assertEquals(model.find("f1"), dialog.myDestinationComboBox.getItemAt(11).component)
    assertEquals(model.find("othersubnav"), dialog.myDestinationComboBox.getItemAt(12).component)

    assertEquals(13, dialog.myDestinationComboBox.itemCount)
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
        model.find("f4")!!,
        null
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myDestinationComboBox
    assertEquals(null, combo.getItemAt(0))
    assertEquals(model.find("subnav2"), combo.getItemAt(1).component)
    assertEquals(model.find("f4"), combo.getItemAt(2).component)
    assertEquals(model.find("subnav3"), combo.getItemAt(3).component)
    assertTrue(combo.getItemAt(4).isReturnToSource)
    assertTrue(combo.getItemAt(5).isSeparator)
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
        model.find("root")!!,
        null
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myDestinationComboBox
    assertEquals(null, combo.getItemAt(0))
    assertEquals(model.find("root"), combo.getItemAt(1).component)
    assertTrue(combo.getItemAt(2).isReturnToSource)
    assertTrue(combo.getItemAt(3).isSeparator)
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
        model.find("subnav2")!!,
        null
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
    assertEquals("subnav1", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 2)
    assertEquals("f2", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 3)
    assertEquals("f3", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 4)
    assertEquals("↻ subnav2", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 5)
    assertEquals("↵ Source", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    assertInstanceOf(renderer.getListCellRendererComponent(list, combo.getItemAt(6), 6, false, false), TitledSeparator::class.java)
    rendererComponent = getRendererComponent(renderer, list, combo, 7)
    assertEquals("f4", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 8)
    assertEquals("subnav3", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    assertInstanceOf(renderer.getListCellRendererComponent(list, combo.getItemAt(9), 9, false, false), TitledSeparator::class.java)
    rendererComponent = getRendererComponent(renderer, list, combo, 10)
    assertEquals("navigation (Root)", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 11)
    assertEquals("  f1", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 12)
    assertEquals("  othersubnav", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)

    // Check that it doesn't have leading spaces when it's the selected item (not in the popup)
    rendererComponent = renderer.getListCellRendererComponent(list, combo.getItemAt(11), -1, false, false) as JLabel
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
        model.find("subnav2")!!,
        null
    )
    val dialog = dialogWrapper.dialog

    val combo = dialog.myPopToComboBox
    val renderer = combo.renderer

    @Suppress("UNCHECKED_CAST")
    val list = mock(JList::class.java) as JList<out NlComponent>
    val font = UIUtil.getListFont().deriveFont(Font.PLAIN)
    `when`(list.font).thenReturn(font)

    var rendererComponent = getRendererComponent(renderer, list, combo, 0)
    assertEquals("None", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 1)
    assertEquals("navigation (Root)", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 2)
    assertEquals("  f1", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 3)
    assertEquals("othersubnav", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
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
    assertEquals("subnav2", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 8)
    assertEquals("  f4", rendererComponent.text)
    assertFalse(rendererComponent.font.isBold)
    rendererComponent = getRendererComponent(renderer, list, combo, 9)
    assertEquals("subnav3", rendererComponent.text)
    assertTrue(rendererComponent.font.isBold)

    // Check that it doesn't have leading spaces when it's the selected item (not in the popup)
    rendererComponent = renderer.getListCellRendererComponent(list, combo.getItemAt(8), -1, false, false) as JLabel
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

    var dialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f1, null)
    assertEquals(null, dialog.destination)
    assertEquals(f1, dialog.source)
    assertFalse(dialog.isInclusive)
    assertEquals(null, dialog.popTo)
    dialog.close(0)

    dialog = AddActionDialog(AddActionDialog.Defaults.GLOBAL, null, f1, null)
    assertEquals(f1, dialog.destination)
    assertEquals(model.find("root"), dialog.source)
    assertFalse(dialog.isInclusive)
    assertEquals(null, dialog.popTo)
    dialog.close(0)

    dialog = AddActionDialog(
        AddActionDialog.Defaults.RETURN_TO_SOURCE,
        null,
        f1,
        null
    )
    assertEquals(null, dialog.destination)
    assertEquals(f1, dialog.source)
    assertTrue(dialog.isInclusive)
    assertEquals("@id/f1", dialog.popTo)
    dialog.close(0)
  }
}