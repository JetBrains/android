/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.cliserver

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(JUnit4::class)
class CliIntegrationServiceTest {

  @Test
  fun testInvokeHandler_withNoProjectSpecified_oneOpen() {
    val project = mock(Project::class.java)
    `when`(project.name).thenReturn("testProject")

    var handled = false
    val handler =
      object : CliActionHandler {
        override val type = 1

        override fun handle(project: Project, request: ByteArray): ByteArray {
          handled = true
          return ByteArray(0)
        }
      }

    // TODO: android-merge; upstream writes `commandRequest { this.project = "" }`; the generated Kotlin DSL builders are
    //  invisible here because the studio-platform jar declares no kotlin_module for com.android.cliserver.
    val request = CommandRequest.newBuilder().setProject("").build()

    val response = handler.invokeHandler(request, arrayOf(project))
    assertThat(response.hasError()).isFalse()
    assertThat(handled).isTrue()
  }

  @Test
  fun testInvokeHandler_withSpecificProject() {
    val project = mock(Project::class.java)
    `when`(project.name).thenReturn("testProject")

    val handler =
      object : CliActionHandler {
        override val type = 1

        override fun handle(project: Project, request: ByteArray): ByteArray {
          return ByteArray(0)
        }
      }

    // TODO: android-merge; upstream writes `commandRequest { this.project = "non-existent" }`; the generated Kotlin DSL
    //  builders are invisible here because the studio-platform jar declares no kotlin_module for com.android.cliserver.
    val request = CommandRequest.newBuilder().setProject("non-existent").build()

    val response = handler.invokeHandler(request, arrayOf(project))
    assertThat(response.hasError()).isTrue()
    assertThat(response.error.message).contains("No project with name non-existent")
  }

  @Test
  fun testInvokeHandler_withNoProjectSpecified_multipleOpen() {
    val project1 = mock(Project::class.java)
    val project2 = mock(Project::class.java)

    val handler =
      object : CliActionHandler {
        override val type = 1

        override fun handle(project: Project, request: ByteArray): ByteArray {
          return ByteArray(0)
        }
      }

    // TODO: android-merge; upstream writes `commandRequest { this.project = "" }`; the generated Kotlin DSL builders are
    //  invisible here because the studio-platform jar declares no kotlin_module for com.android.cliserver.
    val request = CommandRequest.newBuilder().setProject("").build()

    val response = handler.invokeHandler(request, arrayOf(project1, project2))
    assertThat(response.hasError()).isTrue()
    assertThat(response.error.message).contains("There are multiple open projects")
  }
}
