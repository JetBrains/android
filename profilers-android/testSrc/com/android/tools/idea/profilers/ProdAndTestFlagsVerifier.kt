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
package com.android.tools.idea.profilers

import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FeatureConfig
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Method

/**
 * This test make sure that our flags in prod and test environment are consistent.
 * Any divergence should be documented below with a tracking bug associated.
 */
@RunWith(Parameterized::class)
class ProdAndTestFlagsVerifier(val method: Method, val name: String) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "FeatureConfig.{1}()")
    fun data() : Collection<Array<Any>> {
      return FeatureConfig::class.java.declaredMethods.map { arrayOf(it, it.name) }.toList()
    }

    // After a flag is introduced but before it's enabled in the stable releases,
    // the production and testing environment may or may not diverge depending on which
    // branch the test is running on. For example, it's possible that a flag is false in
    // the testing environment, true on the main branch (canary releases), but false on
    // release branches (for beta, RC, stable releases).
    val KNOWN_POSSIBLE_DIVERGENCES = mapOf("isTaskTitleV2Enabled" to "b/410089372")
  }

  @Test
  fun checkTestDefaultValues() {
    val prodServicesConfig = IntellijProfilerServices.FeatureConfigProd()
    val prodValue = method.invoke(prodServicesConfig)

    val testServicesConfig = FakeIdeProfilerServices().featureConfig
    val testValue = method.invoke(testServicesConfig)

    if (prodValue != testValue && !KNOWN_POSSIBLE_DIVERGENCES.containsKey(name)) {
      Assert.fail("Value for FeatureConfig.$name() is $prodValue in Prod but $testValue for Test.")
    }
  }
}

/**
 * This test class runs once and make sure our {@code KNOWN_POSSIBLE_DIVERGENCES} registry isn't stale with entries that doesn't exist anymore.
 */
class VerifyKnownDivergencesUpdatedTest {
  @Test
  fun makeSureAllKnownDivergencesExists() {
    val allMethodsInFeatureConfig = FeatureConfig::class.java.declaredMethods.map { it.name }.toSet()
    val missingMethodsFromKnownDivergences = ProdAndTestFlagsVerifier.KNOWN_POSSIBLE_DIVERGENCES.keys.minus(allMethodsInFeatureConfig)
    if (missingMethodsFromKnownDivergences.isNotEmpty()) {
      Assert.fail("The following methods are listed in KNOWN_POSSIBLE_DIVERGENCES but doesn't exist in FeatureConfig interface: " +
                  "${missingMethodsFromKnownDivergences}. " +
                  "Make sure you removed the entry from KNOWN_POSSIBLE_DIVERGENCES if you removed/cleaned the flag.")
    }
  }
}