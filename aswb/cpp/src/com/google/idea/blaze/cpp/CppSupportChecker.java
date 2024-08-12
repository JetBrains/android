/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.idea.common.util.MorePlatformUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import java.nio.file.Path;

/** Checks whether or not the IDE supports a given CPP configuration. */
public interface CppSupportChecker {

  ExtensionPointName<CppSupportChecker> EP_NAME =
      new ExtensionPointName<>("com.google.idea.blaze.cpp.CppSupportChecker");

  Logger logger = Logger.getInstance(CppSupportChecker.class);

  static boolean isSupportedCppConfiguration(
      CidrCompilerSwitches compilerSwitches, Path workspaceRoot) {
    for (CppSupportChecker checker : EP_NAME.getExtensionList()) {
      if (!checker.supportsCppConfiguration(compilerSwitches, workspaceRoot)) {
        logger.info("CPP support disallowed by " + checker.getClass().getName());
        return false;
      }
    }
    return true;
  }

  boolean supportsCppConfiguration(CidrCompilerSwitches switches, Path workspaceRoot);
}
