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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.PendingRunConfigurationContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import java.util.Comparator;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * When we have two or more candidate web_tests for a given context.
 *
 * <p>Creates a popup chooser when executed to select the desired web_test to run.
 */
public class PendingWebTestContext extends TestContext implements PendingRunConfigurationContext {
  private static BoolExperiment findWebTestContext =
      new BoolExperiment("find.web.test.context", true);

  /**
   * Attempt to find web_test(s) wrapping a particular target.
   *
   * <ul>
   *   <li>If we find no web_tests, return null. The caller will proceed with the original target.
   *   <li>If we find exactly one web_test, then we replace the original target with the web_test in
   *       a {@link KnownTargetTestContext}.
   *   <li>if we find two or more web_tests, then we return a {@link PendingWebTestContext}, which
   *       will surface a popup chooser for selecting the desired web_test when executed.
   * </ul>
   */
  @Nullable
  static RunConfigurationContext findWebTestContext(
      Project project,
      ImmutableSet<ExecutorType> supportedExecutors,
      TargetInfo target,
      PsiElement sourceElement,
      ImmutableList<BlazeFlagsModification> blazeFlags,
      @Nullable String description) {
    if (!findWebTestContext.getValue()) {
      return null;
    }
    // TODO(b/274800785): Add query sync support
    if (Blaze.getProjectType(project) != ProjectType.ASPECT_SYNC) {
      return null;
    }
    ImmutableList<TargetInfo> wrapperTests = getWebTestWrappers(project, target);
    if (wrapperTests.isEmpty()) {
      return null;
    } else if (wrapperTests.size() == 1) {
      return new KnownTargetTestContext(
          wrapperTests.get(0), sourceElement, blazeFlags, description);
    }
    return new PendingWebTestContext(
        wrapperTests, supportedExecutors, sourceElement, blazeFlags, description);
  }

  private final ImmutableList<TargetInfo> wrapperTests;
  private final ImmutableSet<ExecutorType> supportedExecutors;

  private PendingWebTestContext(
      ImmutableList<TargetInfo> wrapperTests,
      ImmutableSet<ExecutorType> supportedExecutors,
      PsiElement sourceElement,
      ImmutableList<BlazeFlagsModification> blazeFlags,
      @Nullable String description) {
    super(sourceElement, blazeFlags, description);
    this.wrapperTests = wrapperTests;
    this.supportedExecutors = supportedExecutors;
  }

  private static ImmutableList<TargetInfo> getWebTestWrappers(
      Project project, TargetInfo wrappedTest) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }
    TargetMap targetMap = projectData.getTargetMap();
    return ReverseDependencyMap.get(project)
        .get(TargetKey.forPlainTarget(wrappedTest.label))
        .stream()
        .map(targetMap::get)
        .filter(Objects::nonNull)
        .filter(t -> t.getKind().isWebTest())
        .map(TargetIdeInfo::toTargetInfo)
        .sorted(Comparator.comparing(t -> t.label))
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public boolean isDone() {
    return false;
  }

  @Override
  public ImmutableSet<ExecutorType> supportedExecutors() {
    return supportedExecutors;
  }

  @Override
  boolean setupTarget(BlazeCommandRunConfiguration config) {
    config.setPendingContext(this);
    return true;
  }

  @Override
  boolean matchesTarget(BlazeCommandRunConfiguration config) {
    return getSourceElementString().equals(config.getContextElementString());
  }

  @Override
  public void resolve(
      ExecutionEnvironment env, BlazeCommandRunConfiguration config, Runnable rerun) {
    DataContext dataContext = env.getDataContext();
    if (dataContext == null) {
      return;
    }
    JBPopup popup =
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(wrapperTests)
            .setTitle("Choose Web Test to Run")
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setCancelOnWindowDeactivation(false)
            .setItemChosenCallback(
                (wrapperTest) -> updateContextAndRerun(config, wrapperTest, rerun))
            .createPopup();
    TransactionGuard.getInstance()
        .submitTransactionAndWait(() -> popup.showInBestPositionFor(dataContext));
  }

  @VisibleForTesting
  public void updateContextAndRerun(
      BlazeCommandRunConfiguration config, TargetInfo wrapperTest, Runnable rerun) {
    // Changing the description here prevents rerun,
    // due to RunnerAndConfigurationSettings being tied to description.
    RunConfigurationContext context =
        new KnownTargetTestContext(wrapperTest, sourceElement, blazeFlags, description);
    if (context.setupRunConfiguration(config)) {
      config.clearPendingContext();
      rerun.run();
    }
  }
}
