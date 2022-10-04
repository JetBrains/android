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
package com.android.tools.idea.gradle.project.sync.internal

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProjectDumperTest {

  @Test
  fun testJavaVersionMask() {
    val samples11 = listOf("jbr-11", "corretto-11")
    val samples = listOf("jbr-17", "corretto-17")
    fun ProjectDumper.test(src: String) = src.replaceJdkName()

    val dumper = ProjectDumper(offlineRepos = emptyList(), androidSdk = File("/nowhere"))
    for (sample in samples11) {
      assertEquals("<JDK_NAME-11>", dumper.test(sample))
    }
    for (sample in samples) {
      assertEquals("<JDK_NAME>", dumper.test(sample))
    }
  }

  @Test
  fun testJDKVersionMask() {
    val samples11 = listOf("JetBrains Runtime version 11.0.8", "Amazon Corretto version 11.0.8")
    val samples = listOf("JetBrains Runtime version 17.0.8", "Amazon Corretto version 17.0.8")
    fun ProjectDumper.test(src: String) = src.replaceJdkVersion()

    val dumper = ProjectDumper(offlineRepos = emptyList(), androidSdk = File("/nowhere"))
    for (sample in samples11) {
      assertEquals("<JDK_VERSION-11>", dumper.test(sample))
    }
    for (sample in samples) {
      assertEquals("<JDK_VERSION>", dumper.test(sample))
    }
  }
}
