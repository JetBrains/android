/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.flags

import com.android.flags.BooleanFlag
import com.android.flags.Flag
import com.android.tools.idea.flags.overrides.FeatureConfigurationProvider
import com.android.tools.idea.flags.overrides.FEATURE_FLAGS_FILE
import com.google.common.truth.Truth
import org.junit.Test
import java.lang.reflect.Modifier
import org.junit.Assert.fail

/**
 * This test validates the content of feature_flags.txt to ensure that we can use it during our release process.
 *
 * This checks the following:
 * - the flag entries are sorted. This is critical to diff between releases
 * - there is no entries in the file that have no match in [StudioFlag]. It's just good hygiene
 * - each flag entry with state "STABLE" has a matching date to ensure we can clear out old flags
 *
 */
class FeatureValidationTest {

  @Test
  fun validate_sorted_entries() {
    val lines = readFile()

    val sortedLines = lines.sorted()

    if (sortedLines != lines) {
      fail("feature_flags.txt is not sorted. Make sure the flag entries are sorted alphabetically")
    }
  }

  @Test
  fun validate_no_obsolete_entries() {
    val flagsFromFile = readFile().map {
      // this will not return null but we need to handle it somehow to please the compiler
      val tokens = FeatureConfigurationProvider.parseLine(it, throwOnInvalidValue = true) ?: return@map null
      tokens.first
    }
    val flagsFromClass = getFields()

    // it is ok to have field with no entries in the files (this is how
    // we have flags with a default value of false)
    // So we want to test that the field list contains all the entries
    // in the file.

    Truth.assertThat(flagsFromClass).containsAllIn(flagsFromFile)
  }

  @Test
  fun validate_stable_has_date() {
    val completeFlagsFromFile = readFile().mapNotNull {
      val tokens = FeatureConfigurationProvider.parseLine(it, removeDate = false, throwOnInvalidValue = true)
                   ?: return@mapNotNull null

      if (!tokens.second.startsWith(FeatureConfiguration.COMPLETE.name)) return@mapNotNull null

      tokens
    }

    for (flag in completeFlagsFromFile) {
      if (flag.second == FeatureConfiguration.COMPLETE.name) {
        fail("Flag with ID '${flag.first}' and value ${FeatureConfiguration.COMPLETE} does not have a date")
      }

      if (!flag.second.matches(COMPLETE_WITH_DATE_REGEX)) {
        fail("Flag with ID '${flag.first}' and value ${FeatureConfiguration.COMPLETE} has a malformed date. Make sure the format is STABLE:YYYY")
      }
    }
  }

  companion object {

    fun readFile(): List<String> {
      val flagsStream = FeatureConfigurationProvider::class.java.getResourceAsStream(FEATURE_FLAGS_FILE)

      Truth.assertWithMessage("Check loading of $FEATURE_FLAGS_FILE").that(flagsStream).isNotNull()
      flagsStream!! // to please kotlin compiler

      return flagsStream.use { stream ->
        stream.reader(Charsets.UTF_8).use { reader ->
          reader.readLines().filter { !it.startsWith("#") }
        }
      }
    }

    private fun getFields(): List<String> {
      val clazz = StudioFlags::class.java
      val fields = clazz.declaredFields

      return buildList {
        for (field in fields) {
          if (Modifier.isStatic(field.modifiers) && field.type == Flag::class.java) {
            val instance = field.get(null) as Flag<*>
            if (instance::class.java == BooleanFlag::class.java) {
              instance as BooleanFlag
              add(instance.id)
            }
          }
        }
      }
    }
  }
}

private val COMPLETE_WITH_DATE_REGEX = Regex("${FeatureConfiguration.COMPLETE}:\\d{4}")
