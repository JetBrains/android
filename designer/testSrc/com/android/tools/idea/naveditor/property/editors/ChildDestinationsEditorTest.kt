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

class ChildDestinationsEditorTest : NavTestCase() {
  fun testChildDestinations() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1", label = "fragment1")
        activity("activity1")
        navigation("subnav1") {
          fragment("f2", label = "fragment2")
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
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("root")))

    EnumEditorFixture.create(::ChildDestinationsEditor).use {
      it.setProperty(property)
        .showPopup()
        .expectChoices(
          "none", null,
          "fragment1 (f1)", "@+id/f1",
          "activity1", "@+id/activity1",
          "subnav1", "@+id/subnav1",
          "subnav2", "@+id/subnav2"
        )
    }

    `when`(property.components).thenReturn(listOf(model.find("subnav1")))

    EnumEditorFixture.create(::ChildDestinationsEditor).use {
      it.setProperty(property)
        .showPopup()
        .expectChoices(
          "none", null,
          "fragment2 (f2)", "@+id/f2",
          "f3", "@+id/f3"
        )
    }
  }
}