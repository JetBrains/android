/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_PARENT_TAG
import com.android.SdkConstants.ATTR_SHOW_IN
import com.android.SdkConstants.TOOLS_NS_NAME_PREFIX
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler
import com.google.common.collect.ImmutableList

/** Handler for the <merge> tag */
class MergeHandler : FrameLayoutHandler() {

  override fun getInspectorProperties(): List<String> {
    return ImmutableList.of(
      TOOLS_NS_NAME_PREFIX + ATTR_SHOW_IN,
      TOOLS_NS_NAME_PREFIX + ATTR_PARENT_TAG
    )
  }

  override fun getTitle(tagName: String): String {
    return "<merge>"
  }

  override fun getTitle(component: NlComponent): String {
    return "<merge>"
  }
}
