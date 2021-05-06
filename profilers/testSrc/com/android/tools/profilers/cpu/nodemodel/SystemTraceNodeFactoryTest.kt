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
package com.android.tools.profilers.cpu.nodemodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemTraceNodeFactoryTest {

  @Test
  fun `name without numbers`() {
    val name = "MyNoNumber Name"
    val factory = SystemTraceNodeFactory()

    assertThat(name).isEqualTo(factory.getNode(name).fullName)
  }

  @Test
  fun `name with numbers in the end`() {
    val name = "Name Ends Number 1234"
    val expected = "Name Ends Number ###"
    val factory = SystemTraceNodeFactory()

    assertThat(expected).isEqualTo(factory.getNode(name).fullName)
  }

  @Test
  fun `name with # and numbers in the end`() {
    val name = "Choreographer#doFrame 1234"
    val expected = "Choreographer#doFrame ###"
    val factory = SystemTraceNodeFactory()

    assertThat(expected).isEqualTo(factory.getNode(name).fullName)
  }

  @Test
  fun `name with numbers in the middle`() {
    val name = "Name 1 number"
    val factory = SystemTraceNodeFactory()

    assertThat(name).isEqualTo(factory.getNode(name).fullName)
  }

  @Test
  fun `two nodes same raw name`() {
    val factory = SystemTraceNodeFactory()

    val node1 = factory.getNode("MyNoNumber Name")
    val node2 = factory.getNode("MyNoNumber Name")

    assertThat(node1).isSameAs(node2)
  }

  @Test
  fun `two nodes same canonical came`() {
    val factory = SystemTraceNodeFactory()

    val node1 = factory.getNode("Name Ends Number 1234")
    val node2 = factory.getNode("Name Ends Number 987654")

    assertThat(node1).isSameAs(node2)
  }

  @Test
  fun `two nodes different entities`() {
    val factory = SystemTraceNodeFactory()

    val node1 = factory.getNode("Name Ends Number")
    val node2 = factory.getNode("Name Ends Number 1234")

    assertThat(node1).isNotSameAs(node2)
  }

}