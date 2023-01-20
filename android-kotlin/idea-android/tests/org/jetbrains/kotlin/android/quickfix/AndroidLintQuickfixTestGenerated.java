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

        public void testNullableType() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/nullableType.kt");
            doTest(fileName);
        }

        public void testSimple() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/lintQuickfix/findViewById/simple.kt");
            doTest(fileName);
        }
    }
}
