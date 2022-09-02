/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.android.KotlinTestUtils;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidLintQuickfixTestGenerated {

    @TestDataPath("$PROJECT_ROOT")
    public static class FindViewById extends AbstractAndroidLintQuickfixTest {

        public void testNullableType() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/nullableType.kt");
            doTest(fileName);
        }

        public void testSimple() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/simple.kt");
            doTest(fileName);
        }
    }

    @TestDataPath("$PROJECT_ROOT")
    public static class Parcelable extends AbstractAndroidLintQuickfixTest {

        public void testMissingCreator() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/parcelable/missingCreator.kt");
            doTest(fileName);
        }

        public void testNoImplementation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/parcelable/noImplementation.kt");
            doTest(fileName);
        }
    }

    @TestDataPath("$PROJECT_ROOT")
    public static class RequiresApi extends AbstractAndroidLintQuickfixTest {

        public void testAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/annotation.kt");
            doTest(fileName);
        }

        public void testCompanion() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/companion.kt");
            doTest(fileName);
        }

        public void testDefaultParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/defaultParameter.kt");
            doTest(fileName);
        }

        public void testExtend() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/extend.kt");
            doTest(fileName);
        }

        public void testFunctionLiteral() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/functionLiteral.kt");
            doTest(fileName);
        }

        public void testInlinedConstant() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/inlinedConstant.kt");
            doTest(fileName);
        }

        public void testMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/method.kt");
            doTest(fileName);
        }

        public void testProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/property.kt");
            doTest(fileName);
        }

        public void testTopLevelProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/topLevelProperty.kt");
            doTest(fileName);
        }

        public void testWhen() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/requiresApi/when.kt");
            doTest(fileName);
        }
    }

    @TestDataPath("$PROJECT_ROOT")
    public static class SuppressLint extends AbstractAndroidLintQuickfixTest {
        public void testActivityMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/activityMethod.kt");
            doTest(fileName);
        }

        public void testAddToExistingAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/addToExistingAnnotation.kt");
            doTest(fileName);
        }

        public void testConstructorParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/constructorParameter.kt");
            doTest(fileName);
        }

        public void testDestructuringDeclaration() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/destructuringDeclaration.kt");
            doTest(fileName);
        }

        public void testLambdaArgument() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/lambdaArgument.kt");
            doTest(fileName);
        }

        public void testLambdaArgumentProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/lambdaArgumentProperty.kt");
            doTest(fileName);
        }

        public void testMethodParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/methodParameter.kt");
            doTest(fileName);
        }

        public void testPropertyWithLambda() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/propertyWithLambda.kt");
            doTest(fileName);
        }

        public void testSimpleProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/suppressLint/simpleProperty.kt");
            doTest(fileName);
        }
    }

    @TestDataPath("$PROJECT_ROOT")
    public static class TargetApi extends AbstractAndroidLintQuickfixTest {

        public void testAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/annotation.kt");
            doTest(fileName);
        }

        public void testCompanion() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/companion.kt");
            doTest(fileName);
        }

        public void testDefaultParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/defaultParameter.kt");
            doTest(fileName);
        }

        public void testExtend() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/extend.kt");
            doTest(fileName);
        }

        public void testFunctionLiteral() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/functionLiteral.kt");
            doTest(fileName);
        }

        public void testInlinedConstant() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/inlinedConstant.kt");
            doTest(fileName);
        }

        public void testMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/method.kt");
            doTest(fileName);
        }

        public void testProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/property.kt");
            doTest(fileName);
        }

        public void testTopLevelProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/topLevelProperty.kt");
            doTest(fileName);
        }

        public void testWhen() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetApi/when.kt");
            doTest(fileName);
        }
    }

    @TestDataPath("$PROJECT_ROOT")
    public static class TargetVersionCheck extends AbstractAndroidLintQuickfixTest {

        public void testAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/annotation.kt");
            doTest(fileName);
        }

        public void testDefaultParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/defaultParameter.kt");
            doTest(fileName);
        }

        public void testDestructuringDeclaration() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/destructuringDeclaration.kt");
            doTest(fileName);
        }

        public void testExpressionBody() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/expressionBody.kt");
            doTest(fileName);
        }

        public void testFunctionLiteral() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/functionLiteral.kt");
            doTest(fileName);
        }

        public void testGetterWIthExpressionBody() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/getterWIthExpressionBody.kt");
            doTest(fileName);
        }

        public void testIf() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/if.kt");
            doTest(fileName);
        }

        public void testIfWithBlock() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/ifWithBlock.kt");
            doTest(fileName);
        }

        public void testInlinedConstant() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/inlinedConstant.kt");
            doTest(fileName);
        }

        public void testMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/method.kt");
            doTest(fileName);
        }

        public void testWhen() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/targetVersionCheck/when.kt");
/* b/244759724
            doTest(fileName);
b/244759724 */
        }
    }
}
