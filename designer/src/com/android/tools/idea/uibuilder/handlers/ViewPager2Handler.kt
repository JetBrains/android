/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_VIEWPAGER2
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.google.common.collect.ImmutableList

class ViewPager2Handler : ViewGroupHandler() {
  override fun getInspectorProperties(): List<String> = ImmutableList.of(ATTR_VISIBILITY)

  override fun getGradleCoordinateId(tagName: String) = ANDROIDX_VIEWPAGER2
}
