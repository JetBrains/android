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
package com.google.idea.blaze.java.sync.source;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Parse package string directly from java source */
public class JavaSourcePackageReader extends JavaPackageReader {
  private static final Logger logger = Logger.getInstance(JavaSourcePackageReader.class);

  public static JavaSourcePackageReader getInstance() {
    return ApplicationManager.getApplication().getService(JavaSourcePackageReader.class);
  }

  // Package declaration of java-like languages.
  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)");

  @Override
  @Nullable
  public String getDeclaredPackageOfJavaFile(
      BlazeContext context,
      ArtifactLocationDecoder artifactLocationDecoder,
      SourceArtifact sourceArtifact) {
    if (sourceArtifact.artifactLocation.isGenerated()) {
      return null;
    }
    InputStreamProvider inputStreamProvider = InputStreamProvider.getInstance();
    File sourceFile = artifactLocationDecoder.resolveSource(sourceArtifact.artifactLocation);
    if (sourceFile == null) {
      return null;
    }
    try (InputStream javaInputStream = inputStreamProvider.forFile(sourceFile)) {
      BufferedReader javaReader = new BufferedReader(new InputStreamReader(javaInputStream, UTF_8));
      String javaLine;

      while ((javaLine = javaReader.readLine()) != null) {
        Matcher packageMatch = PACKAGE_PATTERN.matcher(javaLine);
        if (packageMatch.find()) {
          return packageMatch.group(1);
        }
      }
      IssueOutput.warn("No package name string found in java source file: " + sourceFile)
          .inFile(sourceFile)
          .submit(context);
      return null;
    } catch (FileNotFoundException e) {
      context.output(PrintOutput.log("No source file found for: " + sourceFile));
      return null;
    } catch (IOException e) {
      logger.error(e);
      return null;
    }
  }
}
