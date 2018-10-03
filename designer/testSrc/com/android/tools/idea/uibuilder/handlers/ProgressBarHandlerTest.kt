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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.addChild
import com.android.tools.idea.uibuilder.createNlModelFromTagName
import com.android.tools.idea.uibuilder.getRoot
import com.google.common.truth.Truth
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test

class ProgressBarHandlerTest {

  @get:Rule
  val myRule = AndroidProjectRule.inMemory()

  @Test
  fun getIconFromShortStyleName() {
    val handler = ProgressBarHandler()
    val styleValue = "@style/a"
    Truth.assertThat(handler.getIcon(createProgressBar(styleValue))).isNotNull()
  }

  @Test
  fun getIconFromWithoutStyleSuffix() {
    val handler = ProgressBarHandler()
    val styleValue = "@style/Material.ProgressBar."
    Truth.assertThat(handler.getIcon(createProgressBar(styleValue))).isNotNull()
  }

  @Test
  fun getIconFromEmptyStyle() {
    val handler = ProgressBarHandler()
    val styleValue = "@+id/progressBar"
    Truth.assertThat(handler.getIcon(createProgressBar(styleValue))).isNotNull()
  }

  private fun createProgressBar(styleValue: String): NlComponent {
    val progressBar = createNlModelFromTagName(AndroidFacet.getInstance(myRule.module)!!).getRoot().addChild(progressBarTag(styleValue))
    Truth.assertThat(progressBar.tagName).isEqualTo(SdkConstants.PROGRESS_BAR)
    Truth.assertThat(progressBar.getAttribute(null, "style")).isEqualTo(styleValue)
    return progressBar
  }
}

private fun progressBarTag(styleValue: String): String {
  //language=XML
  return """<ProgressBar
      android:id="@+id/progressBar"
      style="$styleValue"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
  />
  """
}
