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
package com.google.idea.blaze.base.run.confighandler;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import javax.annotation.Nullable;

/**
 * Generic handler provider for {@link BlazeCommandRunConfiguration}s, used as a fallback in the
 * case where no other handler providers are more relevant.
 */
public class BlazeCommandGenericRunConfigurationHandlerProvider
    implements BlazeCommandRunConfigurationHandlerProvider {

  public static BlazeCommandGenericRunConfigurationHandlerProvider getInstance() {
    return BlazeCommandRunConfigurationHandlerProvider.EP_NAME.findExtension(
        BlazeCommandGenericRunConfigurationHandlerProvider.class);
  }

  @Override
  public boolean canHandleKind(TargetState state, @Nullable Kind kind) {
    return !state.equals(TargetState.PENDING);
  }

  @Override
  public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
    return new BlazeCommandGenericRunConfigurationHandler(config);
  }

  @Override
  public String getId() {
    return "BlazeCommandGenericRunConfigurationHandlerProvider";
  }
}
