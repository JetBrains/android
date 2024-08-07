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
package com.google.idea.blaze.qsync.java;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PackageStatementParserTest {

  @Test
  public void basic_package_statement() throws IOException {
    PackageStatementParser psp = new PackageStatementParser();
    assertThat(
            psp.readPackage(
                new ByteArrayInputStream(
                    Joiner.on("\n")
                        .join("package com.myorg.somepackage;", "", "public class MyClass {}")
                        .getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.myorg.somepackage");
  }

  @Test
  public void comment_before_package_statement() throws IOException {
    PackageStatementParser psp = new PackageStatementParser();
    assertThat(
            psp.readPackage(
                new ByteArrayInputStream(
                    Joiner.on("\n")
                        .join(
                            "/**",
                            " * Copyright statement!",
                            " * Another line",
                            " */",
                            "package com.myorg.otherpackage;",
                            "",
                            "public class MyClass {}")
                        .getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.myorg.otherpackage");
  }

  @Test
  public void single_line_generated_file() throws IOException {
    PackageStatementParser psp = new PackageStatementParser();
    assertThat(
            psp.readPackage(
                new ByteArrayInputStream(
                    ("/* This is a generated file */package com.myorg.package.generated;public"
                         + " final class SomeClass {public final static boolean GENERATED_THING ="
                         + " true;}")
                        .getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.myorg.package.generated");
  }

  @Test
  public void basic_kotlin() throws IOException {
    PackageStatementParser psp = new PackageStatementParser();
    assertThat(
            psp.readPackage(
                new ByteArrayInputStream(
                    Joiner.on("\n")
                        .join(
                            "package com.myorg.kotlinpackage",
                            "",
                            "import kotlin.text.*",
                            "",
                            "fun main() {",
                            "    println(\"Hello world!\")",
                            "}")
                        .getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.myorg.kotlinpackage");
  }

  @Test
  public void kotlin_package_annotation() throws IOException {
    PackageStatementParser psp = new PackageStatementParser();
    assertThat(
            psp.readPackage(
                new ByteArrayInputStream(
                    Joiner.on("\n")
                        .join(
                            "@file:JvmName(\"MyFile\")",
                            "package com.myorg.kotlinpackage",
                            "",
                            "object MyObject {",
                            "}")
                        .getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.myorg.kotlinpackage");
  }

  @Test
  public void kotlin_shebang() throws IOException {
    PackageStatementParser psp = new PackageStatementParser();
    assertThat(
            psp.readPackage(
                new ByteArrayInputStream(
                    Joiner.on("\n")
                        .join(
                            "#!/bin/interpreter",
                            "package com.myorg.kotlinpackage",
                            "",
                            "object MyObject {",
                            "}")
                        .getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.myorg.kotlinpackage");
  }
}
