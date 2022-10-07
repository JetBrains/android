/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs

import com.android.SdkConstants.AUTO_URI
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CHANGE_PROPERTY
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CREATE_ACTION
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.EDIT_ACTION
import com.google.wireless.android.sdk.stats.NavEditorEvent.Source.DESIGN_SURFACE
import com.google.wireless.android.sdk.stats.NavPropertyInfo
import com.google.wireless.android.sdk.stats.NavPropertyInfo.Property.LAUNCH_SINGLE_TOP
import com.google.wireless.android.sdk.stats.NavPropertyInfo.Property.POP_UP_TO
import com.google.wireless.android.sdk.stats.NavPropertyInfo.TagType.ACTION_TAG
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialog ->
      assertFalse(dialog.dialog.myEnterComboBox.isEnabled)
      assertFalse(dialog.dialog.myExitComboBox.isEnabled)
      assertFalse(dialog.dialog.myPopEnterComboBox.isEnabled)
      assertFalse(dialog.dialog.myPopExitComboBox.isEnabled)

      val destinationCombo = dialog.dialog.myDestinationComboBox
      val f2 = model.find("f2")
      for (i in 0 until destinationCombo.itemCount) {
        if (destinationCombo.getItemAt(i)?.component == f2) {
          destinationCombo.selectedIndex = i
          break
        }
      }
      dialog.dialog.myIdTextField.text = "foo"
      dialog.writeUpdatedAction()

      val action = model.find("foo")!!
      assertEquals(model.find("f2"), action.actionDestination)
      assertEquals(model.find("f1"), dialog.source)

      assertTrue(dialog.dialog.myEnterComboBox.isEnabled)
      assertTrue(dialog.dialog.myExitComboBox.isEnabled)
      assertTrue(dialog.dialog.myPopEnterComboBox.isEnabled)
      assertTrue(dialog.dialog.myPopExitComboBox.isEnabled)
    }
  }

  fun testCreateWithGeneratedId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialog ->
      val destinationCombo = dialog.dialog.myDestinationComboBox
      val f2 = model.find("f2")
      for (i in 0 until destinationCombo.itemCount) {
        if (destinationCombo.getItemAt(i)?.component == f2) {
          destinationCombo.selectedIndex = i
          break
        }
      }
      dialog.writeUpdatedAction()

      val action = model.find("action_f1_to_f2")!!
      assertEquals(model.find("f2"), action.actionDestination)
      assertEquals(model.find("f1"), dialog.source)
    }
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, model.find("a1"), model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals(model.find("f2"), dialog.destination)
      assertEquals("@anim/fade_in", dialog.enterTransition)
      assertEquals("@anim/fade_out", dialog.popEnterTransition)
      assertEquals(model.find("f1"), dialog.source)
      assertEquals("f2", dialog.popTo)
      assertEquals("a1", dialog.id)
      assertFalse(dialog.dialog.myIdTextField.isEnabled)
      assertTrue(dialog.dialog.myDestinationComboBox.isEnabled)
      assertEquals(model.find("f2")!!, dialog.destination)
      assertTrue(dialog.dialog.myEnterComboBox.isEnabled)
      assertTrue(dialog.dialog.myExitComboBox.isEnabled)
      assertTrue(dialog.dialog.myPopEnterComboBox.isEnabled)
      assertTrue(dialog.dialog.myPopExitComboBox.isEnabled)
    }
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, model.find("a1"), model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals("@anim/fade_in", dialog.enterTransition)
      assertEquals("@anim/fade_out", dialog.popEnterTransition)
      assertEquals(model.find("f1"), dialog.source)
      assertEquals("f2", dialog.popTo)
      assertEquals("a1", dialog.id)
    }
  }

  fun testExistingPopToInclude() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", popUpTo = "nav")
        }
        include("navigation")
      }
    }

    AddActionDialog(AddActionDialog.Defaults.NORMAL, model.find("a1"), model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals("nav", dialog.popTo)
    }
  }

  fun testContent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
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
      assertTrue(dialog.myIdTextField.isEnabled)

      for (combo in arrayOf(dialog.myEnterComboBox, dialog.myExitComboBox, dialog.myPopEnterComboBox, dialog.myPopExitComboBox)) {
        assertEquals(null, combo.getItemAt(0).value)
        assertEquals("@anim/fade_in", combo.getItemAt(1).value)
        assertEquals("@anim/fade_out", combo.getItemAt(2).value)
        assertEquals("@animator/test1", combo.getItemAt(3).value)
        assertEquals("@animator/test2", combo.getItemAt(4).value)
        assertEquals("@animator/test3", combo.getItemAt(5).value)
        assertEquals("@animator/test4", combo.getItemAt(6).value)
        assertEquals(7, combo.itemCount)
      }

      assertEquals(null, dialog.myPopToComboBox.getItemAt(0))
      assertEquals("f1", dialog.myPopToComboBox.getItemAt(1).component?.id)
      assertEquals("root", dialog.myPopToComboBox.getItemAt(2).component?.id)
      assertEquals("f2", dialog.myPopToComboBox.getItemAt(3).component?.id)
      assertEquals(4, dialog.myPopToComboBox.itemCount)

      assertEquals("action_f1_self", dialog.myIdTextField.text)
    }
  }

  fun testAddReturnToSource() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
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
    }
  }

  fun testExistingReturnToSource() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("action_f1_pop", popUpTo = "f1", inclusive = true)
        }
      }
    }

    val f1 = model.find("f1")!!
    val action_f1_pop = model.find("action_f1_pop")!!

    AddActionDialog(AddActionDialog.Defaults.NORMAL, action_f1_pop, f1, DESIGN_SURFACE).runAndClose { dialogWrapper ->
      val dialog = dialogWrapper.dialog

      val destination = dialog.myDestinationComboBox.selectedItem as? AddActionDialog.DestinationListEntry?
      assertEquals(true, destination?.isReturnToSource)

      val entry = dialog.myPopToComboBox.selectedItem as AddActionDialog.DestinationListEntry?
      assertEquals(f1, entry?.component)
      assertFalse(dialog.myPopToComboBox.isEnabled)

      assertTrue(dialog.myInclusiveCheckBox.isSelected)
      assertFalse(dialog.myInclusiveCheckBox.isEnabled)

      assertEquals("action_f1_pop", dialog.myIdTextField.text)
      assertTrue(dialog.myPopEnterComboBox.isEnabled)
      assertTrue(dialog.myPopExitComboBox.isEnabled)
    }
  }

  fun testReturnToSourceRestoresPrevious() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("f1")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
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
    }
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("subnav2")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
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
    }
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("f4")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
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
    }
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("root")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
      val dialog = dialogWrapper.dialog

      val combo = dialog.myDestinationComboBox
      assertEquals(null, combo.getItemAt(0))
      assertTrue(combo.getItemAt(1).isReturnToSource)
      assertTrue(combo.getItemAt(2).isSeparator)
      assertEquals(model.find("root"), combo.getItemAt(3).component)
      assertEquals(model.find("f1"), combo.getItemAt(4).component)
      assertEquals(model.find("subnav1"), combo.getItemAt(5).component)

      assertEquals(6, combo.itemCount)
    }
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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("subnav2")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
      val dialog = dialogWrapper.dialog

      val combo = dialog.myDestinationComboBox
      val renderer = combo.renderer

      @Suppress("UNCHECKED_CAST")
      val list = mock(JList::class.java) as JList<out AddActionDialog.DestinationListEntry>
      val font = UIUtil.getListFont().deriveFont(Font.PLAIN)
      whenever(list.font).thenReturn(font)
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
    }

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

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, model.find("subnav2")!!, DESIGN_SURFACE).runAndClose { dialogWrapper ->
      val dialog = dialogWrapper.dialog

      val combo = dialog.myPopToComboBox
      val renderer = combo.renderer

      @Suppress("UNCHECKED_CAST")
      val list = mock(JList::class.java) as JList<out AddActionDialog.DestinationListEntry>
      val font = UIUtil.getListFont().deriveFont(Font.PLAIN)
      whenever(list.font).thenReturn(font)

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
    }

  }

  fun testDefaults() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f1, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals(null, dialog.destination)
      assertEquals(f1, dialog.source)
      assertFalse(dialog.isInclusive)
      assertEquals(null, dialog.popTo)
      assertEquals("", dialog.id)
    }

    AddActionDialog(AddActionDialog.Defaults.GLOBAL, null, f1, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals(f1, dialog.destination)
      assertEquals(model.find("root"), dialog.source)
      assertFalse(dialog.isInclusive)
      assertEquals(null, dialog.popTo)
      assertEquals("action_global_f1", dialog.id)
    }

    AddActionDialog(AddActionDialog.Defaults.RETURN_TO_SOURCE, null, f1, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals(null, dialog.destination)
      assertEquals(f1, dialog.source)
      assertTrue(dialog.isInclusive)
      assertEquals("f1", dialog.popTo)
      assertEquals("action_f1_pop", dialog.id)
    }
  }

  fun testIdUpdatesRespectfully() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!

    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f1, DESIGN_SURFACE).runAndClose { dialog ->
      assertEquals("", dialog.id)
      dialog.dialog.myDestinationComboBox.selectedIndex = 5
      assertEquals("action_f1_to_f2", dialog.id)
      dialog.dialog.myDestinationComboBox.selectedIndex = 3
      assertEquals("action_f1_self", dialog.id)
      dialog.dialog.myIdTextField.text = "foo"
      dialog.dialog.myDestinationComboBox.selectedIndex = 5
      assertEquals("foo", dialog.id)
    }
  }

  fun testInclusive() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", popUpTo = "f2", inclusive = true)
        }
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!
    val a1 = model.find("a1")!!

    AddActionDialog(AddActionDialog.Defaults.NORMAL, a1, f1, DESIGN_SURFACE).runAndClose { dialog ->
      assertTrue(dialog.dialog.myInclusiveCheckBox.isSelected)
      assertTrue(dialog.dialog.myInclusiveCheckBox.isEnabled)

      val previousIndex = dialog.dialog.myPopToComboBox.selectedIndex
      dialog.dialog.myPopToComboBox.selectedIndex = 0

      assertFalse(dialog.dialog.myInclusiveCheckBox.isSelected)
      assertFalse(dialog.dialog.myInclusiveCheckBox.isEnabled)

      dialog.dialog.myPopToComboBox.selectedIndex = previousIndex

      assertFalse(dialog.dialog.myInclusiveCheckBox.isSelected)
      assertTrue(dialog.dialog.myInclusiveCheckBox.isEnabled)
    }
  }

  fun testShowAndUpdateFromDialogWithExisting() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2")
        }
        fragment("f2")
      }
    }
    val surface = model.surface
    val f1 = model.find("f1")!!
    surface.selectionModel.setSelection(listOf(f1))
    val dialog = mock(AddActionDialog::class.java)

    whenever(dialog.showAndGet()).thenReturn(true)
    val action = model.find("a1")!!
    doReturn(action).whenever(dialog).writeUpdatedAction()
    doReturn(DESIGN_SURFACE).whenever(dialog).invocationSite

    TestNavUsageTracker.create(model).use { tracker ->
      showAndUpdateFromDialog(dialog, surface, true)
      assertSameElements(surface.selectionModel.selection, f1)
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(EDIT_ACTION)
                                 .setActionInfo(NavActionInfo.newBuilder()
                                                  .setCountFromSource(1)
                                                  .setCountSame(1)
                                                  .setCountToDestination(1)
                                                  .setType(NavActionInfo.ActionType.REGULAR))
                                 .setSource(DESIGN_SURFACE).build())
    }
  }

  fun testShowAndUpdateFromDialogToCreate() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2")
        }
        fragment("f2")
      }
    }
    val surface = model.surface
    val dialog = mock(AddActionDialog::class.java)
    whenever(dialog.showAndGet()).thenReturn(true)
    val action = model.find("a1")!!
    doReturn(action).whenever(dialog).writeUpdatedAction()
    doReturn(DESIGN_SURFACE).whenever(dialog).invocationSite

    TestNavUsageTracker.create(model).use { tracker ->
      showAndUpdateFromDialog(dialog, surface, false)
      assertSameElements(surface.selectionModel.selection, action)
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(CREATE_ACTION)
                                 .setActionInfo(NavActionInfo.newBuilder()
                                                  .setCountFromSource(1)
                                                  .setCountSame(1)
                                                  .setCountToDestination(1)
                                                  .setType(NavActionInfo.ActionType.REGULAR))
                                 .setSource(DESIGN_SURFACE).build())
    }
  }

  fun testShowAndUpdateFromDialogCancel() {
    val model = mock(NlModel::class.java)
    val surface = mock(NavDesignSurface::class.java)
    val dialog = mock(AddActionDialog::class.java)
    whenever(dialog.showAndGet()).thenReturn(false)
    doReturn(DESIGN_SURFACE).whenever(dialog).invocationSite
    TestNavUsageTracker.create(model).use { tracker ->
      showAndUpdateFromDialog(dialog, surface, false)
      verifyNoMoreInteractions(tracker)
    }
  }

  fun testPropertyChangeMetrics() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!
    AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f1, DESIGN_SURFACE).runAndClose { dialog ->
      dialog.dialog.myPopToComboBox.selectedIndex = 2
      dialog.dialog.mySingleTopCheckBox.isSelected = true


      TestNavUsageTracker.create(model).use { tracker ->
        dialog.writeUpdatedAction()
        verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                   .setType(CHANGE_PROPERTY)
                                   .setPropertyInfo(NavPropertyInfo.newBuilder()
                                                      .setWasEmpty(true)
                                                      .setProperty(POP_UP_TO)
                                                      .setContainingTag(ACTION_TAG))
                                   .setSource(DESIGN_SURFACE).build())
        verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                   .setType(CHANGE_PROPERTY)
                                   .setPropertyInfo(NavPropertyInfo.newBuilder()
                                                      .setWasEmpty(true)
                                                      .setProperty(LAUNCH_SINGLE_TOP)
                                                      .setContainingTag(ACTION_TAG))
                                   .setSource(DESIGN_SURFACE).build())
      }
    }
  }
}