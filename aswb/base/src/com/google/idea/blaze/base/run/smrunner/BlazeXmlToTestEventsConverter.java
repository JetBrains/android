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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.ErrorOrFailureOrSkipped;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestCase;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.google.idea.blaze.base.run.smrunner.TestComparisonFailureParser.BlazeComparisonFailureData;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;

/** Converts blaze test runner xml logs to smRunner events. */
public class BlazeXmlToTestEventsConverter extends OutputToGeneralTestEventsConverter {
  private static final ErrorOrFailureOrSkipped NO_ERROR = new ErrorOrFailureOrSkipped();
  private static final BoolExperiment removeZeroRunTimeCheck =
      new BoolExperiment("remove.zero.run.time.check", true);

  {
    NO_ERROR.message = "No message"; // cannot be null
  }

  private final BlazeTestResultFinderStrategy testResultFinderStrategy;

  public BlazeXmlToTestEventsConverter(
      String testFrameworkName,
      TestConsoleProperties testConsoleProperties,
      BlazeTestResultFinderStrategy testResultFinderStrategy) {
    super(testFrameworkName, testConsoleProperties);
    this.testResultFinderStrategy = testResultFinderStrategy;
  }

  @Override
  public void flushBufferOnProcessTermination(int exitCode) {
    super.flushBufferOnProcessTermination(exitCode);

    try {
      BlazeTestResults testResults = testResultFinderStrategy.findTestResults();
      if (testResults == BlazeTestResults.NO_RESULTS) {
        reportError(exitCode);
      } else {
        processAllTestResults(testResults);
      }
    } catch (GetArtifactsException e) {
      Logger.getInstance(this.getClass()).error(e.getMessage());
    } finally {
      testResultFinderStrategy.deleteTemporaryOutputFiles();
    }
  }

  private void processAllTestResults(BlazeTestResults testResults) {
    onStartTesting();
    getProcessor().onTestsReporterAttached();
    List<ListenableFuture<ParsedTargetResults>> futures = new ArrayList<>();
    for (Label label : testResults.perTargetResults.keySet()) {
      futures.add(
          FetchExecutor.EXECUTOR.submit(
              () -> parseTestXml(label, testResults.perTargetResults.get(label))));
    }
    List<ParsedTargetResults> parsedResults =
        FuturesUtil.getIgnoringErrors(Futures.allAsList(futures));
    if (parsedResults != null) {
      parsedResults.forEach(this::processParsedTestResults);
    }
  }

  private void reportError(int exitCode) {
    BlazeTestExitStatus exitStatus = BlazeTestExitStatus.forExitCode(exitCode);
    if (exitStatus == null) {
      reportTestRuntimeError(
          "Unknown Error", "Test runtime terminated unexpectedly with exit code " + exitCode + ".");
    } else {
      reportTestRuntimeError(exitStatus.title, exitStatus.message);
    }
  }

  /** Parsed test output for a single target. */
  private static class ParsedTargetResults {
    private final Label label;
    private final Collection<BlazeTestResult> results;
    private final List<BlazeArtifact> outputFiles;
    private final List<TestSuite> targetSuites;

    ParsedTargetResults(
        Label label,
        Collection<BlazeTestResult> results,
        List<BlazeArtifact> outputFiles,
        List<TestSuite> targetSuites) {
      this.label = label;
      this.results = results;
      this.outputFiles = outputFiles;
      this.targetSuites = targetSuites;
    }
  }

  /** Parse all test XML files from a single test target. */
  private static ParsedTargetResults parseTestXml(
      Label label, Collection<BlazeTestResult> results) {
    List<BlazeArtifact> outputFiles = new ArrayList<>();
    results.forEach(result -> outputFiles.addAll(result.getOutputXmlFiles()));
    List<TestSuite> targetSuites = new ArrayList<>();
    for (BlazeArtifact file : outputFiles) {
      try (InputStream input = file.getInputStream()) {
        targetSuites.add(BlazeXmlSchema.parse(input));
      } catch (Exception e) {
        // ignore parsing errors -- most common cause is user cancellation, which we can't easily
        // recognize.
      }
    }
    return new ParsedTargetResults(label, results, outputFiles, targetSuites);
  }

