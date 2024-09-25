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
package com.google.idea.blaze.java.run;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Interface to set up the environment for debugging Java-like targets */
public interface BlazeJavaDebuggingSetupHandler {

  ExtensionPointName<BlazeJavaDebuggingSetupHandler> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.blazeJavaDebuggingSetupHandler");

  /**
   * Prepare the environment for debugging java-like targets.
   *
   * <p>This can include building binaries to be used during debugging.
   *
   * <p>Returns true if the environment is set up successfully, and returns false if the task failed
   * or was cancelled
   */
  boolean setUpDebugging(ExecutionEnvironment environment);

  /**
   * Returns the handler's environment data storage key.
   *
   * <p>Since this handler logic will be run as a before run task, the environment passed to {@code
   * setupDebugging(env)} is created for this task specifically and is created as a copy of the one
   * that will be used along the way in the following debugging steps.
   *
   * <p>Therefore, if the handler would like to store a value in the environment's copyableUserData,
   * they should return the data key in this method. {@link BlazeJavaRunConfigurationHandler} will
   * create an entry for this key in the environment and then the handler can update the value of
   * that key after its pre-run task is complete to make it available for later debugging stages.
   */
  Optional<Key<AtomicReference<String>>> getEnvironmentDataKey();

  static void initHandlersData(ExecutionEnvironment env) {
    for (BlazeJavaDebuggingSetupHandler handler : EP_NAME.getExtensionList()) {
      handler
          .getEnvironmentDataKey()
          .ifPresent(k -> env.putCopyableUserData(k, new AtomicReference<>()));
    }
  }

  static boolean setUpJavaDebugging(ExecutionEnvironment env) {
    return EP_NAME.getExtensionList().stream().allMatch(handler -> handler.setUpDebugging(env));
  }
}
