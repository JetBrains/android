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
package com.google.idea.blaze.android.run.test;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import java.util.List;

/** Provides a list of supported launch methods from bazel and blaze for android tests. */
public class BlazeAndroidTestLaunchMethodsProviderImpl
    implements BlazeAndroidTestLaunchMethodsProvider {
  @Override
  public List<AndroidTestLaunchMethodComboEntry> getLaunchMethods(Project project) {
    String blaze = Blaze.buildSystemName(project);
    return ImmutableList.of(
        new AndroidTestLaunchMethodComboEntry(
            AndroidTestLaunchMethod.NON_BLAZE, String.format("Run without using %s", blaze)),
        new AndroidTestLaunchMethodComboEntry(
            AndroidTestLaunchMethod.BLAZE_TEST,
            String.format("Run with %s test", blaze.toLowerCase())),
        new AndroidTestLaunchMethodComboEntry(
            AndroidTestLaunchMethod.MOBILE_INSTALL,
            String.format("Run with %s mobile-install", blaze.toLowerCase())));
  }
}
