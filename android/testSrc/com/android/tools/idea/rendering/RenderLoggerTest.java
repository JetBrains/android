/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.rendering;

import static com.android.tools.idea.rendering.RenderLogger.RENDER_PROBLEMS_LIMIT;
import static com.android.tools.idea.rendering.RenderLogger.STACK_OVERFLOW_TRACE_LIMIT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class RenderLoggerTest {
  @Rule
  public final TestName nameRule = new TestName();

  @After
  public void tearDown() {
    RenderLogger.resetFidelityErrorsFilters();
  }

  @Test
  public void testFidelityWarningIgnore() {
    final String TAG = "TEST_WARNING";
    final String TEXT = "Test fidelity warning";

    RenderLogger logger = new RenderLogger(null, null);
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    // Check that duplicates are ignored
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    assertEquals(1, logger.getFidelityWarnings().size());
    assertEquals(TAG, logger.getFidelityWarnings().get(0).getTag());
    assertEquals(TEXT, logger.getFidelityWarnings().get(0).getClientData());

    // Test ignoring a single message
    logger = new RenderLogger(null, null);
    RenderLogger.ignoreFidelityWarning(TEXT);
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    assertEquals(0, logger.getFidelityWarnings().size());
    logger.fidelityWarning(TAG, "This should't be ignored", null, null, null);
    assertEquals("This should't be ignored", logger.getFidelityWarnings().get(0).getClientData());

    // Test ignore all
    logger = new RenderLogger(null, null);
    RenderLogger.ignoreAllFidelityWarnings();
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    logger.fidelityWarning(TAG, "This should be ignored", null, null, null);
    logger.fidelityWarning(TAG, "And this", new Throwable("Test"), null, null);
    assertEquals(0, logger.getFidelityWarnings().size());
  }

  @Test
  public void testMessageLogging() {
    RenderLogger logger = new RenderLogger(null, null, null);
    for (int i = 0; i < 20; i++) {
      logger.error("TAG" + i, "Message " + i, null, null, null);
    }

    List<RenderProblem> problems = logger.getMessages();
    assertEquals(20, problems.size());
    for (int i = 0; i < 20; i++) {
      assertEquals("TAG" + i, problems.get(i).getTag());
      assertEquals("Message " + i, problems.get(i).getHtml());
    }
  }

  /**
   * Throws an exception that contains at least `length` [StackTraceElement]s.
   *
   * @param writableStackTrace whether the stack trace of the generated exception should be writable
   */
  private static void generateLongStackOverflowException(int length) {
    if (length > 0) {
      generateLongStackOverflowException(length - 1);
    }

    throw new StackOverflowError();
  }

  /**
   * Check that very long {@link StackOverflowError} exceptions are correctly summarized to not waste memory.
   */
  @Test
  public void testStackOverflowSummarizing() {
    RenderLogger logger = new RenderLogger(null, null, null);
    try {
      generateLongStackOverflowException(STACK_OVERFLOW_TRACE_LIMIT + 200);
    }
    catch (StackOverflowError e) {
      logger.error("TAG", "StackOverflow", e, null, null);
    }

    RenderProblem stackOverFlowProblem = Iterables.getOnlyElement(logger.getMessages());
    StackOverflowError overflowError = (StackOverflowError)stackOverFlowProblem.getThrowable();
    assertEquals(STACK_OVERFLOW_TRACE_LIMIT + 1, overflowError.getStackTrace().length);
    StackTraceElement omitted = overflowError.getStackTrace()[STACK_OVERFLOW_TRACE_LIMIT/2];
    assertEquals("omitted", omitted.getClassName());
    assertEquals("omitted", omitted.getMethodName());
    assertEquals("omitted", omitted.getFileName());

    StackTraceElement notOmitted = overflowError.getStackTrace()[STACK_OVERFLOW_TRACE_LIMIT/2 - 1];
    assertEquals(RenderLoggerTest.class.getName(), notOmitted.getClassName());
    assertEquals("generateLongStackOverflowException", notOmitted.getMethodName());
    Set<String> methodNames = Arrays.stream(overflowError.getStackTrace())
      .map(StackTraceElement::getMethodName)
      .collect(Collectors.toSet());
    // Check that we keep the beginning of the stacktrace by checking the test method name is in there
    assertTrue(methodNames.contains(nameRule.getMethodName()));
  }

  /**
   * Check that very long {@link StackOverflowError} exceptions are correctly summarized to not waste memory when reported as part
   * of a broken class load.
   */
  @Test
  public void testStackOverflowSummarizingOnBrokenClasses() {
    RenderLogger logger = new RenderLogger(null, null, null);
    try {
      generateLongStackOverflowException(STACK_OVERFLOW_TRACE_LIMIT + 200);
    }
    catch (StackOverflowError e) {
      logger.addBrokenClass("TestClass", e);
    }

    StackOverflowError overflowError = (StackOverflowError)Iterables.getOnlyElement(logger.getBrokenClasses().values());
    // Ensure that the omitted trace has been generated
    assertTrue(Iterables.any(Arrays.asList(overflowError.getStackTrace()), element -> "omitted".equals(element.getClassName()) &&
                                                                                      "omitted".equals(element.getMethodName()) &&
                                                                                      "omitted".equals(element.getFileName())));
  }

  /**
   * Check that the number of {@link RenderProblem}s is limited in the render logger. When the limit is hit, we just log one extra
   * problem indicating that more problems were found.
   */
  @Test
  public void testMessageOverflow() {
    RenderLogger logger = new RenderLogger(null, null, null);
    for (int i = 0; i < RENDER_PROBLEMS_LIMIT + 150; i++) {
      logger.error("TAG", "Message " + i, null, null, null);
    }

    List<RenderProblem> problems = logger.getMessages();
    assertEquals(RENDER_PROBLEMS_LIMIT + 1, problems.size());
    RenderProblem tooManyProblemsElement = problems.get(problems.size() - 1);
    assertEquals(HighlightSeverity.WARNING, ProblemSeverities.toHighlightSeverity(tooManyProblemsElement.getSeverity()));
    assertEquals("Too many errors (150 more errors not displayed)", tooManyProblemsElement.getHtml());
  }

  @Test
  public void testHasErrors() {
    RenderLogger logger = new RenderLogger(null, null, null);

    logger.warning("TAG_WARNING", "This is a warning", null, null);
    assertFalse(logger.hasErrors());

    logger.error("TAG_ERROR", "This is an error", null, null);
    assertTrue(logger.hasErrors());
  }
}