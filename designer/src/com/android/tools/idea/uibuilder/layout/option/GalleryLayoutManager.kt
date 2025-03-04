/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout.option

import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.uibuilder.layout.padding.GroupPadding
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager

/** [SurfaceLayoutManager] for Gallery mode. */
class GalleryLayoutManager(
  padding: GroupPadding,
  transform: (Collection<PositionableContent>) -> List<PositionableGroup>,
) : GroupedGridSurfaceLayoutManager(padding, transform)
