/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.menu

import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_MENU
import com.android.tools.idea.common.model.NlComponent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MenuHandlerTest {

  @Test
  fun testMenuCannotBeAddedToMenu() {
    val menu1: NlComponent =
      mock<NlComponent>().apply { whenever(this.tagName).thenReturn(TAG_MENU) }
    val menu2: NlComponent =
      mock<NlComponent>().apply { whenever(this.tagName).thenReturn(TAG_MENU) }
    val item: NlComponent =
      mock<NlComponent>().apply { whenever(this.tagName).thenReturn(TAG_ITEM) }
    val handler = MenuHandler()
    assertThat(handler.acceptsChild(menu1, menu2)).isFalse()
    assertThat(handler.acceptsChild(menu2, menu1)).isFalse()
    assertThat(handler.acceptsChild(menu1, item)).isTrue()
    assertThat(handler.acceptsChild(menu2, item)).isTrue()
  }
}
