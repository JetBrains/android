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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class AllDestinationsEditorTest : NavTestCase() {
  fun testDestinations() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1", label = "fragment1") {
          action("a1", destination = "subnav1")
        }
        activity("activity1")
        navigation("subnav1") {
          fragment("f2", label = "fragment2")
          fragment("f3")
        }
        navigation("subnav2") {
          fragment("f4") {
            action("a2", destination = "fragment1")
          }
        }
      }
    }
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("a1")))

    EnumEditorFixture.create(::AllDestinationsEditor).use {
      it.setProperty(property)
        .showPopup()
        .expectChoices(
          "none", null,
          "root", "@+id/root",
          "fragment1 (f1)", "@+id/f1",
          "activity1", "@+id/activity1",
          "subnav1", "@+id/subnav1",
          "fragment2 (f2)", "@+id/f2",
          "f3", "@+id/f3",
          "subnav2", "@+id/subnav2",
          "f4", "@+id/f4"
        )
    }

    `when`(property.components).thenReturn(listOf(model.find("a2")))

    EnumEditorFixture.create(::AllDestinationsEditor).use {
      it.setProperty(property)
        .showPopup()
        .expectChoices(
          "none", null,
          "root", "@+id/root",
          "fragment1 (f1)", "@+id/f1",
          "activity1", "@+id/activity1",
          "subnav1", "@+id/subnav1",
          "fragment2 (f2)", "@+id/f2",
          "f3", "@+id/f3",
          "subnav2", "@+id/subnav2",
          "f4", "@+id/f4"
        )
    }
  }
}