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
package com.android.tools.idea.device.explorer.files.adbimpl;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Assert;
import org.junit.Test;

public class AdbShellCommandBuilderTest {
  @Test
  public void testBuildCommand() {
    assertThat(new AdbShellCommandBuilder()
                 .withText("ls -la")
                 .build()).isEqualTo("ls -la");
    assertThat(new AdbShellCommandBuilder()
                 .withText("ls -la ")
                 .withEscapedPath("/data/local/single'quote")
                 .build()).isEqualTo("ls -la /data/local/single\\'quote");
    assertThat(new AdbShellCommandBuilder()
                 .withText("ls -la ")
                 .withEscapedPath("/data/local/double\"quote")
                 .build()).isEqualTo("ls -la /data/local/double\\\"quote");
    assertThat(new AdbShellCommandBuilder()
                 .withText("echo don\\'t")
                 .build()).isEqualTo("echo don\\'t");
  }

  @Test
  public void testBuildCommandWithRunAs() {
    assertThat(new AdbShellCommandBuilder()
                 .withRunAs("my.package")
                 .withText("ls -la")
                 .build()).isEqualTo("run-as my.package sh -c 'ls -la'");
    assertThat(new AdbShellCommandBuilder()
                 .withRunAs("my.package")
                 .withText("ls -la ")
                 .withEscapedPath("/data/local/single'quote")
                 .build()).isEqualTo("run-as my.package sh -c 'ls -la /data/local/single\\'\"'\"'quote'");
    assertThat(new AdbShellCommandBuilder()
                 .withRunAs("my.package")
                 .withText("ls -la ")
                 .withEscapedPath("/data/local/double\"quote")
                 .build()).isEqualTo("run-as my.package sh -c 'ls -la /data/local/double\\\"quote'");
    assertThat(new AdbShellCommandBuilder()
                 .withRunAs("my.package")
                 .withText("echo don\\'t")
                 .build()).isEqualTo("run-as my.package sh -c 'echo don\\'\"'\"'t'");
  }

  @Test
  public void testBuildCommandWithSu() {
    assertThat(new AdbShellCommandBuilder()
                 .withSuRootPrefix()
                 .withText("ls -la")
                 .build()).isEqualTo("su 0 sh -c 'ls -la'");
    assertThat(new AdbShellCommandBuilder()
                 .withSuRootPrefix()
                 .withText("ls -la ")
                 .withEscapedPath("/data/local/single'quote")
                 .build()).isEqualTo("su 0 sh -c 'ls -la /data/local/single\\'\"'\"'quote'");
    assertThat(new AdbShellCommandBuilder()
                 .withSuRootPrefix()
                 .withText("ls -la ")
                 .withEscapedPath("/data/local/double\"quote")
                 .build()).isEqualTo("su 0 sh -c 'ls -la /data/local/double\\\"quote'");
    assertThat(new AdbShellCommandBuilder()
                 .withSuRootPrefix()
                 .withText("echo don\\'t")
                 .build()).isEqualTo("su 0 sh -c 'echo don\\'\"'\"'t'");
  }

  @Test
  public void testBuildCommandWithSuAndRunAs() {
    try {
      new AdbShellCommandBuilder()
        .withSuRootPrefix()
        .withRunAs("my.package")
        .withText("ls -la")
        .build();

      Assert.fail("Expected exception not thrown");
    } catch (IllegalStateException e) {
      // Expected
    }
  }
}
