/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.plugin;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BuildSystemName;

/** Verifies that the available Bazel version is supported by this plugin. */
public class BazelVersionChecker implements BuildSystemVersionChecker {

  private static final BazelVersion OLDEST_SUPPORTED_VERSION = new BazelVersion(4, 0, 0);

  @Override
  public boolean versionSupported(BlazeContext context, BlazeVersionData version) {
    if (version.buildSystem() != BuildSystemName.Bazel) {
      return true;
    }
    if (version.bazelIsAtLeastVersion(OLDEST_SUPPORTED_VERSION)) {
      return true;
    }
    IssueOutput.error(
            String.format(
                "Bazel version %s is not supported by this version of the Bazel plugin. "
                    + "Please upgrade to Bazel version %s+.\n"
                    + "Upgrade instructions are available at https://bazel.build",
                version, OLDEST_SUPPORTED_VERSION))
        .submit(context);
    return false;
  }
}
