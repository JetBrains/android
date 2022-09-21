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
package com.android.build.attribution.proto

import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class PairEnumFinderTest(
  private val testParam: TestParam
) {
  data class TestParam(val real: KClass<out Enum<*>>, val converter: EnumConverter<Enum<*>, Enum<*>>)

  @Test
  fun exampleParameterizedTest() {
    testParam.real.java.enumConstants.forEach { realEnum ->
      val proto = testParam.converter.aToB(realEnum)
      val result = testParam.converter.bToA(proto)
      Truth.assertThat(result).isEqualTo(realEnum)
    }
  }

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun myTestParameters() = PairEnumFinder.permissibleConversions.map {
      TestParam(it.key, it.value as EnumConverter<Enum<*>, Enum<*>>)
    }
  }
}
