/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java

import com.google.common.base.Joiner
import com.google.common.truth.Truth
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.common.Output
import com.google.idea.blaze.common.PrintOutput
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PackageStatementParserTest {
  @Test
  @Throws(IOException::class)
  fun basic_package_statement() {
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(
        ByteArrayInputStream(
          Joiner.on("\n")
            .join("package com.myorg.somepackage;", "", "public class MyClass {}")
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )
      .isEqualTo("com.myorg.somepackage")
  }

  @Test
  @Throws(IOException::class)
  fun comment_before_package_statement() {
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(
        ByteArrayInputStream(
          Joiner.on("\n")
            .join(
              "/**",
              " * Copyright statement!",
              " * Another line",
              " */",
              "package com.myorg.otherpackage;",
              "",
              "public class MyClass {}"
            )
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )
      .isEqualTo("com.myorg.otherpackage")
  }

  @Test
  @Throws(IOException::class)
  fun single_line_generated_file() {
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(
        ByteArrayInputStream(
          (("/* This is a generated file */package com.myorg.package.generated;public"
            + " final class SomeClass {public final static boolean GENERATED_THING ="
            + " true;}"))
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )
      .isEqualTo("com.myorg.package.generated")
  }

  @Test
  @Throws(IOException::class)
  fun basic_kotlin() {
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(
        ByteArrayInputStream(
          Joiner.on("\n")
            .join(
              "package com.myorg.kotlinpackage",
              "",
              "import kotlin.text.*",
              "",
              "fun main() {",
              "    println(\"Hello world!\")",
              "}"
            )
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )
      .isEqualTo("com.myorg.kotlinpackage")
  }

  @Test
  @Throws(IOException::class)
  fun kotlin_package_annotation() {
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(
        ByteArrayInputStream(
          Joiner.on("\n")
            .join(
              "@file:JvmName(\"MyFile\")",
              "package com.myorg.kotlinpackage",
              "",
              "object MyObject {",
              "}"
            )
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )
      .isEqualTo("com.myorg.kotlinpackage")
  }

  @Test
  @Throws(IOException::class)
  fun kotlin_shebang() {
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(
        ByteArrayInputStream(
          Joiner.on("\n")
            .join(
              "#!/bin/interpreter",
              "package com.myorg.kotlinpackage",
              "",
              "object MyObject {",
              "}"
            )
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )
      .isEqualTo("com.myorg.kotlinpackage")
  }

  @Test
  @Throws(IOException::class)
  fun handled_io_errors() {
    val outputs = ArrayList<Output?>()
    val psp = PackageStatementParser()
    Truth.assertThat(
      psp.readPackage(object : NoopContext() {
        override fun <T : Output?> output(output: T?) {
          outputs.add(output)
        }
      }, Path.of("/file/indeed/not/found!"))
    ).isNull()
    Truth.assertThat(outputs)
      .containsExactly(
        PrintOutput.error("Cannot read file '/file/indeed/not/found!': /file/indeed/not/found! (No such file or directory)")
      )
  }
}
