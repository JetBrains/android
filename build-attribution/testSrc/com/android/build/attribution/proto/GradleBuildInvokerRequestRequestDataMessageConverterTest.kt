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

import com.android.build.attribution.proto.converters.GradleBuildInvokerRequestRequestDataMessageConverter
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

class GradleBuildInvokerRequestRequestDataMessageConverterTest {
  @Test
  fun testRequestData() {
    val requestData = GradleBuildInvoker.Request.RequestData(
      BuildMode.DEFAULT_BUILD_MODE,
      File("rootproject"),
      listOf("task1", "task2"),
      listOf("e1", "e2"),
      listOf("c1", "c2"),
      mapOf(Pair("a", "b"), Pair("c","d")),
      false
    )
    val requestDataMessage = GradleBuildInvokerRequestRequestDataMessageConverter.transform(requestData)
    val resultConverted = GradleBuildInvokerRequestRequestDataMessageConverter.construct(requestDataMessage)
    Truth.assertThat(resultConverted).isEqualTo(requestData)
  }

  @Test
  fun testRequestDataNullMode() {
    val requestData = GradleBuildInvoker.Request.RequestData(
      null,
      File("root-project"),
      emptyList()
    )
    val requestDataMessage = GradleBuildInvokerRequestRequestDataMessageConverter.transform(requestData)
    val resultConverted = GradleBuildInvokerRequestRequestDataMessageConverter.construct(requestDataMessage)
    Truth.assertThat(resultConverted).isEqualTo(requestData)
  }

  @Test
  fun testBuildModeNull() {
    Truth.assertThat(GradleBuildInvokerRequestRequestDataMessageConverter.constructBuildMode(
      GradleBuildInvokerRequestRequestDataMessageConverter.transformBuildMode(null))).isNull()
  }
}