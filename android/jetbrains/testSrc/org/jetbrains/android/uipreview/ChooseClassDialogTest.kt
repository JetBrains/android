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
package org.jetbrains.android.uipreview

import com.android.tools.adtui.swing.FakeUi
import com.intellij.psi.PsiClass
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.Component
import javax.swing.DefaultListModel
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.junit.Ignore
import org.junit.Rule

class ChooseClassDialogTest : AndroidTestCase() {

  @get:Rule
  val runInEdt = EdtRule()

  fun testIsPublicAndUnRestricted() {
    @Language("JAVA")
    val restrictText =
      """package android.support.annotation;

        import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
        import static java.lang.annotation.ElementType.CONSTRUCTOR;
        import static java.lang.annotation.ElementType.FIELD;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.ElementType.PACKAGE;
        import static java.lang.annotation.ElementType.TYPE;
        import static java.lang.annotation.RetentionPolicy.CLASS;

        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;

        @Retention(CLASS)
        @Target({ANNOTATION_TYPE,TYPE,METHOD,CONSTRUCTOR,FIELD,PACKAGE})
        public @interface RestrictTo {

            Scope[] value();

            enum Scope {
                LIBRARY,
                LIBRARY_GROUP,
                @Deprecated
                GROUP_ID,
                TESTS,
                SUBCLASSES,
            }
        }"""

    @Language("JAVA")
    val protectedView =
      """package p1.p2;

        import android.content.Context;
        import android.widget.ImageView;

        class ProtectedImageView extends ImageView {
            public ProtectedImageView(Context context) {
                super(context);
            }
        }"""

    @Language("JAVA")
    val restrictedView =
      """package p1.p2;

        import android.content.Context;
        import android.support.annotation.RestrictTo;
        import android.widget.ImageView;

        @RestrictTo(RestrictTo.Scope.SUBCLASSES)
        public class HiddenImageView extends ImageView {
            public HiddenImageView(Context context) {
                super(context);
            }
        }"""

    @Language("JAVA")
    val view =
      """package p1.p2;

        import android.content.Context;
        import android.widget.ImageView;

        public class VisibleImageView extends ImageView {
            public VisibleImageView(Context context) {
                super(context);
            }
        }"""

    myFixture.addClass(restrictText)
    val isPublicAndUnRestricted = ChooseClassDialog.getIsPublicAndUnrestrictedFilter()
    assertFalse(isPublicAndUnRestricted.test(myFixture.addClass(protectedView)))
    assertFalse(isPublicAndUnRestricted.test(myFixture.addClass(restrictedView)))
    assertTrue(isPublicAndUnRestricted.test(myFixture.addClass(view)))
  }

  // AS Koala 2024.1.2 Canary 7 Merge: Disabled test: Not allowed to run in EDT; Issue showing the dialog on macOS in headless mode.
  @Suppress("unused", "TestFunctionName")
  @RunsInEdt
  fun _testCheckboxControlsLibraryClassVisibility() {
    val userDefinedClass1 = myFixture.addClass("package com.example; public class MyClass {}")
    val userDefinedClass2 = myFixture.addClass("package com.example; public class AnotherClass {}")

    val libraryClass1 = myFixture.addClass("package android.widget; public class TextView {}")
    val libraryClass2 =
      myFixture.addClass(
        "package com.google.android.material; public class FloatingActionButton {}"
      )

    val userDefinedClasses = listOf(userDefinedClass1, userDefinedClass2)
    val nonUserDefinedClasses = listOf(libraryClass1, libraryClass2)

    val dialog =
      ChooseClassDialog(myModule, "Test Dialog", userDefinedClasses, nonUserDefinedClasses)

    // Show the dialog to ensure the UI is initialized
    dialog.show()

    val mainPanel = dialog.createCenterPanel()!!

    val fakeUi = FakeUi(mainPanel)
    fakeUi.layoutAndDispatchEvents()

    // Get the checkbox component from the FakeUi
    val checkbox =
      fakeUi.findComponent(JBCheckBox::class.java) { it.text == "Show library classes" }!!

    // Get the list items' text using FakeUi
    val initialItems = list((mainPanel.getComponent(1) as JBScrollPane).viewport.view!!)

    // Assert that only user-defined classes are initially shown
    assertEquals(userDefinedClasses.size, initialItems.size)
    for (userDefinedClass in userDefinedClasses) {
      assertTrue(initialItems.contains(userDefinedClass.qualifiedName))
    }

    // Simulate click on the checkbox
    checkbox.doClick()
    fakeUi.layoutAndDispatchEvents()

    // Get the updated list items' text
    val allItems = list((mainPanel.getComponent(1) as JBScrollPane).viewport.view!!)

    // Assert that both user-defined and library classes are now shown
    assertEquals(userDefinedClasses.size + nonUserDefinedClasses.size, allItems.size)
    for (userDefinedClass in userDefinedClasses) {
      assertTrue(allItems.contains(userDefinedClass.qualifiedName))
    }
    for (nonUserDefinedClass in nonUserDefinedClasses) {
      assertTrue(allItems.contains(nonUserDefinedClass.qualifiedName))
    }

    // Simulate click on the checkbox again
    checkbox.doClick()
    fakeUi.layoutAndDispatchEvents()

    // Get the final list items' text
    val finalItems = list((mainPanel.getComponent(1) as JBScrollPane).viewport.view!!)

    // Assert that only user-defined classes are shown again
    assertEquals(userDefinedClasses.size, finalItems.size)
    for (userDefinedClass in userDefinedClasses) {
      assertTrue(finalItems.contains(userDefinedClass.qualifiedName))
    }
  }

  private fun list(viewportView: Component) =
    (((viewportView as JBList<*>).model as DefaultListModel).elements().toList() as List<PsiClass>)
      .map { it.qualifiedName }
}
