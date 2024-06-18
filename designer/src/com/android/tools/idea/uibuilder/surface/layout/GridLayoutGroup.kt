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
package com.android.tools.idea.uibuilder.surface.layout

/** Layout of components. */
class GridLayoutGroup(val header: PositionableContent?, val rows: List<List<PositionableContent>>)

/**
 * @return A list with all the content in the [GridLayoutGroup] without considering the rows,
 *   returns an empty list otherwise.
 */
fun GridLayoutGroup?.content(): List<PositionableContent> = this?.rows?.flatten() ?: emptyList()
