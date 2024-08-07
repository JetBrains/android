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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Package reader that parses package statements from java source files. */
public class PackageStatementParser implements PackageReader {

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)");
  private static final Pattern SINGLE_LINE_PACKAGE_PATTERN =
      Pattern.compile("\\bpackage\\s+([^;]+);");

  @Override
  public String readPackage(Path path) throws IOException {
    try (InputStream in = new FileInputStream(path.toFile())) {
      return readPackage(in);
    }
  }

  public String readPackage(InputStream in) throws IOException {
    BufferedReader javaReader = new BufferedReader(new InputStreamReader(in, UTF_8));
    String firstLine = null;
    String javaLine;
    int linesRead = 0;
    while ((javaLine = javaReader.readLine()) != null) {
      if (firstLine == null) {
        firstLine = javaLine;
      }
      linesRead++;
      Matcher packageMatch = PACKAGE_PATTERN.matcher(javaLine);
      if (packageMatch.find()) {
        return packageMatch.group(1);
      }
    }
    // A special case for generated sources files with no newlines in them:
    if (linesRead == 1) {
      Matcher packageMatch = SINGLE_LINE_PACKAGE_PATTERN.matcher(firstLine);
      if (packageMatch.find()) {
        return packageMatch.group(1);
      }
    }
    return "";
  }
}
