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
public class AndroidCompletionTestGenerated extends AbstractAndroidCompletionTest {

    public void testFqNameInAttr() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/fqNameInAttr/");
        doTest(fileName);
    }

    public void testFqNameInAttrFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/fqNameInAttrFragment/");
        doTest(fileName);
    }

    public void testFqNameInTag() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/fqNameInTag/");
        doTest(fileName);
    }

    public void testFqNameInTagFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/fqNameInTagFragment/");
        doTest(fileName);
    }

    public void testMultiFile() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/multiFile/");
        doTest(fileName);
    }

    public void testMultiFileFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/multiFileFragment/");
        doTest(fileName);
    }

    public void testPropertiesSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/propertiesSimple/");
        doTest(fileName);
    }

    public void testPropertiesSimpleFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/propertiesSimpleFragment/");
        doTest(fileName);
    }

    public void testPropertiesSimpleView() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/propertiesSimpleView/");
        doTest(fileName);
    }

    public void testWithoutImport() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/completion/withoutImport/");
        doTest(fileName);
    }
}
