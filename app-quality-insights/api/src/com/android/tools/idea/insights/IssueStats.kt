/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.services.firebase.insights.datamodel

/** Stats of an [Issue]. */
data class IssueStats<T : Number>(val topValue: String?, val groups: List<StatsGroup<T>>)

fun <T : Number, R : Number> IssueStats<T>.map(mapper: (T) -> R) =
  IssueStats(
    topValue,
    groups.map { group ->
      StatsGroup(
        group.groupName,
        mapper(group.percentage),
        group.breakdown.map { DataPoint(it.name, mapper(it.percentage)) }
      )
    }
  )

/** A named group of [DataPoint]s. */
data class StatsGroup<T : Number>(
  val groupName: String,
  val percentage: T,
  val breakdown: List<DataPoint<T>>
)

/** Represents a leaf named data point. */
data class DataPoint<T : Number>(val name: String, val percentage: T)
