/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.TestUtils
import org.junit.rules.ExternalResource
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class CollectDaemonLogsRule : ExternalResource() {
  override fun after() {
    val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
    val testOutputDir = TestUtils.getTestOutputDir()
    tmpDir
      .resolve(".gradle/daemon").toFile()
      .walk()
      .filter { it.name.endsWith("out.log") }
      .forEach {
        // There is a more general rule that collects logs only when tests fail,
        // but in benchmarks we also are interested in logs when it succeeds.
        Files.copy(it.toPath(), testOutputDir.resolve(it.name), StandardCopyOption.REPLACE_EXISTING)
      }
  }
}
