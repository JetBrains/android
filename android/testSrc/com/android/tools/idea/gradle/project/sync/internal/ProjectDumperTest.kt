// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.project.sync.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ProjectDumperTest {
  private val SAMPLES: Map<String, String> = mutableMapOf(
    Pair(
      "<GRADLE>/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/<KOTLIN_VERSION>/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/kotlin-stdlib-common-<KOTLIN_VERSION>.jar",
      "<M2>/org/jetbrains/kotlin/kotlin-stdlib-common/<KOTLIN_VERSION>/kotlin-stdlib-common-<KOTLIN_VERSION>.jar"
    )
  )


  @Test
  fun testConvertToMavenPath() {
    val dumper = ProjectDumper(offlineRepos = emptyList(), androidSdk = File("/nowhere"))
    for (entry in SAMPLES) {
      assertEquals(dumper.convertToMaskedMavenPath(entry.key), entry.value)
    }
  }
}