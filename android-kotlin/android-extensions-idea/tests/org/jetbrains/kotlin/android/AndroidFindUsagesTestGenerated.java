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
public class AndroidFindUsagesTestGenerated extends AbstractAndroidFindUsagesTest {

    public void testFqNameInAttr() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/fqNameInAttr/");
        doTest(fileName);
    }

    public void testFqNameInAttrFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/fqNameInAttrFragment/");
        doTest(fileName);
    }

    public void testFqNameInTag() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/fqNameInTag/");
        doTest(fileName);
    }

    public void testFqNameInTagFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/fqNameInTagFragment/");
        doTest(fileName);
    }

    public void testMultiFile() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/multiFile/");
        doTest(fileName);
    }

    public void testMultiFileFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/multiFileFragment/");
        doTest(fileName);
    }

    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/simple/");
        doTest(fileName);
    }

    public void testSimpleFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/simpleFragment/");
        doTest(fileName);
    }

    public void testSimpleView() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/simpleView/");
        doTest(fileName);
    }

    public void testWrongIdFormat() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/findUsages/wrongIdFormat/");
        doTest(fileName);
    }
}
