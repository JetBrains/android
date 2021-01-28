/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.lint;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class KotlinLintTestGenerated extends AbstractKotlinLintTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
    }

    public void testDisabled() {}
    /* TODO(b/117954721): The Kotlin Lint tests in android-kotlin are flaky due to spurious errors from the Kotlin compiler.
    public void testAlarm() throws Exception {
        runTest("idea-android/testData/android/lint/alarm.kt");
    }

    public void testApiCheck() throws Exception {
        runTest("idea-android/testData/android/lint/apiCheck.kt");
    }

    public void testCallSuper() throws Exception {
        runTest("idea-android/testData/android/lint/callSuper.kt");
    }

    public void testCloseCursor() throws Exception {
        runTest("idea-android/testData/android/lint/closeCursor.kt");
    }

    public void testCommitFragment() throws Exception {
        runTest("idea-android/testData/android/lint/commitFragment.kt");
    }

    public void testFindViewById() throws Exception {
        runTest("idea-android/testData/android/lint/findViewById.kt");
    }

    public void testJavaPerformance() throws Exception {
        runTest("idea-android/testData/android/lint/javaPerformance.kt");
    }

    public void testJavaScriptInterface() throws Exception {
        runTest("idea-android/testData/android/lint/javaScriptInterface.kt");
    }

    public void testLayoutInflation() throws Exception {
        runTest("idea-android/testData/android/lint/layoutInflation.kt");
    }

    public void testLog() throws Exception {
        runTest("idea-android/testData/android/lint/log.kt");
    }

    public void testNoInternationalSms() throws Exception {
        runTest("idea-android/testData/android/lint/noInternationalSms.kt");
    }

    public void testOverrideConcrete() throws Exception {
        runTest("idea-android/testData/android/lint/overrideConcrete.kt");
    }

    public void testParcel() throws Exception {
        runTest("idea-android/testData/android/lint/parcel.kt");
    }

    public void testSdCardTest() throws Exception {
        runTest("idea-android/testData/android/lint/sdCardTest.kt");
    }

    public void testSetJavaScriptEnabled() throws Exception {
        runTest("idea-android/testData/android/lint/setJavaScriptEnabled.kt");
    }

    public void testSharedPrefs() throws Exception {
        runTest("idea-android/testData/android/lint/sharedPrefs.kt");
    }

    public void testShowDiagnosticsWhenFileIsRed() throws Exception {
        runTest("idea-android/testData/android/lint/showDiagnosticsWhenFileIsRed.kt");
    }

    public void testSqlite() throws Exception {
        runTest("idea-android/testData/android/lint/sqlite.kt");
    }

    public void testSupportAnnotation() throws Exception {
        runTest("idea-android/testData/android/lint/supportAnnotation.kt");
    }

    public void testSystemServices() throws Exception {
        runTest("idea-android/testData/android/lint/systemServices.kt");
    }

    public void testToast() throws Exception {
        runTest("idea-android/testData/android/lint/toast.kt");
    }

    public void testValueOf() throws Exception {
        runTest("idea-android/testData/android/lint/valueOf.kt");
    }

    public void testVelocityTrackerRecycle() throws Exception {
        runTest("idea-android/testData/android/lint/velocityTrackerRecycle.kt");
    }

    public void testViewConstructor() throws Exception {
        runTest("idea-android/testData/android/lint/viewConstructor.kt");
    }

    public void testViewHolder() throws Exception {
        runTest("idea-android/testData/android/lint/viewHolder.kt");
    }

    public void testWrongAnnotation() throws Exception {
        runTest("idea-android/testData/android/lint/wrongAnnotation.kt");
    }

    public void testWrongImport() throws Exception {
        runTest("idea-android/testData/android/lint/wrongImport.kt");
    }

    public void testWrongViewCall() throws Exception {
        runTest("idea-android/testData/android/lint/wrongViewCall.kt");
    }
    TODO(b/117954721): The Kotlin Lint tests in android-kotlin are flaky due to spurious errors from the Kotlin compiler. */
}
