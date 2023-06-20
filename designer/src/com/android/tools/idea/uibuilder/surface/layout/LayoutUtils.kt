/*
 * Copyright (C) 2020 The Android Open Source Project
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

import java.awt.Insets

/**
 * The minimum scale zoom-to-fit scale value. For now, the legal minimum zoom level is 1%.
 */
internal const val MINIMUM_SCALE = 0.01

/**
 * The unit of scale when calculating the zoom-to-fit scale by calling [GridSurfaceLayoutManager.getFitIntoScale].
 * The recursion stops when the differences of two zoom-to-fit value is smaller than this value.
 * We don't display the zoom level lower than 1% in the zoom panel, so we use 0.01 here.
 */
internal const val SCALE_UNIT = 0.01

/**
 * Max iteration times of the binary search. Iterate 10 times can search 1% to 1024% range which is enough in the most use cases.
 */
internal const val MAX_ITERATION_TIMES = 10

/**
 * Returns the sum of both the top and bottom margins
 */
val Insets.vertical: Int get() = top + bottom

/**
 * Returns the sum of both the left and right margins
 */
val Insets.horizontal: Int get() = left + right