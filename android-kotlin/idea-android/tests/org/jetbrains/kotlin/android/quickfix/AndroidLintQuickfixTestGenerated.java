/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.android.KotlinTestUtils;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidLintQuickfixTestGenerated {
    // This isn't actually lint, but TypeParameterFindViewByIdInspection
    @TestDataPath("$PROJECT_ROOT")
    public static class FindViewById extends AbstractAndroidLintQuickfixTest {

        public void testAlreadyHasTypeArgument() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/alreadyHasTypeArgument.kt");
            doTest(fileName);
        }

        public void testCastDoesNotSatisfyTypeParameterBounds() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/castDoesNotSatisfyTypeParameterBounds.kt");
            doTest(fileName);
        }

        public void testFindViewWithTag() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/findViewWithTag.kt");
            doTest(fileName);
        }

        public void testNotReturningTypeParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/notReturningTypeParameter.kt");
            doTest(fileName);
        }

        public void testNoTypeParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/noTypeParameter.kt");
            doTest(fileName);
        }

        public void testNullableType() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/nullableType.kt");
            doTest(fileName);
        }

        public void testParenthesized() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/parenthesized.kt");
            doTest(fileName);
        }

        public void testPlatformNotNullExpression() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/platformNotNullExpression.kt");
            doTest(fileName);
        }

        public void testPlatformNullableExpression() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/platformNullableExpression.kt");
            doTest(fileName);
        }

        public void testQualifiedRequireView() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/qualifiedRequireView.kt");
            doTest(fileName);
        }

        public void testRequireView() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/requireView.kt");
            doTest(fileName);
        }

        public void testRequireViewNullable() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/requireViewNullable.kt");
            doTest(fileName);
        }

        public void testSafeCallOfNotNullFunction() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunction.kt");
            doTest(fileName);
        }

        public void testSafeCallOfNotNullFunctionWithNonNullCast() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunctionWithNonNullCast.kt");
            doTest(fileName);
        }

        public void testSafeCast() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/safeCast.kt");
            doTest(fileName);
        }

        public void testSimple() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/simple.kt");
            doTest(fileName);
        }

        public void testTooManyTypeParameters() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/tooManyTypeParameters.kt");
            doTest(fileName);
        }

        public void testVariableTypeAlreadyExists() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/variableTypeAlreadyExists.kt");
            doTest(fileName);
        }
    }
}
