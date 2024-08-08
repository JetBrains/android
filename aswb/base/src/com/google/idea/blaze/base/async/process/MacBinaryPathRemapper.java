/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async.process;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import java.io.File;
import java.util.Optional;

/** Redirects binaries to match console $PATH on Macs. */
final class MacBinaryPathRemapper implements BinaryPathRemapper {

  /**
   * Given an absolute path or command and, returns a corresponding remapped file, or an empty
   * optional if it's not remapped.
   */
  @Override
  public Optional<File> getRemappedBinary(String path) {
    if (!SystemInfo.isMac || path.indexOf(File.separatorChar) >= 0) {
      return Optional.empty();
    }
    String shellPath = EnvironmentUtil.getValue("PATH");
    return Optional.ofNullable(
        PathEnvironmentVariableUtil.findInPath(path, shellPath, /* filter= */ null));
  }
}
