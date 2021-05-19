/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.annotator;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.android.KotlinTestUtils;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidGutterIconTestGenerated extends AbstractAndroidGutterIconTest {

    public void testColor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/gutterIcon/color.kt");
        doTest(fileName);
    }

    public void testDrawable() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/gutterIcon/drawable.kt");
        doTest(fileName);
    }

    public void testMipmap() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/gutterIcon/mipmap.kt");
        doTest(fileName);
    }

    public void testSystemColor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/gutterIcon/systemColor.kt");
        doTest(fileName);
    }

    public void testSystemDrawable() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/gutterIcon/systemDrawable.kt");
        doTest(fileName);
    }

    public void testKotlinKeyword() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/gutterIcon/kotlinKeyword.kt");
        doTest(fileName);
    }
}
