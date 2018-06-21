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
package com.android.tools.idea.gradle.structure.daemon.analysis

data class PsMessageScope(val buildType: String, val productFlavors: List<String> = listOf(), val artifact: String = "")
data class PsMessageAggregatedScope(val buildType: String?, val productFlavors: List<String?> = listOf(), val scope: String? = "") {
  constructor (from: PsMessageScope) : this(from.buildType, from.productFlavors, from.artifact)

  fun withFlavor(flavor: String?, at: Int) = copy(productFlavors = productFlavors.toMutableList().also { it[at] = flavor })
}

/**
 * A best effort issue scope aggregator aiming to reduce the number of variations of the same issue reported to the user.
 *
 * Example: an issue reported for both debug and release build types but only for paid product flavor should be reported to the user
 *          as affecting paidImplementation.
 *
 * It does not guarantee the smallest set of scopes to be returned, however.
 */
class PsMessageScopeAggregator(
  private val allBuildTypes: Set<String>,
  private val allProductFlavors: List<Set<String>>
) {
  fun aggregate(messageScopes: Set<PsMessageScope>): Set<PsMessageAggregatedScope> {
    assertCorrectNumberOfDimensions(messageScopes)

    val tailsByBuildType = messageScopes
      .groupBy({ it.buildType }, { PsMessageAggregatedScope(it).copy(buildType = null) })
      .mapValues { (_, value) -> value.toSet() }

    val commonTails = allBuildTypes
      .map { tailsByBuildType[it].orEmpty() }
      .reduce { acc, it -> acc.intersect(it) }

    val aggregatedTails = aggregateProductFlavors(commonTails)
    return tailsByBuildType
             .map { (buildType, items) ->
               aggregateProductFlavors(items - commonTails).map { it.copy(buildType = buildType) }.toSet()
             }
             .reduce { acc, it -> acc + it } + aggregatedTails
  }

  private fun aggregateProductFlavors(issueScopes: Set<PsMessageAggregatedScope>,
                                      firstIndex: Int = 0,
                                      collapsed: Int = 0): Set<PsMessageAggregatedScope> {
    if (firstIndex == allProductFlavors.size) {
      return issueScopes
    }
    val nonCollapsed = firstIndex - collapsed
    val tailsByProductFlavor = issueScopes
      .groupBy({ it.productFlavors[firstIndex] }, { it.withFlavor(flavor = null, at = firstIndex) })
      .mapValues { (_, value) -> value.toSet() }

    val commonTails = allProductFlavors[firstIndex]
      .map { tailsByProductFlavor[it].orEmpty() }
      .reduce { acc, it -> it.intersect(acc) }

    val aggregatedTails = aggregateProductFlavors(commonTails, firstIndex + 1, collapsed + 1)
      .takeIf {
        // Discard any tails not forming a single or all dimension combination.
        nonCollapsed == 0 || it.all {
          (it.productFlavors.count { it != null } + nonCollapsed).let { nc -> nc <= 1 || nc == allProductFlavors.size }
        }
      }
    // This is a simplifications. We reject all aggregated tails if any of them is bad.
    val acceptedCommonTails = commonTails.takeUnless { aggregatedTails == null }.orEmpty()

    return tailsByProductFlavor.takeUnless { it.isEmpty() }
             ?.map { (productFlavor, items) ->
               aggregateProductFlavors(items - acceptedCommonTails, firstIndex + 1, collapsed)
                 .map { it.withFlavor(productFlavor, at = firstIndex) }.toSet()
             }
             ?.reduce { acc, it -> acc + it }.orEmpty() + aggregatedTails.orEmpty()
  }

  private fun assertCorrectNumberOfDimensions(messages: Set<PsMessageScope>) = messages.forEach {
    if (allProductFlavors.size != it.productFlavors.size) {
      throw IllegalArgumentException("productFlavors.size must be ${allProductFlavors.size}")
    }
  }
}