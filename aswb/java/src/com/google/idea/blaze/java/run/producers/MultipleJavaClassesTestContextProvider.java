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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.base.sync.projectview.WorkspaceFileFinder;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Runs tests in all selected java classes (or all classes below selected directory). Ignores
 * classes spread across multiple test targets.
 */
class MultipleJavaClassesTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    boolean outsideProject = context.getModule() == null;
    if (outsideProject) {
      // TODO(brendandouglas): resolve PSI asynchronously for files outside the project
      return null;
    }
    PsiElement location = context.getPsiLocation();
    if (location instanceof PsiDirectory) {
      PsiDirectory dir = (PsiDirectory) location;
      ListenableFuture<TargetInfo> future = getTargetContext(dir);
      return futureEmpty(future) ? null : fromDirectory(future, dir);
    }
    Set<PsiClass> testClasses = selectedTestClasses(context);
    if (testClasses.size() <= 1) {
      return null;
    }
    ListenableFuture<TargetInfo> target = getTestTargetIfUnique(context.getProject(), testClasses);
    if (futureEmpty(target)) {
      return null;
    }
    testClasses = ProducerUtils.includeInnerTestClasses(testClasses);
    return fromClasses(target, testClasses);
  }

  @Nullable
  private static TestContext fromDirectory(ListenableFuture<TargetInfo> future, PsiDirectory dir) {
    String packagePrefix =
        ProjectFileIndex.SERVICE
            .getInstance(dir.getProject())
            .getPackageNameByDirectory(dir.getVirtualFile());
    if (packagePrefix == null) {
      return null;
    }
    String description = String.format("all in directory '%s'", dir.getName());
    String testFilter = packagePrefix.isEmpty() ? null : packagePrefix;
    return TestContext.builder(dir, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(future)
        .setTestFilter(testFilter)
        .setDescription(description)
        .build();
  }

  @Nullable
  private static TestContext fromClasses(
      ListenableFuture<TargetInfo> target, Set<PsiClass> classes) {
    Map<PsiClass, Collection<Location<?>>> methodsPerClass =
        classes.stream().collect(Collectors.toMap(c -> c, c -> ImmutableList.of()));
    String filter = BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(methodsPerClass);
    if (filter == null || filter.isEmpty()) {
      return null;
    }

    PsiClass sampleClass =
        classes.stream()
            .min(
                Comparator.comparing(
                    PsiClass::getName, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);
    if (sampleClass == null) {
      return null;
    }
    String name = sampleClass.getName();
    if (name != null && classes.size() > 1) {
      name += String.format(" and %s others", classes.size() - 1);
    }
    return TestContext.builder(sampleClass, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(filter)
        .setDescription(name)
        .build();
  }

  private static Set<PsiClass> selectedTestClasses(ConfigurationContext context) {
    DataContext dataContext = context.getDataContext();
    PsiElement[] elements = getSelectedPsiElements(dataContext);
    if (elements == null) {
      return ImmutableSet.of();
    }
    return Arrays.stream(elements)
        .map(e -> getNonAbstractTestClass(e))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Nullable
  private static PsiElement[] getSelectedPsiElements(DataContext context) {
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
    if (elements != null) {
      return elements;
    }
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    return element != null ? new PsiElement[] {element} : null;
  }

  /**
   * Returns a {@link RunConfigurationContext} future setting up the relevant test target pattern,
   * if one can be found.
   */
  @Nullable
  private static ListenableFuture<TargetInfo> getTargetContext(PsiDirectory dir) {
    Project project = dir.getProject();
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    if (!index.isInTestSourceContent(dir.getVirtualFile())) {
      return null;
    }
    if (BlazePackage.isBlazePackage(dir)) {
      // this case is handled by a separate run config producer
      return null;
    }
    ListenableFuture<Set<PsiClass>> classes = findAllTestClassesBeneathDirectory(dir);
    if (classes == null) {
      return null;
    }
    return Futures.transformAsync(
        classes, set -> ReadAction.compute(() -> getTestTargetIfUnique(project, set)), EXECUTOR);
  }

  private static final int MAX_DEPTH_TO_SEARCH = 8;
  private static final ListeningExecutorService EXECUTOR =
      ApplicationManager.getApplication().isUnitTestMode()
          ? MoreExecutors.newDirectExecutorService()
          : MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private static ListenableFuture<Set<PsiClass>> findAllTestClassesBeneathDirectory(
      PsiDirectory dir) {
    Project project = dir.getProject();
    WorkspaceFileFinder finder =
        WorkspaceFileFinder.Provider.getInstance(project).getWorkspaceFileFinder();
    if (finder == null || !relevantDirectory(finder, dir)) {
      return null;
    }
    return EXECUTOR.submit(
        () -> {
          Set<PsiClass> classes = new HashSet<>();
          ReadAction.run(() -> addClassesInDirectory(finder, dir, classes, /* currentDepth= */ 0));
          return classes;
        });
  }

  private static boolean relevantDirectory(WorkspaceFileFinder finder, PsiDirectory dir) {
    return finder.isInProject(new File(dir.getVirtualFile().getPath()));
  }

  private static void addClassesInDirectory(
      WorkspaceFileFinder finder, PsiDirectory dir, Set<PsiClass> set, int currentDepth) {
    if (currentDepth > MAX_DEPTH_TO_SEARCH || !relevantDirectory(finder, dir)) {
      return;
    }
    PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
    set.addAll(
        Arrays.stream(classes).filter(ProducerUtils::isTestClass).collect(toImmutableList()));
    for (PsiDirectory child : dir.getSubdirectories()) {
      addClassesInDirectory(finder, child, set, currentDepth + 1);
    }
  }

  private static ListenableFuture<TargetInfo> getTestTargetIfUnique(
      Project project, @Nullable Set<PsiClass> classes) {
    if (classes == null) {
      return Futures.immediateFuture(null);
    }
    Set<File> files =
        classes.stream()
            .map(PsiElement::getContainingFile)
            .filter(Objects::nonNull)
            .map(psi -> getFile(psi))
            .collect(toImmutableSet());
    ListenableFuture<Collection<TargetInfo>> targets =
        SourceToTargetFinder.findTargetInfoFuture(project, files, Optional.of(RuleType.TEST));
    if (futureEmpty(targets)) {
      return Futures.immediateFuture(null);
    }
    Executor executor =
        ApplicationManager.getApplication().isUnitTestMode()
            ? MoreExecutors.directExecutor()
            : PooledThreadExecutor.INSTANCE;
    return Futures.transform(
        targets, list -> findUniqueRelevantTestTarget(project, classes, list), executor);
  }

  /**
   * Runs the test choosing heuristics against each of the given classes. If the same target is
   * chosen for them all, returns that target, otherwise returns null.
   */
  @Nullable
  private static TargetInfo findUniqueRelevantTestTarget(
      Project project, Set<PsiClass> classes, @Nullable Collection<TargetInfo> targets) {
    if (targets == null || targets.isEmpty()) {
      return null;
    }
    Set<TargetInfo> set =
        classes.stream()
            .map(c -> findTestTargetForClass(project, c, targets))
            .filter(Objects::nonNull)
            .collect(toImmutableSet());
    return set.size() == 1 ? Iterables.getOnlyElement(set) : null;
  }

  @Nullable
  private static TargetInfo findTestTargetForClass(
      Project project, PsiClass psiClass, Collection<TargetInfo> targets) {
    PsiClass testClass = ReadAction.compute(() -> getNonAbstractTestClass(psiClass));
    if (testClass == null) {
      return null;
    }
    PsiFile psiFile = ReadAction.compute(testClass::getContainingFile);
    if (psiFile == null) {
      return null;
    }
    TestSize testSize = ReadAction.compute(() -> TestSizeFinder.getTestSize(testClass));
    return TestTargetHeuristic.chooseTestTargetForSourceFile(
        project, psiFile, getFile(psiFile), targets, testSize);
  }

  private static PsiClass getNonAbstractTestClass(PsiElement element) {
    PsiClass testClass = ProducerUtils.getTestClass(element);
    if (testClass == null || testClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }
    return testClass;
  }

  private static File getFile(PsiFile psiFile) {
    return new File(psiFile.getViewProvider().getVirtualFile().getPath());
  }

  private static boolean futureEmpty(@Nullable ListenableFuture<?> future) {
    return future == null
        || future.isCancelled()
        || (future.isDone() && FuturesUtil.getIgnoringErrors(future) == null);
  }
}
