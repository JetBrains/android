/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.android.KotlinTestUtils;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidUsageHighlightingTestGenerated extends AbstractAndroidUsageHighlightingTest {

    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/usageHighlighting/simple/");
        doTest(fileName);
    }
}
