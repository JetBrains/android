/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.folding;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.android.KotlinTestUtils;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidResourceFoldingTestGenerated extends AbstractAndroidResourceFoldingTest {

    public void testDimensions() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/folding/dimensions.kt");
        doTest(fileName);
    }

    public void testGetString() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/folding/getString.kt");
        doTest(fileName);
    }

    public void testPlurals() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/folding/plurals.kt");
        doTest(fileName);
    }
}
