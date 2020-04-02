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
package com.android.tools.adtui.model.filter

/**
 * This object can be passed in a recursive computation of filters and then transformed into a
 * {@link FilterResult} at the end by calling {@link #toFilterResult()}.
 *
 * The main purpose is to avoid the creation of too many short lived FilterResult objects when
 * it's possible.
 */
class FilterAccumulator(private val isFilterEnabled: Boolean) {

  private var totalCount: Int = 0
  private var matchCount: Int = 0

  fun increaseTotalCount() { totalCount++ }
  fun increaseMatchCount() { matchCount++ }

  fun toFilterResult() : FilterResult = FilterResult(matchCount, totalCount, isFilterEnabled)
}