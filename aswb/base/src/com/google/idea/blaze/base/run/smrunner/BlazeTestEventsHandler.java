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
package com.google.idea.blaze.base.run.smrunner;


import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.URLUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Stateless language-specific handling of SM runner test protocol */
public interface BlazeTestEventsHandler {

  ExtensionPointName<BlazeTestEventsHandler> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeTestEventsHandler");

  /**
   * Whether there's a {@link BlazeTestEventsHandler} applicable to the given target.
   *
   * <p>Test results will still be displayed for unhandled kinds if they're included in a test_suite
   * or multi-target Blaze invocation, where we don't know up front the languages involved.
   */
  static boolean targetsSupported(
      Project project, ImmutableList<? extends TargetExpression> targets) {
    Kind kind = getKindForTargets(project, targets);
    return Arrays.stream(EP_NAME.getExtensions()).anyMatch(handler -> handler.handlesKind(kind));
  }

  /**
   * Returns a {@link BlazeTestEventsHandler} applicable to the given target.
   *
   * <p>If no such handler exists, falls back to returning {@link BlazeGenericTestEventsHandler}.
   * This adds support for test suites / multi-target invocations, which can mix supported and
   * unsupported target kinds.
   */
  static BlazeTestEventsHandler getHandlerForTargetKindOrFallback(@Nullable Kind kind) {
    return getHandlerForTargetKind(kind).orElse(new BlazeGenericTestEventsHandler());
  }

  /**
   * Returns a {@link BlazeTestEventsHandler} applicable to the given target or {@link
   * Optional#empty()} if no such handler can be found.
   */
  static Optional<BlazeTestEventsHandler> getHandlerForTarget(
      Project project, TargetExpression target) {
    return getHandlerForTargetKind(getKindForTarget(project, target));
  }

  /**
   * Returns a {@link BlazeTestEventsHandler} applicable to the given targets or {@link
   * Optional#empty()} if no such handler can be found.
   */
  static Optional<BlazeTestEventsHandler> getHandlerForTargets(
      Project project, ImmutableList<? extends TargetExpression> targets) {
    return getHandlerForTargetKind(getKindForTargets(project, targets));
  }

  /**
   * Returns a {@link BlazeTestEventsHandler} applicable to the given target kind, or {@link
   * Optional#empty()} if no such handler can be found.
   */
  static Optional<BlazeTestEventsHandler> getHandlerForTargetKind(@Nullable Kind kind) {
    return Arrays.stream(EP_NAME.getExtensions())
        .filter(handler -> handler.handlesKind(kind))
        .findFirst();
  }

  /** Returns the single Kind shared by all targets or null if they have different kinds. */
  @Nullable
  static Kind getKindForTargets(Project project, List<? extends TargetExpression> targets) {
    // TODO(brendandouglas): extend BlazeTestEventsHandler API to handle multiple targets with
    // *known* kinds
    Kind singleKind = null;
    for (TargetExpression target : targets) {
      Kind kind = getKindForTarget(project, target);
      if (kind == null || (singleKind != null && !kind.equals(singleKind))) {
        return null;
      }
      singleKind = kind;
    }
    return singleKind;
  }

  @Nullable
  static Kind getKindForTarget(Project project, TargetExpression target) {
    if (!(target instanceof Label)) {
      return null;
    }
    TargetInfo targetInfo = TargetFinder.findTargetInfo(project, (Label) target);
    return targetInfo != null ? targetInfo.getKind() : null;
  }

  boolean handlesKind(@Nullable Kind kind);

  /**
   * A {@link SMTestLocator} to convert location URLs provided by this event handler to project PSI
   * elements. Returns {@code null} if no such conversion is available.
   */
  @Nullable
  SMTestLocator getTestLocator();

  /**
   * The --test_filter flag passed to blaze to rerun the given tests.
   *
   * @return {@code null} if no filter can be constructed for these tests
   */
  @Nullable
  String getTestFilter(Project project, List<Location<?>> testLocations);

  /** Returns {@code null} if this test events handler doesn't support test filtering. */
  @Nullable
  default AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return new BlazeRerunFailedTestsAction(this, consoleView);
  }

  /** Converts the testsuite name in the blaze test XML to a user-friendly format. */
  default String suiteDisplayName(Label label, @Nullable Kind kind, String rawName) {
    return rawName;
  }

  /** Converts the testcase name in the blaze test XML to a user-friendly format. */
  default String testDisplayName(Label label, @Nullable Kind kind, String rawName) {
    return rawName;
  }

  /** Converts the suite name to a parsable location URL. */
  default String suiteLocationUrl(Label label, @Nullable Kind kind, String name) {
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  /** Converts the test case and suite names to a parsable location URL. */
  default String testLocationUrl(
      Label label,
      @Nullable Kind kind,
      String parentSuite,
      String name,
      @Nullable String className) {
    String base = SmRunnerUtils.GENERIC_TEST_PROTOCOL + URLUtil.SCHEME_SEPARATOR;
    if (Strings.isNullOrEmpty(className)) {
      return base + name;
    }
    return base + className + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER + name;
  }

  /** Whether to skip logging a {@link TestSuite}. */
  default boolean ignoreSuite(Label label, @Nullable Kind kind, TestSuite suite) {
    // by default only include innermost 'testsuite' elements
    return !suite.testSuites.isEmpty();
  }
}
