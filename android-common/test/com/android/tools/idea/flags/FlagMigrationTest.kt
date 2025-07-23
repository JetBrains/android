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

import com.android.tools.idea.flags.overrides.ConfigurationOverrides
import com.android.utils.associateNotNull
import com.google.common.truth.Truth
import org.junit.Test

/**
 * This test is temporary and meant to help with an upcoming flag
 * change.
 *
 * We want to manage BooleanFlag's default values via a text file
 * and that will require moving the default values out of the instance
 * creation constructor calls into the text file. At the same time we will
 * tweak how values are defined, and this can introduce bugs.
 *
 * In order to make sure no mistake are made during this mostly manual
 * process, we first create a file that contains the current values
 * of these flags and check it in. In order to guarantee this file
 * is valid, we'll write a test that checks its content.
 */
class FlagMigrationTest {

  @Test
  fun validateNewStorageFile() {
    val migrationFlags = readFile()
      .associateNotNull {
        ConfigurationOverrides.parseLine(it, throwOnInvalidValue = true)
      }
    val newFlags = FeatureValidationTest.readFile()
      .filter { !it.startsWith("#") }
      .associateNotNull {
        ConfigurationOverrides.parseLine(it, removeDate = true, throwOnInvalidValue = true)
      }

    // first compare only the ids
    Truth.assertThat(newFlags.keys).containsExactlyElementsIn(migrationFlags.keys)

    // because the files use different values (IdeChannel vs IdeConfiguration) we need a conversion
    // mechanism to be able to compare them
    for (key in newFlags.keys) {
      val oldValue = migrationFlags[key]!!
      val newValue = newFlags[key]!!

      val expected = when (oldValue) {
        "true", "STABLE" -> "STABLE"
        "CANARY" -> "PREVIEW"
        "DEV" -> "INTERNAL"
        "NIGHTLY" -> "NIGHTLY"
        else -> throw RuntimeException("Unexpected value in flag_migration.txt: $oldValue")
      }

      Truth.assertWithMessage("Value of $key").that(newValue).isEqualTo(expected)
    }
  }

  private fun readFile(): List<String> {
    val flagsStream = FlagMigrationTest::class.java.getResourceAsStream("/file_migration.txt")

    Truth.assertWithMessage("Check loading of flag_migration.txt").that(flagsStream).isNotNull()
    flagsStream!! // to please kotlin compiler

    return flagsStream.use { stream ->
      stream.reader(Charsets.UTF_8).use { reader ->
        reader.readLines().filter { !it.startsWith("#") }
      }
    }
  }
}