  /** Process all parsed test XML files from a single test target. */
  private void processParsedTestResults(ParsedTargetResults parsedResults) {
    if (noUsefulOutput(parsedResults.results, parsedResults.outputFiles)) {
      Optional<TestStatus> status =
          parsedResults.results.stream().map(BlazeTestResult::getTestStatus).findFirst();
      status.ifPresent(
          testStatus -> reportTargetWithoutOutputFiles(parsedResults.label, testStatus));
      return;
    }

    Kind kind =
        parsedResults.results.stream()
            .map(BlazeTestResult::getTargetKind)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    BlazeTestEventsHandler eventsHandler =
        BlazeTestEventsHandler.getHandlerForTargetKindOrFallback(kind);
    TestSuite suite =
        parsedResults.targetSuites.size() == 1
            ? parsedResults.targetSuites.get(0)
            : BlazeXmlSchema.mergeSuites(parsedResults.targetSuites);
    processTestSuite(getProcessor(), eventsHandler, parsedResults.label, kind, suite);
  }

  /**
   * If an error occurred when running the test the user should be informed with sensible error
   * messages to help them decide what to do next. (e.g. re-run the test?)
   */
  private void reportTestRuntimeError(String errorName, String errorMessage) {
    GeneralTestEventsProcessor processor = getProcessor();
    processor.onTestFailure(
        getTestFailedEvent(errorName, errorMessage, null, BlazeComparisonFailureData.NONE, 0));
  }

  /** Return false if there's output XML which should be parsed. */
  private static boolean noUsefulOutput(
      Collection<BlazeTestResult> results, List<BlazeArtifact> outputFiles) {
    if (outputFiles.isEmpty()) {
      return true;
    }
    TestStatus status =
        results.stream().map(BlazeTestResult::getTestStatus).findFirst().orElse(null);
    return status != null && BlazeTestResult.NO_USEFUL_OUTPUT.contains(status);
  }

  /**
   * If there are no output files, the test may have failed to build, or timed out. Provide a
   * suitable message in the test UI.
   */
  private void reportTargetWithoutOutputFiles(Label label, TestStatus status) {
    if (status == TestStatus.PASSED) {
      // Empty test targets do not produce output XML, yet technically pass. Ignore them.
      return;
    }
    GeneralTestEventsProcessor processor = getProcessor();
    TestSuiteStarted suiteStarted = new TestSuiteStarted(label.toString());
    processor.onSuiteStarted(new TestSuiteStartedEvent(suiteStarted, /*locationUrl=*/ null));
    String targetName = label.targetName().toString();
    processor.onTestStarted(new TestStartedEvent(targetName, /*locationUrl=*/ null));
    processor.onTestFailure(
        getTestFailedEvent(
            targetName,
            STATUS_EXPLANATIONS.getOrDefault(status, "No output found for test target.")
                + " See console output for details",
            /* content= */ null,
            BlazeComparisonFailureData.NONE,
            /* duration= */ 0));
    processor.onTestFinished(new TestFinishedEvent(targetName, /*duration=*/ 0L));
    processor.onSuiteFinished(new TestSuiteFinishedEvent(label.toString()));
  }

  /** Status explanations for tests without output XML. */
  private static final ImmutableMap<TestStatus, String> STATUS_EXPLANATIONS =
      new ImmutableMap.Builder<TestStatus, String>()
          .put(TestStatus.TIMEOUT, "Test target timed out.")
          .put(TestStatus.INCOMPLETE, "Test output was incomplete.")
          .put(TestStatus.REMOTE_FAILURE, "Remote failure during test execution.")
          .put(TestStatus.FAILED_TO_BUILD, "Test target failed to build.")
          .put(TestStatus.TOOL_HALTED_BEFORE_TESTING, "Test target failed to build.")
          .put(TestStatus.NO_STATUS, "No output found for test target.")
          .build();

