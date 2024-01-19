/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.android.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.android.KotlinTestUtils

@TestDataPath("\$PROJECT_ROOT")
class AndroidLintQuickfixTestGenerated {
  // This isn't actually lint, but TypeParameterFindViewByIdInspection
  @TestDataPath("\$PROJECT_ROOT")
  class FindViewById : AbstractAndroidLintQuickfixTest() {
    fun testAlreadyHasTypeArgument() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/alreadyHasTypeArgument.kt")
      doTest(fileName)
    }

    fun testCastDoesNotSatisfyTypeParameterBounds() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/castDoesNotSatisfyTypeParameterBounds.kt")
      doTest(fileName)
    }

    fun testFindViewWithTag() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/findViewWithTag.kt")
      doTest(fileName)
    }

    fun testNotReturningTypeParameter() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/notReturningTypeParameter.kt")
      doTest(fileName)
    }

    fun testNoTypeParameter() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/noTypeParameter.kt")
      doTest(fileName)
    }

    fun testNullableType() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/nullableType.kt")
      doTest(fileName)
    }

    fun testParenthesized() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/parenthesized.kt")
      doTest(fileName)
    }

    fun testPlatformNotNullExpression() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/platformNotNullExpression.kt")
      doTest(fileName)
    }

    fun testPlatformNullableExpression() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/platformNullableExpression.kt")
      doTest(fileName)
    }

    fun testQualifiedRequireView() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/qualifiedRequireView.kt")
      doTest(fileName)
    }

    fun testRequireView() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/requireView.kt")
      doTest(fileName)
    }

    fun testRequireViewNullable() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/requireViewNullable.kt")
      doTest(fileName)
    }

    fun testSafeCallOfNotNullFunction() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunction.kt")
      doTest(fileName)
    }

    fun testSafeCallOfNotNullFunctionWithNonNullCast() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunctionWithNonNullCast.kt")
      doTest(fileName)
    }

    fun testSafeCast() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/safeCast.kt")
      doTest(fileName)
    }

    fun testSimple() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/simple.kt")
      doTest(fileName)
    }

    fun testTooManyTypeParameters() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/tooManyTypeParameters.kt")
      doTest(fileName)
    }

    fun testVariableTypeAlreadyExists() {
      val fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/variableTypeAlreadyExists.kt")
      doTest(fileName)
    }
  }
}
