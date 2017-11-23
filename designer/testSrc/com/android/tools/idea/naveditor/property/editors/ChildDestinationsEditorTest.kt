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

import com.android.SdkConstants
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ChildDestinationsEditorTest : NavigationTestCase() {
  fun testChildDestinations() {
    val model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root")
            .unboundedChildren(
                NavModelBuilderUtil.fragmentComponent("f1")
                    .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME, "fragment1"),
                NavModelBuilderUtil.activityComponent("activity1"),
                NavModelBuilderUtil.navigationComponent("subnav1")
                    .unboundedChildren(
                        NavModelBuilderUtil.fragmentComponent("f2")
                            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME, "fragment2"),
                        NavModelBuilderUtil.fragmentComponent("f3")),
                NavModelBuilderUtil.navigationComponent("subnav2")
                    .unboundedChildren(
                        NavModelBuilderUtil.fragmentComponent("f4"),
                        NavModelBuilderUtil.navigationComponent("subsubnav")
                            .unboundedChildren(
                                NavModelBuilderUtil.fragmentComponent("f5")))))
        .build()
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("root")))

    val editor = VisibleDestinationsEditor()
    editor.property = property

    EnumEditorFixture
        .create(::ChildDestinationsEditor)
        .setProperty(property)
        .showPopup()
        .expectChoices("none", null,
            "fragment1 (f1)", "@+id/f1",
            "activity1 (activity1)", "@+id/activity1",
            "subnav1 (subnav1)", "@+id/subnav1",
            "subnav2 (subnav2)", "@+id/subnav2")

    `when`(property.components).thenReturn(listOf(model.find("subnav1")))
    editor.property = property

    EnumEditorFixture
        .create(::ChildDestinationsEditor)
        .setProperty(property)
        .showPopup()
        .expectChoices("none", null,
            "fragment2 (f2)", "@+id/f2",
            "f3 (f3)", "@+id/f3")
  }
}