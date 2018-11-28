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
package com.android.tools.adtui.ptable2.impl

import com.android.tools.adtui.ptable2.item.Group
import com.android.tools.adtui.ptable2.item.Item
import com.android.tools.adtui.ptable2.item.createModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PTableModelImplTest {

  @Test
  fun testDepth() {
    val model = createModel(Item("weight"), Group("weiss", Item("siphon"), Group("extra", Item("some"), Group("more", Item("stuff")))))
    val impl = PTableModelImpl(model)
    assertThat(impl.depth(model.find("weight")!!)).isEqualTo(0)
    assertThat(impl.depth(model.find("weiss")!!)).isEqualTo(0)
    assertThat(impl.depth(model.find("siphon")!!)).isEqualTo(1)
    assertThat(impl.depth(model.find("extra")!!)).isEqualTo(1)
    assertThat(impl.depth(model.find("some")!!)).isEqualTo(2)
    assertThat(impl.depth(model.find("more")!!)).isEqualTo(2)
    assertThat(impl.depth(model.find("stuff")!!)).isEqualTo(3)
  }
}
