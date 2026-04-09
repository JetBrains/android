/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import kotlinx.coroutines.test.runTest
import org.junit.rules.ExternalResource

/**
 * Add this rule first in a RuleChain to ensure [after] is run after all other cleanup methods are done. This ensures that cleanup errors in
 * coroutines are caught and reported in the same test that triggered them without leaking to other tests.
 */
class TestScopeRule(private val ignore: Boolean = false) : ExternalResource() {
  /**
   * This will empty the coroutine exceptions caught globally by ExceptionCollector.
   *
   * Explanation: ExceptionCollector collects all uncaught coroutine exceptions globally for all tests. Whenever TestScope.runTest() is used
   * it will report the recorded exceptions found so far and cause a failure in the test that is using TestScope.runTest(). This is
   * confusing because the exceptions happened earlier potentially in a different unit test or even in a different source file. By running
   * TestScope.runTest() after all the cleanup of a test method is done, we can report these exceptions for the test that caused the
   * exceptions.
   */
  override fun after() {
    try {
      runTest {}
    } catch (ex: IllegalStateException) {
      if (ex.javaClass.simpleName == "UncaughtExceptionsBeforeTest") {
        if (!ignore) {
          throw UncaughtExceptionsAfterTest().apply { ex.suppressed.forEach { addSuppressed(it) } }
        }
      } else {
        throw ex
      }
    }
  }

  /**
   * Use this exception class instead of the UncaughtExceptionsBeforeTest that TestScope is using to avoid misleading the reader of the
   * exception coming from TestScopeRule.
   *
   * This is the definition of UncaughtExceptionsBeforeTest in TestScope:
   * <pre>
   *   internal class UncaughtExceptionsBeforeTest : IllegalStateException(
   *       "There were uncaught exceptions before the test started. Please avoid this," +
   *       " as such exceptions are also reported in a platform-dependent manner so that they are not lost."
   *   )
   * </pre>
   *
   * The error message reported by UncaughtExceptionsBeforeTest doesn't match the nature of the exception reported by TestScopeRule since
   * the exception is known to be thrown in the cleanup of the unit test that caused the exception(s).
   */
  private class UncaughtExceptionsAfterTest :
    IllegalStateException("There were uncaught coroutine exceptions after the test finished. Please avoid this.")
}
