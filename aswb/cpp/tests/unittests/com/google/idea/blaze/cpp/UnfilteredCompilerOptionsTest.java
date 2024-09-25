/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for any unfiltered compiler options */
@RunWith(JUnit4.class)
public class UnfilteredCompilerOptionsTest extends BlazeTestCase {
  @Test
  public void testUnfilteredOptionsParsingForISystemOptions() {
    ImmutableList<String> unfilteredOptions =
        ImmutableList.of(
            "-isystem",
            "sys/inc1",
            "-VER2",
            "-isystem",
            "sys2/inc1",
            "-isystem",
            "sys3/inc1",
            "-isystm",
            "sys4/inc1");
    UnfilteredCompilerOptions compilerOptions =
        UnfilteredCompilerOptions.builder()
            .registerSingleOrSplitOption("-isystem")
            .build(unfilteredOptions);

    List<String> sysIncludes = compilerOptions.getExtractedOptionValues("-isystem");
    List<String> flags = compilerOptions.getUninterpretedOptions();

    assertThat(sysIncludes).containsExactly("sys/inc1", "sys2/inc1", "sys3/inc1").inOrder();

    assertThat(flags).containsExactly("-VER2", "-isystm", "sys4/inc1").inOrder();
  }

  @Test
  public void testUnfilteredOptionsParsingForISystemOptionsNoSpaceAfterIsystem() {
    ImmutableList<String> unfilteredOptions =
        ImmutableList.of(
            "-isystem", "sys/inc1", "-VER2", "-isystemsys2/inc1", "-isystem", "sys3/inc1");
    UnfilteredCompilerOptions compilerOptions =
        UnfilteredCompilerOptions.builder()
            .registerSingleOrSplitOption("-isystem")
            .build(unfilteredOptions);

    List<String> sysIncludes = compilerOptions.getExtractedOptionValues("-isystem");
    List<String> flags = compilerOptions.getUninterpretedOptions();
    assertThat(sysIncludes).containsExactly("sys/inc1", "sys2/inc1", "sys3/inc1").inOrder();

    assertThat(flags).containsExactly("-VER2").inOrder();
  }

  @Test
  public void testMultipleFlagsToExtract() {
    ImmutableList<String> unfilteredOptions =
        ImmutableList.of(
            "-I",
            "foo/headers1",
            "-fno-exceptions",
            "-Werror",
            "-DMACRO1=1",
            "-D",
            "MACRO2",
            "-Ifoo/headers2",
            "-I=sysroot_header",
            "-Wall",
            "-I",
            "foo/headers3");
    UnfilteredCompilerOptions compilerOptions =
        UnfilteredCompilerOptions.builder()
            .registerSingleOrSplitOption("-I")
            .registerSingleOrSplitOption("-D")
            .build(unfilteredOptions);

    List<String> defines = compilerOptions.getExtractedOptionValues("-D");
    List<String> includes = compilerOptions.getExtractedOptionValues("-I");
    List<String> flags = compilerOptions.getUninterpretedOptions();
    assertThat(includes)
        .containsExactly("foo/headers1", "foo/headers2", "=sysroot_header", "foo/headers3")
        .inOrder();
    assertThat(defines).containsExactly("MACRO1=1", "MACRO2").inOrder();
    assertThat(flags).containsExactly("-fno-exceptions", "-Werror", "-Wall").inOrder();
  }
}
