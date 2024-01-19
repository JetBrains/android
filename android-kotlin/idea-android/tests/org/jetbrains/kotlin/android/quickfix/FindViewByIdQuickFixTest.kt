/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.android.quickfix

import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.TestDataPath

@TestDataPath("\$PROJECT_ROOT")
class FindViewByIdQuickFixTest : AbstractAndroidLintQuickfixTest() {
    fun testAlreadyHasTypeArgument() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/alreadyHasTypeArgument.kt"))
    }

    fun testCastDoesNotSatisfyTypeParameterBounds() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/castDoesNotSatisfyTypeParameterBounds.kt"))
    }

    fun testFindViewWithTag() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/findViewWithTag.kt"))
    }

    fun testNotReturningTypeParameter() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/notReturningTypeParameter.kt"))
    }

    fun testNoTypeParameter() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/noTypeParameter.kt"))
    }

    fun testNullableType() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/nullableType.kt"))
    }

    fun testParenthesized() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/parenthesized.kt"))
    }

    fun testPlatformNotNullExpression() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/platformNotNullExpression.kt"))
    }

    fun testPlatformNullableExpression() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/platformNullableExpression.kt"))
    }

    fun testQualifiedRequireView() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/qualifiedRequireView.kt"))
    }

    fun testRequireView() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/requireView.kt"))
    }

    fun testRequireViewNullable() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/requireViewNullable.kt"))
    }

    fun testSafeCallOfNotNullFunction() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunction.kt"))
    }

    fun testSafeCallOfNotNullFunctionWithNonNullCast() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunctionWithNonNullCast.kt"))
    }

    fun testSafeCast() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/safeCast.kt"))
    }

    fun testSimple() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/simple.kt"))
    }

    fun testTooManyTypeParameters() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/tooManyTypeParameters.kt"))
    }

    fun testVariableTypeAlreadyExists() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/variableTypeAlreadyExists.kt"))
    }

    private fun testFile(@TestDataFile testFile: String): String {
        return testFile
    }
}
