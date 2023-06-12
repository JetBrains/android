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

    val KNOWN_DIVERGENCES = mapOf("isEnergyProfilerEnabled" to "b/162495674")
  }

  @Test
  fun checkTestDefaultValues() {
    val prodServicesConfig = IntellijProfilerServices.FeatureConfigProd()
    val prodValue = method.invoke(prodServicesConfig)

    val testServicesConfig = FakeIdeProfilerServices().featureConfig
    val testValue = method.invoke(testServicesConfig)

    if (prodValue != testValue && !KNOWN_DIVERGENCES.containsKey(name)) {
      Assert.fail("Value for FeatureConfig.$name() is $prodValue in Prod but $testValue for Test.")
    } else if (prodValue == testValue && KNOWN_DIVERGENCES.containsKey(name)) {
      Assert.fail("Value for FeatureConfig.$name() is the same in Prod and tests ($prodValue), but a divergence is being tracked in " +
                  "${KNOWN_DIVERGENCES[name]}. If you fixed it, remove it from the knownDivergences.")
    }
  }
}

/**
 * This test class runs once and make sure our {@code KNOWN_DIVERGENCES} registry isn't stale with entries that doesn't exist anymore.
 */
class VerifyKnownDivergencesUpdatedTest {
  @Test
  fun makeSureAllKnownDivergencesExists() {
    val allMethodsInFeatureConfig = FeatureConfig::class.java.declaredMethods.map { it.name }.toSet()
    val missingMethodsFromKnownDivergences = ProdAndTestFlagsVerifier.KNOWN_DIVERGENCES.keys.minus(allMethodsInFeatureConfig)
    if (missingMethodsFromKnownDivergences.isNotEmpty()) {
      Assert.fail("The following methods are listed in knownDivergences but doesn't exist in FeatureConfig interface: " +
                  "${missingMethodsFromKnownDivergences}. " +
                  "Make sure you removed the entry from knownDivergences if you removed/cleaned the flag.")
    }
  }
}