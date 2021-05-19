/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.testFramework.TestDataPath;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidRenameTestGenerated extends AbstractAndroidRenameTest {
    public void testCommonElementId() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/commonElementId/");
        doTest(fileName);
    }

    public void testFqNameInAttr() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/fqNameInAttr/");
        doTest(fileName);
    }

    public void testFqNameInAttrFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/fqNameInAttrFragment/");
        doTest(fileName);
    }

    public void testFqNameInTag() throws Exception {
        // Renaming synthetic elements is no longer supported when renaming resources.
        if (StudioFlags.RESOLVE_USING_REPOS.get()) { return; }
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/fqNameInTag/");
        doTest(fileName);
    }

    public void testFqNameInTagFragment() throws Exception {
        // Renaming synthetic elements is no longer supported when renaming resources.
        if (StudioFlags.RESOLVE_USING_REPOS.get()) { return; }
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/fqNameInTagFragment/");
        doTest(fileName);
    }

    public void testMultiFile() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/multiFile/");
        doTest(fileName);
    }

    public void testMultiFileFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/multiFileFragment/");
        doTest(fileName);
    }

    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/simple/");
        doTest(fileName);
    }

    public void testSimpleFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/simpleFragment/");
        doTest(fileName);
    }

    public void testSimpleView() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/rename/simpleView/");
        doTest(fileName);
    }
}
