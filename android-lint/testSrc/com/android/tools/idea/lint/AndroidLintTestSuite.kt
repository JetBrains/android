package com.android.tools.idea.lint

import com.android.testutils.JarTestSuiteRunner
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
@JarTestSuiteRunner.ExcludeClasses(AndroidLintTestSuite::class) // a suite mustn't contain itself
class AndroidLintTestSuite: IdeaTestSuiteBase()