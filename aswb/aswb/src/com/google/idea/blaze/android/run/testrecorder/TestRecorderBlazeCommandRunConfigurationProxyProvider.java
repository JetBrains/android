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
package com.google.idea.blaze.android.run.testrecorder;

import com.android.annotations.Nullable;
import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxy;
import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxyProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.configurations.RunConfiguration;

/** Provides {@link TestRecorderBlazeCommandRunConfigurationProxy}. */
public class TestRecorderBlazeCommandRunConfigurationProxyProvider
    implements TestRecorderRunConfigurationProxyProvider {

  @Nullable
  @Override
  public TestRecorderRunConfigurationProxy getProxy(@Nullable RunConfiguration runConfiguration) {
    if (runConfiguration instanceof BlazeCommandRunConfiguration
        && ((BlazeCommandRunConfiguration) runConfiguration).getHandler()
            instanceof BlazeAndroidBinaryRunConfigurationHandler) {
      return new TestRecorderBlazeCommandRunConfigurationProxy(
          (BlazeCommandRunConfiguration) runConfiguration);
    }

    return null;
  }
}
