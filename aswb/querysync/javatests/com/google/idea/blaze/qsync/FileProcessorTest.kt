/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FileProcessorTest {

  private val workspaceRoot = Path.of("/workspace")
  private val fileExtensions = FileExtensions()
  private val processor = FileProcessor(workspaceRoot, fileExtensions)

  private fun process(filePath: String): FileProcessResult {
    val file = workspaceRoot.resolve(filePath)
    return processor.processRegularFile(file, file.parent)
  }

  @Test
  fun testBuildFile() {
    assertThat(process("java/com/example/BUILD")).isEqualTo(FileProcessResult.Package(Path.of("java/com/example")))
    assertThat(process("java/com/example/BUILD.bazel")).isEqualTo(FileProcessResult.Package(Path.of("java/com/example")))
  }

  @Test
  fun testIgnoredFiles() {
    assertThat(process("java/com/example/TempFile_")).isEqualTo(FileProcessResult.Ignored)
    assertThat(process("java/com/example/.bazelproject")).isEqualTo(FileProcessResult.Ignored)
    assertThat(process("java/com/example/.blazeproject")).isEqualTo(FileProcessResult.Ignored)
    assertThat(process("java/com/example/noextension")).isEqualTo(FileProcessResult.Ignored)
    assertThat(process("docs/README.md")).isEqualTo(FileProcessResult.Ignored) // Unknown extension
  }

  @Test
  fun testJvmSourceFiles() {
    assertThat(process("java/com/example/MyClass.java"))
      .isEqualTo(FileProcessResult.SourceFile(Path.of("java/com/example/MyClass.java"), QuerySyncLanguage.JVM))
    assertThat(process("java/com/example/MyModule.kt"))
      .isEqualTo(FileProcessResult.SourceFile(Path.of("java/com/example/MyModule.kt"), QuerySyncLanguage.JVM))
  }

  @Test
  fun testCcSourceFiles() {
    assertThat(process("cpp/lib/file.cc"))
      .isEqualTo(FileProcessResult.SourceFile(Path.of("cpp/lib/file.cc"), QuerySyncLanguage.CC))
    assertThat(process("cpp/lib/file.h"))
      .isEqualTo(FileProcessResult.SourceFile(Path.of("cpp/lib/file.h"), QuerySyncLanguage.CC))
    assertThat(process("cpp/lib/file.hpp"))
      .isEqualTo(FileProcessResult.SourceFile(Path.of("cpp/lib/file.hpp"), QuerySyncLanguage.CC))
  }

  @Test
  fun testProtoSourceFiles() {
    assertThat(process("proto/my_service.proto"))
      .isEqualTo(FileProcessResult.SourceFile(Path.of("proto/my_service.proto"), null))
  }
}
