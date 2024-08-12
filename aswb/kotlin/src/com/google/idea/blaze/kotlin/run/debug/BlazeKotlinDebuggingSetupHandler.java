/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.debug;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.java.run.BlazeJavaDebuggingSetupHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kotlin-specific handler for {@link BlazeJavaDebuggingSetupHandler}.
 *
 * <p>This class is mainly needed to view coroutines debugging panel and enable coroutines plugin
 * for Kotlin targets that use coroutines and depend on the required versions of kotlinx-coroutines
 * library.
 */
public class BlazeKotlinDebuggingSetupHandler implements BlazeJavaDebuggingSetupHandler {

  // Used to store the path of the Coroutines lib needed to be used as a javaagent for debugging.
  static final Key<AtomicReference<String>> COROUTINES_LIB_PATH = Key.create("coroutines.lib.path");

  @Override
  public boolean setUpDebugging(ExecutionEnvironment env) {
      BlazeCommandRunConfiguration config =
          BlazeCommandRunConfigurationRunner.getConfiguration(env);
    if (Blaze.getProjectType(config.getProject()).equals(ProjectType.QUERY_SYNC)) {
      if (KotlinProjectTraversingService.getInstance().dependsOnKotlinxCoroutinesLib(config)) {
        getCoroutinesDebuggingLib(null, config)
            .ifPresent(path -> env.getCopyableUserData(COROUTINES_LIB_PATH).set(path));
      }
      return true;
    }

      Optional<ArtifactLocation> libArtifact =
          KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

      libArtifact
          .flatMap(artifact -> getCoroutinesDebuggingLib(artifact, config))
          .ifPresent(path -> env.getCopyableUserData(COROUTINES_LIB_PATH).set(path));
    return true;
  }

  @Override
  public Optional<Key<AtomicReference<String>>> getEnvironmentDataKey() {
    return Optional.of(COROUTINES_LIB_PATH);
  }

  private static Optional<String> getCoroutinesDebuggingLib(
      ArtifactLocation artifact, BlazeCommandRunConfiguration config) {
    return KotlinxCoroutinesDebuggingLibProvider.EP_NAME.getExtensionList().stream()
        .filter(p -> p.isApplicable(config.getProject()))
        .findFirst()
        .flatMap(p -> p.getKotlinxCoroutinesDebuggingLib(artifact, config));
  }
}
