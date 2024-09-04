/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.query;

import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Runs blaze queries. Intended for queries run automatically in the background, which it tries to
 * run with a custom 'output_base', to avoid monopolizing the primary blaze server's lock.
 */
public class BlazeQueryOutputBaseProvider {

  public static BlazeQueryOutputBaseProvider getInstance(Project project) {
    return project.getService(BlazeQueryOutputBaseProvider.class);
  }

  private static final Logger logger = Logger.getInstance(BlazeQueryOutputBaseProvider.class);

  private static final BoolExperiment useCustomOutputBase =
      new BoolExperiment("query.custom.output.base", true);

  @Nullable private final File outputBase;

  private BlazeQueryOutputBaseProvider() {
    this.outputBase = getTempDir();
  }

  @Nullable
  private static File getTempDir() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    try {
      File outputBase = Files.createTempDirectory("tmpOutputBase_" + suffix).toFile();
      outputBase.deleteOnExit();
      return outputBase;
    } catch (IOException e) {
      logger.warn("Couldn't create temporary output base directory", e);
      return null;
    }
  }

  /**
   * Returns the output_base flag which should be used for background query invocations, or null if
   * such invocations aren't currently supported.
   */
  @Nullable
  public String getOutputBaseFlag() {
    return outputBase == null || !useCustomOutputBase.getValue()
        ? null
        : "--output_base=" + outputBase.getPath();
  }
}