  private static void processTestSuite(
      GeneralTestEventsProcessor processor,
      BlazeTestEventsHandler eventsHandler,
      Label label,
      @Nullable Kind kind,
      TestSuite suite) {
    if (!hasRunChild(suite)) {
      return;
    }
    // only include the innermost 'testsuite' element
    boolean logSuite = !eventsHandler.ignoreSuite(label, kind, suite);
    if (suite.name != null && logSuite) {
      TestSuiteStarted suiteStarted =
          new TestSuiteStarted(eventsHandler.suiteDisplayName(label, kind, suite.name));
      String locationUrl = eventsHandler.suiteLocationUrl(label, kind, suite.name);
      processor.onSuiteStarted(new TestSuiteStartedEvent(suiteStarted, locationUrl));
    }

    for (TestSuite child : suite.testSuites) {
      processTestSuite(processor, eventsHandler, label, kind, child);
    }
    for (TestSuite decorator : suite.testDecorators) {
      processTestSuite(processor, eventsHandler, label, kind, decorator);
    }
    for (TestCase test : suite.testCases) {
      processTestCase(processor, eventsHandler, label, kind, suite, test);
    }

    if (suite.sysOut != null) {
      processor.onUncapturedOutput(suite.sysOut, ProcessOutputTypes.STDOUT);
    }
    if (suite.sysErr != null) {
      processor.onUncapturedOutput(suite.sysErr, ProcessOutputTypes.STDERR);
    }

    if (suite.name != null && logSuite) {
      processor.onSuiteFinished(
          new TestSuiteFinishedEvent(eventsHandler.suiteDisplayName(label, kind, suite.name)));
    }
  }

