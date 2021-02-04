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
public class AndroidGotoTestGenerated extends AbstractAndroidGotoTest {

    public void testCustomNamespaceName() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/customNamespaceName/");
        doTest(fileName);
    }

    public void testFqNameInAttr() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/fqNameInAttr/");
        doTest(fileName);
    }

    public void testFqNameInAttrFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/fqNameInAttrFragment/");
        doTest(fileName);
    }

    public void testFqNameInTag() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/fqNameInTag/");
        doTest(fileName);
    }

    public void testFqNameInTagFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/fqNameInTagFragment/");
        doTest(fileName);
    }

    public void testMultiFile() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/multiFile/");
        doTest(fileName);
    }

    public void testMultiFileFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/multiFileFragment/");
        doTest(fileName);
    }

    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/simple/");
        doTest(fileName);
    }

    public void testSimpleFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/simpleFragment/");
        doTest(fileName);
    }

    public void testSimpleView() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/goto/simpleView/");
        doTest(fileName);
    }
}
