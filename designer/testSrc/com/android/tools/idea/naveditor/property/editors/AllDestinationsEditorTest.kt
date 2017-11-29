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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class AllDestinationsEditorTest : NavigationTestCase() {
  fun testDestinations() {
    val model = model("nav.xml",
        rootComponent("root")
            .unboundedChildren(
                fragmentComponent("f1")
                    .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL, "fragment1")
                    .unboundedChildren(actionComponent("a1").withDestinationAttribute("subnav1")),
                activityComponent("activity1"),
                navigationComponent("subnav1")
                    .unboundedChildren(
                        fragmentComponent("f2")
                            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL, "fragment2"),
                        fragmentComponent("f3")),
                navigationComponent("subnav2")
                    .unboundedChildren(
                        fragmentComponent("f4")
                            .unboundedChildren(actionComponent("a2").withDestinationAttribute("fragment1")))))
        .build()
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("a1")))

    EnumEditorFixture
        .create(::AllDestinationsEditor)
        .setProperty(property)
        .showPopup()
        .expectChoices("none", null,
            "root", "@+id/root",
            "fragment1 (f1)", "@+id/f1",
            "activity1", "@+id/activity1",
            "subnav1", "@+id/subnav1",
            "fragment2 (f2)", "@+id/f2",
            "f3", "@+id/f3",
            "subnav2", "@+id/subnav2",
            "f4", "@+id/f4")

    `when`(property.components).thenReturn(listOf(model.find("a2")))

    EnumEditorFixture
        .create(::AllDestinationsEditor)
        .setProperty(property)
        .showPopup()
        .expectChoices("none", null,
            "root", "@+id/root",
            "fragment1 (f1)", "@+id/f1",
            "activity1", "@+id/activity1",
            "subnav1", "@+id/subnav1",
            "fragment2 (f2)", "@+id/f2",
            "f3", "@+id/f3",
            "subnav2", "@+id/subnav2",
            "f4", "@+id/f4")
  }
}