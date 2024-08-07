/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/**
 * Used to recognize blaze binary contexts, providing all the information required by blaze run
 * configuration producers.
 */
public interface BinaryContextProvider {

  ExtensionPointName<BinaryContextProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BinaryContextProvider");

  /** A context related to a blaze binary target, used to configure a run configuration. */
  @AutoValue
  abstract class BinaryRunContext {
    abstract PsiElement getSourceElement();

    abstract TargetInfo getTarget();

    public static BinaryRunContext create(PsiElement sourceElement, TargetInfo target) {
      return new AutoValue_BinaryContextProvider_BinaryRunContext(sourceElement, target);
    }
  }

  /**
   * Returns the {@link BinaryRunContext} corresponding to the given {@link ConfigurationContext},
   * if relevant and recognized by this provider.
   *
   * <p>This is called frequently on the EDT, via the {@link RunConfigurationProducer} API, so must
   * be efficient.
   */
  @Nullable
  BinaryRunContext getRunContext(ConfigurationContext context);
}
