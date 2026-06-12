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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.daemon.analysis.PsMissingBuildTypeFallbackQuickFix
import com.android.tools.idea.gradle.structure.daemon.analysis.PsMissingBuildTypeQuickFix
import com.android.tools.idea.gradle.structure.daemon.analysis.PsMissingFlavorDimensionQuickFix
import com.android.tools.idea.gradle.structure.daemon.analysis.PsMissingProductFlavorFallbackQuickFix
import com.android.tools.idea.gradle.structure.daemon.analysis.PsMissingProductFlavorQuickFix
import com.android.tools.idea.gradle.structure.quickfix.PsDependencyConfigurationQuickFixPath
import com.android.tools.idea.gradle.structure.quickfix.PsDependencyKind
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyPlusQuickFixPath
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath
import com.android.tools.idea.gradle.structure.quickfix.SdkIndexLinkQuickFix
import com.android.tools.idea.gradle.structure.quickfix.SdkIndexLinkQuickFixNoLog
import com.google.common.truth.Truth
import org.junit.Test

/** Tests for [PsQuickFix] serialization */
class PsQuickFixSerializationTest {
  data class TestQuickFix(val arg1: String, val arg2: String) : PsQuickFix {
    override val text: String = ""

    override fun execute(context: PsContext) {}

    override fun serialize(): String = "TEST|${PsQuickFix.escape(arg1)}|${PsQuickFix.escape(arg2)}"

    companion object {
      init {
        PsQuickFix.registerDeserializer("TEST", ::deserialize)
      }

      fun deserialize(args: List<String>): TestQuickFix {
        if (args.size != 2) throw IllegalArgumentException("Expected 2 args")
        return TestQuickFix(args[0], args[1])
      }
    }
  }

  @Test
  fun testEscape() {
    Truth.assertThat(PsQuickFix.escape("")).isEqualTo("")
    Truth.assertThat(PsQuickFix.escape("abc")).isEqualTo("abc")
    Truth.assertThat(PsQuickFix.escape("a|b")).isEqualTo("a\\|b")
    Truth.assertThat(PsQuickFix.escape("a\\b")).isEqualTo("a\\\\b")
    Truth.assertThat(PsQuickFix.escape("|")).isEqualTo("\\|")
    Truth.assertThat(PsQuickFix.escape("\\")).isEqualTo("\\\\")
    Truth.assertThat(PsQuickFix.escape("\\|")).isEqualTo("\\\\\\|")
    Truth.assertThat(PsQuickFix.escape("||\\")).isEqualTo("\\|\\|\\\\")
  }

  @Test
  fun testSplitEscaped() {
    Truth.assertThat(PsQuickFix.splitEscaped("")).isEqualTo(listOf(""))
    Truth.assertThat(PsQuickFix.splitEscaped("abc")).isEqualTo(listOf("abc"))
    Truth.assertThat(PsQuickFix.splitEscaped("a|b")).isEqualTo(listOf("a", "b"))
    Truth.assertThat(PsQuickFix.splitEscaped("a\\b")).isEqualTo(listOf("ab"))
    Truth.assertThat(PsQuickFix.splitEscaped("|")).isEqualTo(listOf("", ""))
    Truth.assertThat(PsQuickFix.splitEscaped("\\")).isEqualTo(listOf("\\"))
    Truth.assertThat(PsQuickFix.splitEscaped("\\|")).isEqualTo(listOf("|"))
    Truth.assertThat(PsQuickFix.splitEscaped("||\\")).isEqualTo(listOf("", "", "\\"))
  }

