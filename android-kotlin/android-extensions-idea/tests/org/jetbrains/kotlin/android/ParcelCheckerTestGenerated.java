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
public class ParcelCheckerTestGenerated extends AbstractParcelCheckerTest {

    public void testConstructors() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/constructors.kt");
        doTest(fileName);
    }

    public void testCustomCreator() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/customCreator.kt");
        doTest(fileName);
    }

    /* TODO(b/140137618)
    public void testCustomParcelers() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/customParcelers.kt");
        doTest(fileName);
    }
    */

    public void testCustomWriteToParcel() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/customWriteToParcel.kt");
        doTest(fileName);
    }

    public void testDelegate() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/delegate.kt");
        doTest(fileName);
    }

    public void testEmptyPrimaryConstructor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/emptyPrimaryConstructor.kt");
        doTest(fileName);
    }

    public void testKt20062() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/kt20062.kt");
        doTest(fileName);
    }

    public void testModality() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/modality.kt");
        doTest(fileName);
    }

    public void testNotMagicParcel() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/notMagicParcel.kt");
        doTest(fileName);
    }

    public void testProperties() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/properties.kt");
        doTest(fileName);
    }

    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/simple.kt");
        doTest(fileName);
    }

    public void testUnsupportedType() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/unsupportedType.kt");
        doTest(fileName);
    }

    public void testWithoutParcelableSupertype() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/withoutParcelableSupertype.kt");
        doTest(fileName);
    }

    public void testWrongAnnotationTarget() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/parcel/checker/wrongAnnotationTarget.kt");
        doTest(fileName);
    }
}
