/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.adblib.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class DevicePropertiesParserTest {
  @JvmField
  @Rule
  var exceptionRule: ExpectedException = ExpectedException.none()

  @Test
  fun parserWorks() {
    // Prepare
    val expected = """# This is some build info
# This is more build info

[ro.product.manufacturer]: [Google]
[ro.product.model]: [Pix3l]
[ro.build.version.release]: [versionX
]
[ro.build.version.release2]: [versionX
test]
[ro.build.version.release3]: [versionX
test1
test2
]
[ro.build.version.sdk]: [29]
    """.trimIndent()

    // Act
    val entries = DevicePropertiesParser().parse(expected.splitToSequence("\n"))

    // Assert
    assertThat(entries.size).isEqualTo(6)
    assertThat(entries[0].name).isEqualTo("ro.product.manufacturer")
    assertThat(entries[0].value).isEqualTo("Google")

    assertThat(entries[1].name).isEqualTo("ro.product.model")
    assertThat(entries[1].value).isEqualTo("Pix3l")

    assertThat(entries[2].name).isEqualTo("ro.build.version.release")
    assertThat(entries[2].value).isEqualTo("versionX\n")

    assertThat(entries[3].name).isEqualTo("ro.build.version.release2")
    assertThat(entries[3].value).isEqualTo("versionX\ntest")

    assertThat(entries[4].name).isEqualTo("ro.build.version.release3")
    assertThat(entries[4].value).isEqualTo("versionX\ntest1\ntest2\n")

    assertThat(entries[5].name).isEqualTo("ro.build.version.sdk")
    assertThat(entries[5].value).isEqualTo("29")
  }
}