  @Test
  fun testEscapeSplitEscaped() {
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape(""))).isEqualTo(listOf(""))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("abc"))).isEqualTo(listOf("abc"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("a|b"))).isEqualTo(listOf("a|b"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("a\\b"))).isEqualTo(listOf("a\\b"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("a\\|b"))).isEqualTo(listOf("a\\|b"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("|"))).isEqualTo(listOf("|"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("\\"))).isEqualTo(listOf("\\"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("\\|"))).isEqualTo(listOf("\\|"))
    Truth.assertThat(PsQuickFix.splitEscaped(PsQuickFix.escape("||\\"))).isEqualTo(listOf("||\\"))
  }

  @Test
  fun testDeserializeRoundTripWithSpecialChars() {
    val testCases =
      listOf(
        TestQuickFix("a", "b"),
        TestQuickFix("a|b", "c"),
        TestQuickFix("a\\b", "c"),
        TestQuickFix("a\\|b", "c"),
        TestQuickFix("", "c"),
        TestQuickFix("a", ""),
        TestQuickFix("a", "b|c\\d"),
        TestQuickFix("\\", "|"),
      )

    for (original in testCases) {
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testDeserializeInvalid() {
    Truth.assertThat(PsQuickFix.deserialize("INVALID|a|b")).isInstanceOf(PsQuickFix.NoOpPsQuickFix::class.java)
    Truth.assertThat(PsQuickFix.deserialize("TEST|a")).isInstanceOf(PsQuickFix.NoOpPsQuickFix::class.java) // Not enough args
  }

  @Test
  fun testPsMissingBuildTypeQuickFix() {
    val testCases =
      listOf(
        PsMissingBuildTypeQuickFix("app", "debug"),
        PsMissingBuildTypeQuickFix("lib|module", "release\\test"),
        PsMissingBuildTypeQuickFix("", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsMissingBuildTypeFallbackQuickFix() {
    val testCases =
      listOf(
        PsMissingBuildTypeFallbackQuickFix("app", "debug"),
        PsMissingBuildTypeFallbackQuickFix("lib|module", "release\\test"),
        PsMissingBuildTypeFallbackQuickFix("", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsMissingFlavorDimensionQuickFix() {
    val testCases =
      listOf(
        PsMissingFlavorDimensionQuickFix("app", "dim1"),
        PsMissingFlavorDimensionQuickFix("lib|module", "dim\\test"),
        PsMissingFlavorDimensionQuickFix("", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsMissingProductFlavorQuickFix() {
    val testCases =
      listOf(
        PsMissingProductFlavorQuickFix("app", "dim1", "flavorA"),
        PsMissingProductFlavorQuickFix("lib|module", "dim\\test", "flavorB|C"),
        PsMissingProductFlavorQuickFix("", "", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsMissingProductFlavorFallbackQuickFix() {
    val testCases =
      listOf(
        PsMissingProductFlavorFallbackQuickFix("app", "dim1", "flavorA"),
        PsMissingProductFlavorFallbackQuickFix("lib|module", "dim\\test", "flavorB|C"),
        PsMissingProductFlavorFallbackQuickFix("", "", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsDependencyConfigurationQuickFixPath() {
    val testCases =
      listOf(
        PsDependencyConfigurationQuickFixPath("app", PsDependencyKind.LIBRARY, "key1", "oldConfig", "newConfig"),
        PsDependencyConfigurationQuickFixPath("app", PsDependencyKind.MODULE, "key|2", "old\\C", "new|C"),
        PsDependencyConfigurationQuickFixPath("", PsDependencyKind.UNKNOWN, "", "", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsLibraryDependencyPlusQuickFixPath() {
    val testCases =
      listOf(
        PsLibraryDependencyPlusQuickFixPath("app", "group1", "name1", "config1"),
        PsLibraryDependencyPlusQuickFixPath("lib|a", null, "name|a", "config\\a"),
        PsLibraryDependencyPlusQuickFixPath("", null, "", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testPsLibraryDependencyVersionQuickFixPath() {
    val testCases =
      listOf(
        PsLibraryDependencyVersionQuickFixPath("app", "dep1", "config1", "1.1", null, false, false),
        PsLibraryDependencyVersionQuickFixPath("app", "dep1", "config1", "1.1", true, true, false),
        PsLibraryDependencyVersionQuickFixPath("app", "dep1", "config1", "1.1", false, false, true),
        PsLibraryDependencyVersionQuickFixPath("lib|a", "dep|2", "cfg\\2", "2.0", null, false, false),
        PsLibraryDependencyVersionQuickFixPath("", "", "", "", null, false, false),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testSdkIndexLinkQuickFixNoLog() {
    val testCases =
      listOf(SdkIndexLinkQuickFixNoLog("text1", "url1"), SdkIndexLinkQuickFixNoLog("text|2", "url\\2"), SdkIndexLinkQuickFixNoLog("", ""))
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }

  @Test
  fun testSdkIndexLinkQuickFix() {
    val testCases =
      listOf(
        SdkIndexLinkQuickFix("text1", "url1", "group1", "art1", "ver1"),
        SdkIndexLinkQuickFix("text|2", "url\\2", "g|2", "a\\2", "v|\\2"),
        SdkIndexLinkQuickFix("", "", "", "", ""),
      )
    testCases.forEach { original ->
      val serialized = original.serialize()
      val deserialized = PsQuickFix.deserialize(serialized)
      Truth.assertThat(deserialized).isEqualTo(original)
    }
  }
}
