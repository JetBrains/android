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
import com.android.flags.StaticFlagDefault
import com.android.tools.idea.IdeChannel
import com.google.common.truth.Truth
import org.junit.Test
import java.lang.reflect.Modifier

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
  fun validateMigrationFile() {
    val booleanFlags = getFlags()

    // we are going to create the content of the file automatically, and then compare to the
    // checked-in file.
    // we want to ignore

    val computedContent = buildList {
      for (flag in booleanFlags) {
        // the goal of the future storage is to only declare non-false flags in the file,
        // so we can skip flags that have a static false default value.
        if (flag.default !is StaticFlagDefault<Boolean> || flag.default.get()) {
          add(flag.convertToString())
        } else {
          // let's make sure we're not ignoring unexpected flags
          if (flag.default !is StaticFlagDefault<Boolean>) {
            throw RuntimeException("Unexpected type of FlagDefault (${flag.default.javaClass}) for flag: ${flag.id}")
          }
        }
      }
    }.sorted()

    // make sure the content of the file matches this exactly, in order
    Truth.assertThat(readFile()).containsExactlyElementsIn(computedContent).inOrder()
  }

  private fun BooleanFlag.staticValue(): Boolean? {
    val staticFlagDefault = default as? StaticFlagDefault<Boolean> ?: return null

    return staticFlagDefault.get()
  }

  private fun BooleanFlag.channelValue(): IdeChannel.Channel? {
    val channelDefault = default as? ChannelDefault ?: return null

    return channelDefault.leastStableChannel
  }

  private fun BooleanFlag.convertToString(): String {
    val staticValue = staticValue()
    if (staticValue == true) {
      return "$id=true"
    }

    val channelValue = channelValue()
    if (channelValue != null) {
      return "$id=$channelValue"
    }

    throw RuntimeException("Unexpected type of FlagDefault (${default.javaClass}) for flag: ${id}")
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

  private fun getFlags(): List<BooleanFlag> {
    val clazz = StudioFlags::class.java
    val fields = clazz.declaredFields

    return buildList {
      for (field in fields) {
        if (Modifier.isStatic(field.modifiers) && field.type == Flag::class.java) {
          val instance = field.get(null) as Flag<*>
          if (instance::class.java == BooleanFlag::class.java) {
            instance as BooleanFlag
            add(instance)
          }
        }
      }
    }
  }
}