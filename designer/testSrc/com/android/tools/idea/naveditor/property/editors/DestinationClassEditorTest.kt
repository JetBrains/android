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
package com.android.tools.idea.naveditor.property.editors

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DestinationClassEditorTest : NavTestCase() {
  fun testFragment() {
    val model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root")
            .unboundedChildren(
                NavModelBuilderUtil.fragmentComponent("f1"),
                NavModelBuilderUtil.activityComponent("activity1")))
        .build()
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("f1")))

    val editor = DestinationClassEditor()
    editor.property = property

    var choices = EnumEditorFixture
        .create(::DestinationClassEditor)
        .setProperty(property)
        .showPopup()
        .choices

    assertContainsElements(choices,
        "none" displayFor null,
        "android.app.DialogFragment" displayFor "android.app.DialogFragment",
        "android.arch.navigation.NavHostFragment" displayFor "android.arch.navigation.NavHostFragment",
        "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment")
    assertDoesntContain(choices, "mytest.navtest.MainActivity" displayFor "mytest.navtest.MainActivity")

    `when`(property.components).thenReturn(listOf(model.find("activity1")))
    editor.property = property

    choices = EnumEditorFixture
        .create(::DestinationClassEditor)
        .setProperty(property)
        .showPopup()
        .choices

    assertContainsElements(choices,
        "none" displayFor null,
        "android.app.ListActivity" displayFor "android.app.ListActivity",
        "mytest.navtest.MainActivity" displayFor "mytest.navtest.MainActivity")
    assertDoesntContain(choices, "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment")
  }

  private infix fun String.displayFor(value: String?) = ValueWithDisplayString(this, value)
}