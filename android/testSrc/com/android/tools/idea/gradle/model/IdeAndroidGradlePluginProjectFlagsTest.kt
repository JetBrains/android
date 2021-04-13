/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag
import com.google.common.truth.Truth
import org.junit.Test

class IdeAndroidGradlePluginProjectFlagsTest {

    @Test
    fun testDefaults() {
        val flags = parse()
        Truth.assertThat(flags.applicationRClassConstantIds).isTrue()
        Truth.assertThat(flags.testRClassConstantIds).isTrue()
        Truth.assertThat(flags.transitiveRClasses).isTrue()
    }

    @Test
    fun testLegacyDefaults() {
        val flags = parse()
        Truth.assertThat(flags.applicationRClassConstantIds).isTrue()
        Truth.assertThat(flags.testRClassConstantIds).isTrue()
        Truth.assertThat(flags.transitiveRClasses).isTrue()
    }

    @Test
    fun applicationRClassConstantIds() {
        val flags = parse((BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS to true))
        Truth.assertThat(flags.applicationRClassConstantIds).isTrue()
    }

    @Test
    fun applicationRClassNonConstantIds() {
        val flags = parse((BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS to false))
        Truth.assertThat(flags.applicationRClassConstantIds).isFalse()
    }

    @Test
    fun testRClassConstantIds() {
        val flags = parse((BooleanFlag.TEST_R_CLASS_CONSTANT_IDS to true))
        Truth.assertThat(flags.testRClassConstantIds).isTrue()
    }

    @Test
    fun testRClassNonConstantIds() {
        val flags = parse((BooleanFlag.TEST_R_CLASS_CONSTANT_IDS to false))
        Truth.assertThat(flags.testRClassConstantIds).isFalse()
    }

    @Test
    fun transitiveRClass() {
        val flags = parse((BooleanFlag.TRANSITIVE_R_CLASS to true))
        Truth.assertThat(flags.transitiveRClasses).isTrue()
    }

    @Test
    fun nonTransitiveRClass() {
        val flags = parse(BooleanFlag.TRANSITIVE_R_CLASS to false)
        Truth.assertThat(flags.transitiveRClasses).isFalse()
    }

    private fun parse(vararg flags: Pair<BooleanFlag, Boolean>): IdeAndroidGradlePluginProjectFlags {
      val flagsMap = mapOf(*flags)
      return IdeAndroidGradlePluginProjectFlagsImpl(
        applicationRClassConstantIds = flagsMap[BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS] ?:BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS.legacyDefault,
        testRClassConstantIds = flagsMap[BooleanFlag.TEST_R_CLASS_CONSTANT_IDS] ?: BooleanFlag.TEST_R_CLASS_CONSTANT_IDS.legacyDefault,
        transitiveRClasses = flagsMap[BooleanFlag.TRANSITIVE_R_CLASS] ?: BooleanFlag.TRANSITIVE_R_CLASS.legacyDefault,
        usesCompose = flagsMap[BooleanFlag.JETPACK_COMPOSE] ?: BooleanFlag.JETPACK_COMPOSE.legacyDefault,
        mlModelBindingEnabled = flagsMap[BooleanFlag.ML_MODEL_BINDING] ?: BooleanFlag.ML_MODEL_BINDING.legacyDefault
      )
    }
}