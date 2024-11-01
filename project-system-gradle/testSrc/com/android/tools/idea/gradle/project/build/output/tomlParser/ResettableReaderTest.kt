/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output.tomlParser

import com.android.tools.idea.gradle.project.build.output.TestBuildOutputInstantReader
import com.google.common.truth.Truth
import org.junit.Test

class ResettableReaderTest {

  private val sampleText = """
    foo
    bar
    baz
  """.trimIndent()

  @Test
  fun testReset(){
   val reader = ResettableReader(TestBuildOutputInstantReader(sampleText))
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    reader.resetPosition()
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
  }

  @Test
  fun testReset2(){
    val internalReader = TestBuildOutputInstantReader(sampleText)
    Truth.assertThat(internalReader.readLine()).isEqualTo("foo")
    val reader = ResettableReader(internalReader)
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    Truth.assertThat(reader.readLine()).isEqualTo("baz")
    reader.resetPosition()
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
  }

  @Test
  fun testPushBack(){
    val reader = ResettableReader(TestBuildOutputInstantReader(sampleText))
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    reader.pushBack()
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
  }

  @Test
  fun testPushBack2(){
    val reader = ResettableReader(TestBuildOutputInstantReader(sampleText))
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    reader.pushBack(2)
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
  }

  @Test
  fun testResetAfterPushBack(){
    val reader = ResettableReader(TestBuildOutputInstantReader(sampleText))
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    reader.pushBack()
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    reader.resetPosition()
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
  }

  @Test
  fun testResetAfterReachingEnd(){
    val reader = ResettableReader(TestBuildOutputInstantReader(sampleText))
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
    Truth.assertThat(reader.readLine()).isEqualTo("bar")
    Truth.assertThat(reader.readLine()).isEqualTo("baz")
    Truth.assertThat(reader.readLine()).isNull()
    reader.resetPosition()
    Truth.assertThat(reader.readLine()).isEqualTo("foo")
  }

}