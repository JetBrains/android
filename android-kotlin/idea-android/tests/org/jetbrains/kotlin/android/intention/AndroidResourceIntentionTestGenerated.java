/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.intention;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.android.KotlinTestUtils;
import org.junit.runner.RunWith;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidResourceIntentionTestGenerated extends AbstractAndroidResourceIntentionTest {

    public void testCreateColorValueResource_alreadyExists_AlreadyExists() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/createColorValueResource/alreadyExists/alreadyExists.test");
        doTest(fileName);
    }

    public void testCreateColorValueResource_simpleFunction_SimpleFunction() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/createColorValueResource/simpleFunction/simpleFunction.test");
        doTest(fileName);
    }

    public void testCreateLayoutResourceFile_alreadyExists_AlreadyExists() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/createLayoutResourceFile/alreadyExists/alreadyExists.test");
        doTest(fileName);
    }

    public void testCreateLayoutResourceFile_simpleFunction_SimpleFunction() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/createLayoutResourceFile/simpleFunction/simpleFunction.test");
        doTest(fileName);
    }

    public void testCreateStringValueResource_alreadyExists_AlreadyExists() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/createStringValueResource/alreadyExists/alreadyExists.test");
        doTest(fileName);
    }

    public void testCreateStringValueResource_simpleFunction_SimpleFunction() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/createStringValueResource/simpleFunction/simpleFunction.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_activityExtension_ActivityExtension() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/activityExtension/activityExtension.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_activityMethod_ActivityMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/activityMethod/activityMethod.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_classInActivity_ClassInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/classInActivity/classInActivity.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_extensionLambda_ExtensionLambda() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/extensionLambda/extensionLambda.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_function_Function() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/function/function.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_genericContextExtensionFunction_GenericContextExtensionFunction() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/genericContextExtensionFunction/genericContextExtensionFunction.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_innerClassInActivity_InnerClassInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/innerClassInActivity/innerClassInActivity.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_innerViewInActivity_InnerViewInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/innerViewInActivity/innerViewInActivity.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_objectInActivity_ObjectInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/objectInActivity/objectInActivity.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_objectInActivityMethod_ObjectInActivityMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/objectInActivityMethod/objectInActivityMethod.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_objectInFunction_ObjectInFunction() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/objectInFunction/objectInFunction.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_stringTemplate_StringTemplate() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/stringTemplate/stringTemplate.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_viewExtensionActivityMethod_ViewExtensionActivityMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/viewExtensionActivityMethod/viewExtensionActivityMethod.test");
        doTest(fileName);
    }

    public void testKotlinAndroidAddStringResource_viewMethod_ViewMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea-android/testData/android/resourceIntention/kotlinAndroidAddStringResource/viewMethod/viewMethod.test");
        doTest(fileName);
    }
}
