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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeRunConfigurationFactory;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import javax.annotation.Nullable;

/** Creates run configurations from a BUILD file targets. */
public class BlazeBuildFileRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  static class BuildTarget {

    final FuncallExpression rule;
    final RuleType ruleType;
    final Label label;

    BuildTarget(FuncallExpression rule, RuleType ruleType, Label label) {
      this.rule = rule;
      this.ruleType = ruleType;
      this.label = label;
    }

    @Nullable
    TargetInfo guessTargetInfo() {
      String ruleName = rule.getFunctionName();
      if (ruleName == null) {
        return null;
      }
      Kind kind = Kind.fromRuleName(ruleName);
      return kind != null ? TargetInfo.builder(label, kind.getKindString()).build() : null;
    }
  }

  public BlazeBuildFileRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    Project project = configuration.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    // With query sync we don't need a sync to run a configuration
    if (blazeProjectData == null && Blaze.getProjectType(project) != ProjectType.QUERY_SYNC) {
      return false;
    }
    BuildTarget target = getBuildTarget(context);
    if (target == null) {
      return false;
    }
    sourceElement.set(target.rule);
    setupConfiguration(configuration.getProject(), blazeProjectData, configuration, target);
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BuildTarget target = getBuildTarget(context);
    if (target == null) {
      return false;
    }
    if (!Objects.equals(configuration.getTargets(), ImmutableList.of(target.label))) {
      return false;
    }
    // We don't know any details about how the various factories set up configurations from here.
    // Simply returning true at this point would be overly broad
    // (all configs with a matching target would be identified).
    // A complete equality check, meanwhile, would be too restrictive
    // (things like config name and user flags shouldn't count)
    // - not to mention we lack the equals() implementations needed to perform such a check!

    // So we compromise: if the target, suggested name, and command name match,
    // we consider it close enough. The suggested name is checked because it tends
    // to cover what the handler considers important,
    // and ignores changes the user may have made to the name.
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(configuration.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return false;
    }
    BlazeCommandRunConfiguration generatedConfiguration =
        new BlazeCommandRunConfiguration(
            configuration.getProject(), configuration.getFactory(), configuration.getName());
    setupConfiguration(
        configuration.getProject(), blazeProjectData, generatedConfiguration, target);

    // ignore filtered test configs, produced by other configuration producers.
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState != null && handlerState.getTestFilterFlag() != null) {
      return false;
    }

    return Objects.equals(configuration.suggestedName(), generatedConfiguration.suggestedName())
        && Objects.equals(
            configuration.getHandler().getCommandName(),
            generatedConfiguration.getHandler().getCommandName());
  }

  @Nullable
  private static BuildTarget getBuildTarget(ConfigurationContext context) {
    return getBuildTarget(
        PsiTreeUtil.getNonStrictParentOfType(context.getPsiLocation(), FuncallExpression.class));
  }

  @Nullable
  static BuildTarget getBuildTarget(@Nullable FuncallExpression rule) {
    if (rule == null) {
      return null;
    }
    String ruleType = rule.getFunctionName();
    Label label = rule.resolveBuildLabel();
    if (ruleType == null || label == null) {
      return null;
    }
    return new BuildTarget(rule, Kind.guessRuleType(ruleType), label);
  }

  private static void setupConfiguration(
      Project project,
      BlazeProjectData blazeProjectData,
      BlazeCommandRunConfiguration configuration,
      BuildTarget target) {
    // First see if a BlazeRunConfigurationFactory can give us a specialized setup.
    for (BlazeRunConfigurationFactory configurationFactory :
        BlazeRunConfigurationFactory.EP_NAME.getExtensions()) {
      if (configurationFactory.handlesTarget(project, blazeProjectData, target.label)
          && configurationFactory.handlesConfiguration(configuration)) {
        configurationFactory.setupConfiguration(configuration, target.label);
        return;
      }
    }

    // If no factory exists, directly set up the configuration.
    setupBuildFileConfiguration(configuration, target);
  }

  private static void setupBuildFileConfiguration(
      BlazeCommandRunConfiguration config, BuildTarget target) {
    TargetInfo info = target.guessTargetInfo();
    if (info != null) {
      config.setTargetInfo(info);
    } else {
      config.setTarget(target.label);
    }
    BlazeCommandRunConfigurationCommonState state =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (state != null) {
      state.getCommandState().setCommand(commandForRuleType(target.ruleType));
    }
    config.setGeneratedName();
  }

  static BlazeCommandName commandForRuleType(RuleType ruleType) {
    switch (ruleType) {
      case BINARY:
        return BlazeCommandName.RUN;
      case TEST:
        return BlazeCommandName.TEST;
      default:
        return BlazeCommandName.BUILD;
    }
  }
}
