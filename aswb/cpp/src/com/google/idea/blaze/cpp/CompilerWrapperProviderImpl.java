/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Creates a wrapper script which reads the arguments file and writes the compiler outputs directly.
 */
public class CompilerWrapperProviderImpl implements CompilerWrapperProvider {
  private static final Logger logger = Logger.getInstance(CompilerWrapperProviderImpl.class);

  @Override
  public File createCompilerExecutableWrapper(
      File executionRoot, File blazeCompilerExecutableFile) {
    try {
      File blazeCompilerWrapper =
          FileUtil.createTempFile("blaze_compiler", ".sh", true /* deleteOnExit */);
      if (!blazeCompilerWrapper.setExecutable(true)) {
        logger.warn("Unable to make compiler wrapper script executable: " + blazeCompilerWrapper);
        return null;
      }
      ImmutableList<String> compilerWrapperScriptLines =
          ImmutableList.of(
              "#!/bin/bash",
              "",
              "# The c toolchain compiler wrapper script doesn't handle arguments files, so we",
              "# need to move the compiler arguments from the file to the command line. We",
              "# preserve any existing commandline arguments, and remove the escaping from",
              "# arguments inside the args file.",
              "",
              "parsedargs=()",
              "for arg in \"${@}\"; do ",
              "  case \"$arg\" in",
              "    @*)",
              "      # Make sure the file ends with a newline - the read loop will not return",
              "      # the final line if it does not.",
              "      echo >> ${arg#@}",
              "      # Args file, the read will remove a layer of escaping",
              "      while read; do",
              "        parsedargs+=($REPLY)",
              "      done < ${arg#@}",
              "      ;;",
              "    *)",
              "      # Regular arg",
              "      parsedargs+=(\"$arg\")",
              "      ;;",
              "  esac",
              "done",
              "",
              "# The actual compiler wrapper script we get from blaze",
              String.format("EXE=%s", blazeCompilerExecutableFile.getPath()),
              "# Read in the arguments file so we can pass the arguments on the command line.",
              String.format("(cd %s && $EXE \"${parsedargs[@]}\")", executionRoot));

      try (PrintWriter pw = new PrintWriter(blazeCompilerWrapper, UTF_8.name())) {
        compilerWrapperScriptLines.forEach(pw::println);
      }
      return blazeCompilerWrapper;
    } catch (IOException e) {
      logger.warn(
          "Unable to write compiler wrapper script executable: " + blazeCompilerExecutableFile, e);
      return null;
    }
  }
}