  /**
   * Does the test suite have at least one child which wasn't skipped? <br>
   * This prevents spurious warnings from entirely filtered test classes.
   */
  private static boolean hasRunChild(TestSuite suite) {
    for (TestSuite child : suite.testSuites) {
      if (hasRunChild(child)) {
        return true;
      }
    }
    for (TestSuite child : suite.testDecorators) {
      if (hasRunChild(child)) {
        return true;
      }
    }
    for (TestCase test : suite.testCases) {
      if (wasRun(test) && !isIgnored(test)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCancelled(TestCase test) {
    return "interrupted".equalsIgnoreCase(test.result) || "cancelled".equalsIgnoreCase(test.result);
  }

  private static boolean wasRun(TestCase test) {
    if (test.status != null) {
      return test.status.equals("run");
    }
    // 'status' is not always set. In cases where it's not,
    if (removeZeroRunTimeCheck.getValue() && bazelIsAtLeastVersion(0, 13, 0)) {
      // bazel 0.13.0 and after, tests which aren't run are skipped from the XML entirely.
      return true;
    } else {
      // before 0.13.0, tests which aren't run have a 0 runtime.
      return parseTimeMillis(test.time) != 0;
    }
  }

  static boolean isIgnored(TestCase test) {
    if (test.skipped != null) {
      return true;
    }
    return "suppressed".equalsIgnoreCase(test.result)
        || "skipped".equalsIgnoreCase(test.result)
        || "filtered".equalsIgnoreCase(test.result);
  }

  private static boolean isFailed(TestCase test) {
    return !test.failures.isEmpty() || !test.errors.isEmpty();
  }

  private static void processTestCase(
      GeneralTestEventsProcessor processor,
      BlazeTestEventsHandler eventsHandler,
      Label label,
      @Nullable Kind kind,
      TestSuite parent,
      TestCase test) {
    if (test.name == null || !wasRun(test) || isCancelled(test)) {
      return;
    }
    String displayName = eventsHandler.testDisplayName(label, kind, test.name);
    String locationUrl =
        eventsHandler.testLocationUrl(label, kind, parent.name, test.name, test.classname);
    processor.onTestStarted(new TestStartedEvent(displayName, locationUrl));

    if (test.sysOut != null) {
      processor.onTestOutput(new TestOutputEvent(displayName, test.sysOut, true));
    }
    if (test.sysErr != null) {
      processor.onTestOutput(new TestOutputEvent(displayName, test.sysErr, true));
    }

    if (isIgnored(test)) {
      ErrorOrFailureOrSkipped err = test.skipped != null ? test.skipped : NO_ERROR;
      String message = err.message == null ? "" : err.message;
      processor.onTestIgnored(
          new TestIgnoredEvent(displayName, message, BlazeXmlSchema.getErrorContent(err)));
    } else if (isFailed(test)) {
      List<ErrorOrFailureOrSkipped> errors =
          !test.failures.isEmpty()
              ? test.failures
              : !test.errors.isEmpty() ? test.errors : ImmutableList.of(NO_ERROR);
      for (ErrorOrFailureOrSkipped err : errors) {
        processor.onTestFailure(getTestFailedEvent(displayName, err, parseTimeMillis(test.time)));
      }
    }
    processor.onTestFinished(new TestFinishedEvent(displayName, parseTimeMillis(test.time)));
  }

  /**
   * Remove any duplicate copy of the brief error message from the detailed error content (generally
   * including a stack trace).
   */
  @Nullable
  private static String pruneErrorMessage(@Nullable String message, @Nullable String content) {
    if (message == null) {
      return null;
    }
    return content != null ? content.replace(message, "") : null;
  }

  private static TestFailedEvent getTestFailedEvent(
      String name, ErrorOrFailureOrSkipped error, long duration) {
    String message =
        error.message != null ? error.message : "Test failed (no error message present)";
    String content = pruneErrorMessage(error.message, BlazeXmlSchema.getErrorContent(error));
    return getTestFailedEvent(name, message, content, parseComparisonData(error), duration);
  }

  private static TestFailedEvent getTestFailedEvent(
      String name,
      String message,
      @Nullable String content,
      BlazeComparisonFailureData comparisonData,
      long duration) {
    return new TestFailedEvent(
        name,
        null,
        message,
        content,
        true,
        comparisonData.actual,
        comparisonData.expected,
        null,
        null,
        false,
        false,
        duration);
  }

  private static BlazeComparisonFailureData parseComparisonData(ErrorOrFailureOrSkipped error) {
    if (error.actual != null || error.expected != null) {
      return new BlazeComparisonFailureData(
          parseComparisonString(error.actual), parseComparisonString(error.expected));
    }
    if (error.message != null) {
      return TestComparisonFailureParser.parse(error.message);
    }
    return BlazeComparisonFailureData.NONE;
  }

  @Nullable
  private static String parseComparisonString(@Nullable BlazeXmlSchema.Values values) {
    return values != null ? Joiner.on("\n").skipNulls().join(values.values) : null;
  }

  private static long parseTimeMillis(@Nullable String time) {
    if (time == null) {
      return -1;
    }
    // if the number contains a decimal point, it's a value in seconds. Otherwise in milliseconds.
    try {
      if (time.contains(".")) {
        return Math.round(Float.parseFloat(time) * 1000);
      }
      return Long.parseLong(time);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * @return true if bazel version is at least major.minor.bugfix, or if bazel version is not
   *     applicable (i.e., is blaze, or bazel developmental version).
   */
  private static boolean bazelIsAtLeastVersion(int major, int minor, int bugfix) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (Blaze.getBuildSystemName(project) == BuildSystemName.Bazel) {
        BlazeProjectData projectData =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
        if (projectData != null) {
          return projectData.getBlazeVersionData().bazelIsAtLeastVersion(major, minor, bugfix);
        }
      }
    }
    return true; // assume recent bazel by default.
  }
}
