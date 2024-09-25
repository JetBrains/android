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
package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import javax.annotation.Nullable;

/** Python-specific handler provider for {@link BlazeCommandRunConfiguration}s. */
public class BlazePyRunConfigurationHandlerProvider
    implements BlazeCommandRunConfigurationHandlerProvider {

  @Override
  public boolean canHandleKind(TargetState state, @Nullable Kind kind) {
    return PyDebugUtils.canUsePyDebugger(kind);
  }

  @Override
  public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
    return new BlazePyRunConfigurationHandler(config);
  }

  @Override
  public String getId() {
    return "BlazePyRunConfigurationHandlerProvider";
  }
}
