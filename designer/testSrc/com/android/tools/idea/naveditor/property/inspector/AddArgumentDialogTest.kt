/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.CLASS_PARCELABLE
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.ide.util.TreeClassChooser
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil
import com.intellij.testFramework.replaceService
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*

class AddArgumentDialogTest : NavTestCase() {
  fun testValidation() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }
    val dialog = AddArgumentDialog(null, model.find("fragment1")!!)
    assertNotNull(dialog.doValidate())

    dialog.name = "myArgument"
    assertNull(dialog.doValidate())

    dialog.type = "boolean"
    assertNull(dialog.doValidate())

    dialog.type = "long"
    dialog.defaultValue = "1234"
    assertNull(dialog.doValidate())
    dialog.defaultValue = "abcdL"
    assertNotNull(dialog.doValidate())
    dialog.defaultValue = "1234L"
    assertNull(dialog.doValidate())

    dialog.type = "reference"
    dialog.defaultValue = "1234"
    assertNotNull(dialog.doValidate())
    dialog.defaultValue = "@id/bad_id"
    assertNotNull(dialog.doValidate())
    dialog.defaultValue = "@id/progressBar"
    assertNull(dialog.doValidate())
    dialog.defaultValue = "@layout/activity_main"
    assertNull(dialog.doValidate())

    dialog.close(0)
  }

  fun testInitWithExisting() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          argument("myArgument", type = "integer", value = "1234")
          argument("myArgument2", type = "custom.Parcelable", nullable = true)
          argument("myArgument3")
        }
      }
    }
    val fragment1 = model.find("fragment1")!!
    var dialog = AddArgumentDialog(fragment1.getChild(0), fragment1)
    assertEquals("myArgument", dialog.name)
    assertEquals("integer", dialog.type)
    assertFalse(dialog.isNullable)
    assertEquals("1234", dialog.defaultValue)
    dialog.close(0)

    dialog = AddArgumentDialog(fragment1.getChild(1), fragment1)
    assertEquals("myArgument2", dialog.name)
    assertEquals("custom.Parcelable", dialog.type)
    assertTrue(dialog.isNullable)
    assertTrue(dialog.defaultValue.isNullOrEmpty())
    dialog.close(0)

    dialog = AddArgumentDialog(fragment1.getChild(2), fragment1)
    assertEquals("myArgument3", dialog.name)
    assertNull(dialog.type)
    assertFalse(dialog.isNullable)
    assertTrue(dialog.defaultValue.isNullOrEmpty())
    dialog.close(0)
  }

  fun testDefaultValueEditor() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1") {
          argument("myArgument", type = "custom.Parcelable")
        }
      }
    }
    val fragment1 = model.find("fragment1")!!
    val dialog = AddArgumentDialog(fragment1.children[0], fragment1)

    assertTrue(dialog.myDefaultValueComboBox.isVisible)
    assertFalse(dialog.myDefaultValueTextField.isVisible)

    for (t in listOf("integer", "long", "string", "reference", null)) {
      dialog.type = t
      assertFalse(dialog.myDefaultValueComboBox.isVisible)
      assertTrue(dialog.myDefaultValueTextField.isVisible)
    }
    dialog.type = "boolean"
    assertTrue(dialog.myDefaultValueComboBox.isVisible)
    assertFalse(dialog.myDefaultValueTextField.isVisible)
    dialog.close(0)
  }

  fun testNullable() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1") {
          argument("myArgument", type = "custom.Parcelable")
        }
      }
    }
    val fragment1 = model.find("fragment1")!!
    val dialog = AddArgumentDialog(fragment1.children[0], fragment1)

    assertTrue(dialog.myNullableCheckBox.isEnabled)
    dialog.type = "string"
    assertTrue(dialog.myNullableCheckBox.isEnabled)

    for (t in listOf("integer", "long", "boolean", "reference", null)) {
      dialog.type = t
      assertFalse(dialog.myNullableCheckBox.isEnabled)
    }
    dialog.close(0)
  }

  fun testParcelable() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1")
      }
    }
    val parcelable = ClassUtil.findPsiClass(PsiManager.getInstance(project), CLASS_PARCELABLE)
    val classChooserFactory = mock(TreeClassChooserFactory::class.java)
    val classChooser = mock(TreeClassChooser::class.java)
    `when`(classChooserFactory.createInheritanceClassChooser(any(), any(), eq(parcelable), isNull())).thenReturn(classChooser)
    val customParcelable = mock(PsiClass::class.java)
    `when`(customParcelable.qualifiedName).thenReturn("custom.Parcelable")
    `when`(classChooser.selected).thenReturn(customParcelable)
    project.replaceService(TreeClassChooserFactory::class.java, classChooserFactory, testRootDisposable)

    val fragment1 = model.find("fragment1")!!
    val dialog = AddArgumentDialog(null, fragment1)

    dialog.type = "foo"
    assertEquals("custom.Parcelable", dialog.type)
    dialog.close(0)
  }

  fun testCancelCustomParcelable() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1")
      }
    }
    val parcelable = ClassUtil.findPsiClass(PsiManager.getInstance(project), CLASS_PARCELABLE)
    val classChooserFactory = mock(TreeClassChooserFactory::class.java)
    val classChooser = mock(TreeClassChooser::class.java)
    `when`(classChooserFactory.createInheritanceClassChooser(any(), any(), eq(parcelable), isNull())).thenReturn(classChooser)
    `when`(classChooser.selected).thenReturn(null)
    project.replaceService(TreeClassChooserFactory::class.java, classChooserFactory, testRootDisposable)

    val fragment1 = model.find("fragment1")!!
    val dialog = AddArgumentDialog(null, fragment1)

    dialog.type = "integer"
    dialog.type = "foo"
    assertEquals("integer", dialog.type)
    dialog.close(0)
  }
}

private fun <T> any(): T = ArgumentMatchers.any() as T
private fun <T> eq(arg: T): T = ArgumentMatchers.eq(arg) as T
