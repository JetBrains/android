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
package com.android.tools.idea.naveditor.model

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.google.common.truth.Truth.assertThat

/**
 * Tests for [NlModel] as used in the navigation editor
 */
class NavNlModelTest : NavTestCase() {

  fun testAddChild() {
    val treeDumper = NlTreeDumper()
    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val model = modelBuilder.build()

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "    NlComponent{tag=<fragment>, instance=2}",
        treeDumper.toTree(model.components))

    // Add child
    val parent = modelBuilder.findByPath(NavTestCase.TAG_NAVIGATION)!! as NavModelBuilderUtil.NavigationComponentDescriptor
    assertThat(parent).isNotNull()
    parent.action("action", "fragment1")
    modelBuilder.updateModel(model)

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "    NlComponent{tag=<fragment>, instance=2}\n" +
        "    NlComponent{tag=<action>, instance=3}",
        treeDumper.toTree(model.components))
  }
}
