package com.android.tools.idea.retention.actions

import com.android.testutils.JarTestSuiteRunner
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
@JarTestSuiteRunner.ExcludeClasses(TestRetentionTestSuite::class)  // a suite mustn't contain itself
class TestRetentionTestSuite : IdeaTestSuiteBase()
