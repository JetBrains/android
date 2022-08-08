// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.project.sync.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ProjectDumperTest {
  @Test
  fun testConvertToMavenPath() {
    val samples: Map<String, String> = mutableMapOf(
      Pair(
        "<GRADLE>/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/<KOTLIN_VERSION>/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/kotlin-stdlib-common-<KOTLIN_VERSION>.jar",
        "<M2>/org/jetbrains/kotlin/kotlin-stdlib-common/<KOTLIN_VERSION>/kotlin-stdlib-common-<KOTLIN_VERSION>.jar"
      )
    )

    val dumper = ProjectDumper(offlineRepos = emptyList(), androidSdk = File("/nowhere"))
    for (entry in samples) {
      assertEquals(dumper.convertToMaskedMavenPath(entry.key), entry.value)
    }
  }

  @Test
  fun testJavaVersionMask() {
    val samples = listOf("jbr-11", "corretto-11")
    fun ProjectDumper.test(src: String) = src.replaceJavaVersion()

    val dumper = ProjectDumper(offlineRepos = emptyList(), androidSdk = File("/nowhere"))
    for (sample in samples) {
      assertEquals("<JAVA_VERSION>", dumper.test(sample))
    }
  }

  @Test
  fun testJDKVersionMask() {
    val samples = listOf("JetBrains Runtime version 11.0.8", "Amazon Corretto version 11.0.8")
    fun ProjectDumper.test(src: String) = src.replaceJdkVersion()

    val dumper = ProjectDumper(offlineRepos = emptyList(), androidSdk = File("/nowhere"))
    for (sample in samples) {
      assertEquals("<JDK_VERSION>", dumper.test(sample))
    }
  }
}