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
import com.intellij.openapi.extensions.ExtensionPointName;
import javax.annotation.Nullable;

/**
 * Provides a {@link BlazeCommandRunConfigurationHandler} corresponding to a given {@link
 * BlazeCommandRunConfiguration}.
 */
public interface BlazeCommandRunConfigurationHandlerProvider {

  ExtensionPointName<BlazeCommandRunConfigurationHandlerProvider> EP_NAME =
      ExtensionPointName.create(
          "com.google.idea.blaze.BlazeCommandRunConfigurationHandlerProvider");

  /** The target state of a blaze run configuration. */
  enum TargetState {
    KNOWN,
    PENDING;
  }

  /**
   * Find a BlazeCommandRunConfigurationHandlerProvider applicable to the given kind. If no provider
   * is more relevant, {@link BlazeCommandGenericRunConfigurationHandlerProvider} is returned.
   */
  static BlazeCommandRunConfigurationHandlerProvider findHandlerProvider(
      TargetState state, @Nullable Kind kind) {
    for (BlazeCommandRunConfigurationHandlerProvider handlerProvider : EP_NAME.getExtensions()) {
      if (handlerProvider.canHandleKind(state, kind)) {
        return handlerProvider;
      }
    }
    throw new RuntimeException(
        "No BlazeCommandRunConfigurationHandlerProvider found for Kind " + kind);
  }

  /** Get the BlazeCommandRunConfigurationHandlerProvider with the given ID, if one exists. */
  @Nullable
  static BlazeCommandRunConfigurationHandlerProvider getHandlerProvider(@Nullable String id) {
    for (BlazeCommandRunConfigurationHandlerProvider handlerProvider : EP_NAME.getExtensions()) {
      if (handlerProvider.getId().equals(id)) {
        return handlerProvider;
      }
    }
    return null;
  }

  /** Whether this extension is applicable to the kind. */
  boolean canHandleKind(TargetState state, @Nullable Kind kind);

  /** Returns the corresponding {@link BlazeCommandRunConfigurationHandler}. */
  BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration configuration);

  /**
   * Returns the unique ID of this {@link BlazeCommandRunConfigurationHandlerProvider}. The ID is
   * used to store configuration settings and must not change between plugin versions.
   */
  String getId();
}
