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
package com.google.idea.blaze.base.run.producers;

import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.EditorTestHelper;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.testing.FunctionalHeadlessDataManager;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.CoreIconManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.junit.After;
import org.junit.Before;

/** Run configuration producer integration test base */
public class BlazeRunConfigurationProducerTestCase extends BlazeIntegrationTestCase {

  protected EditorTestHelper editorTest;
  private DataManager defaultDataManager;
  private final List<TargetInfo> registeredTargets = new ArrayList<>();

  @Before
  public final void doSetup() throws Throwable {
    registerExtension(
        SourceToTargetFinder.EP_NAME,
        (project, sourceFiles, ruleType) -> {
          List<TargetInfo> candidates = new ArrayList<>(registeredTargets);
          if (ruleType.isPresent()) {
            candidates =
                candidates.stream()
                    .filter(t -> ruleType.get().equals(t.getRuleType()))
                    .collect(Collectors.toList());
          }
          return Futures.immediateFuture(candidates);
        });

    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    editorTest = new EditorTestHelper(getProject(), testFixture);

    // IntelliJ replaces the normal DataManager with a mock version in headless environments.
    // We rely on a functional DataManager in run configuration tests to recognize when multiple
    // psi elements are selected.
    defaultDataManager = DataManager.getInstance();
    ServiceContainerUtil.registerServiceInstance(
        ApplicationManager.getApplication(),
        DataManager.class,
        new FunctionalHeadlessDataManager());

    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(getProject());
    producerService
        .getState()
        .ignoredProducers
        .addAll(
            Arrays.asList(
                "com.intellij.execution.junit.AbstractAllInDirectoryConfigurationProducer",
                "com.intellij.execution.junit.AllInDirectoryConfigurationProducer",
                "com.intellij.execution.junit.AllInPackageConfigurationProducer",
                "com.intellij.execution.junit.TestInClassConfigurationProducer",
                "com.intellij.execution.junit.TestClassConfigurationProducer",
                "com.intellij.execution.junit.TestMethodConfigurationProducer",
                "com.intellij.execution.junit.PatternConfigurationProducer",
                "com.intellij.execution.junit.UniqueIdConfigurationProducer",
                "com.intellij.execution.junit.testDiscovery.JUnitTestDiscoveryConfigurationProducer",
                "com.intellij.execution.application.ApplicationConfigurationProducer",
                "org.jetbrains.kotlin.idea.junit.KotlinJUnitRunConfigurationProducer",
                "org.jetbrains.kotlin.idea.junit.KotlinPatternConfigurationProducer",
                "com.android.tools.idea.run.AndroidConfigurationProducer",
                "com.android.tools.idea.testartifacts.instrumented.AndroidTestConfigurationProducer"));

    // IntelliJ will use a dummy icon manager that returns the same exact icon.
    // This will cause uniqueness issues for gutter icons.
    IconManager.Companion.activate(new CoreIconManager());
  }

  protected void registerTargets(TargetInfo... targets) {
    registeredTargets.clear();
    registeredTargets.addAll(Arrays.asList(targets));
  }

  @After
  public final void doTeardown() {
    ServiceContainerUtil.registerServiceInstance(
        ApplicationManager.getApplication(), DataManager.class, defaultDataManager);
    IconManager.Companion.deactivate();
  }

  protected PsiFile createAndIndexFile(WorkspacePath path, String... contents) throws Throwable {
    PsiFile file = workspace.createPsiFile(path, contents);
    editorTest.openFileInEditor(file); // open file to trigger update of indices
    return file;
  }

  @Nullable
  protected static String getTestFilterContents(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return handlerState != null ? handlerState.getTestFilterFlag() : null;
  }

  @Nullable
  protected static BlazeCommandName getCommandType(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return handlerState != null ? handlerState.getCommandState().getCommand() : null;
  }

  protected ConfigurationContext createContextFromPsi(PsiElement element) {
    return ConfigurationContext.getFromContext(
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, getProject())
            .add(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(element))
            .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
            .build());
  }

  protected ConfigurationContext createContextFromMultipleElements(PsiElement[] elements) {
    return ConfigurationContext.getFromContext(
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, getProject())
            .add(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(elements[0]))
            .add(Location.DATA_KEY, PsiLocation.fromPsiElement(elements[0]))
            .add(
                Location.DATA_KEYS,
                Arrays.stream(elements)
                    .map(PsiLocation::fromPsiElement)
                    .toArray(Location<?>[]::new))
            .add(LangDataKeys.PSI_ELEMENT_ARRAY, elements)
            .build());
  }

  @Nullable
  protected RunConfiguration createConfigurationFromLocation(PsiFile psiFile) {
    RunnerAndConfigurationSettings settings =
        ConfigurationContext.getFromContext(
                SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, getProject())
                    .add(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(psiFile))
                    .add(Location.DATA_KEY, PsiLocation.fromPsiElement(psiFile))
                    .build())
            .getConfiguration();
    return settings != null ? settings.getConfiguration() : null;
  }

  /**
   * Test utility helper to execute a block within a progress indicator context, avoiding
   * IllegalStateException in runBlockingCancellable during integration test execution.
   */
  protected <T> T runWithProgress(Computable<T> computable) {
    return ProgressManager.getInstance().runProcess(computable, new EmptyProgressIndicator());
  }

  /**
   * Performs the full Stage 2 refinement, Stage 2.5 resolution, and Stage 3 configuration
   * application for a given {@link BlazeCommandRunConfiguration} and producer.
   */
  protected void performFirstRun(
      BlazeRunConfigurationProducer<?> producer,
      BlazeCommandRunConfiguration config,
      ConfigurationContext context) {
    runWithProgress(
        () -> {
          try {
            BuildersKt.runBlocking(
                EmptyCoroutineContext.INSTANCE,
                (scope, continuation) ->
                    producer.prepareAndSetupRunConfiguration(config, context, continuation));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
          }
          return null;
        });
  }

  /**
   * Performs the full Stage 2 refinement, Stage 2.5 resolution, and Stage 3 configuration
   * application for a given {@link BlazeCommandRunConfiguration} using {@link
   * TestContextRunConfigurationProducer}.
   */
  protected void performFirstRun(
      BlazeCommandRunConfiguration config, ConfigurationContext context) {
    performFirstRun(TestContextRunConfigurationProducer.getInstance(), config, context);
  }
}
