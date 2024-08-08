/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BinaryContextProvider.BinaryRunContext;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Produces run configurations via {@link BinaryContextProvider}. */
public class BinaryContextRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  BinaryContextRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  /** Implements {@link #equals} so that cached value stability checker passes. */
  private static final class ContextWrapper {
    final ConfigurationContext context;

    ContextWrapper(ConfigurationContext context) {
      this.context = context;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ContextWrapper
          && Objects.equals(
              context.getPsiLocation(), ((ContextWrapper) obj).context.getPsiLocation());
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.getClass(), context.getPsiLocation());
    }
  }

  @Nullable
  private BinaryRunContext findRunContext(ConfigurationContext context) {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // not a binary run context
      return null;
    }
    ContextWrapper wrapper = new ContextWrapper(context);
    PsiElement psi = context.getPsiLocation();
    return psi == null
        ? null
        : CachedValuesManager.getCachedValue(
            psi,
            () ->
                CachedValueProvider.Result.create(
                    doFindRunContext(wrapper.context),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    BlazeSyncModificationTracker.getInstance(wrapper.context.getProject())));
  }

  @Nullable
  private BinaryRunContext doFindRunContext(ConfigurationContext context) {
    return Arrays.stream(BinaryContextProvider.EP_NAME.getExtensions())
        .map(p -> p.getRunContext(context))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    BinaryRunContext runContext = findRunContext(context);
    if (runContext == null) {
      return false;
    }
    sourceElement.set(runContext.getSourceElement());
    configuration.setTargetInfo(runContext.getTarget());
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.RUN);
    configuration.setGeneratedName();
    return true;
  }

  @VisibleForTesting
  @Override
  public boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandRunConfigurationCommonState commonState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    if (!Objects.equals(commonState.getCommandState().getCommand(), BlazeCommandName.RUN)) {
      return false;
    }
    BinaryRunContext runContext = findRunContext(context);
    if (runContext == null) {
      return false;
    }
    ImmutableList<? extends TargetExpression> targets = configuration.getTargets();
    return targets.size() == 1 && runContext.getTarget().label.equals(targets.get(0));
  }
}
