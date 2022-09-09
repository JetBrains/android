/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.refactoring.modularize;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;

import com.android.SdkConstants;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.psi.KtClass;

public class AndroidModularizeKotlinTest extends AndroidTestCase {

  private static final String BASE_PATH = "refactoring/moveWithResourcesKt/";
  private static final String LIBRARY_PATH = getAdditionalModulePath("library/");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(BASE_PATH + "/res", "res/");
    myFixture.copyDirectoryToProject(BASE_PATH + "/src", "src/");
    myFixture.copyFileToProject(BASE_PATH + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);

    myFixture.copyDirectoryToProject(BASE_PATH + "/library/res", LIBRARY_PATH + "res/");
    myFixture.copyDirectoryToProject(BASE_PATH + "/library/src", LIBRARY_PATH + "src/");
    myFixture.copyFileToProject(BASE_PATH + "/library/" + SdkConstants.FN_ANDROID_MANIFEST_XML,
                                LIBRARY_PATH + SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "library", PROJECT_TYPE_LIBRARY, false);
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testModularize() {
    PsiElement activity = ((KtLightClass) myFixture.getJavaFacade().findClass("google.MainActivity")).getKotlinOrigin();
    DataContext context = dataId -> {
      if (LangDataKeys.TARGET_MODULE.is(dataId)) {
        return TestModuleUtil.findModule(getProject(), "library");
      }
      return null;
    };

    new AndroidModularizeHandler().invoke(myFixture.getProject(), new PsiElement[]{activity}, context);

    // The layout has to move, as well as the icon drawable which is referenced from the manifest
    Lists.newArrayList(
      "res/layout/activity_main.xml",
      "res/drawable/ic_play.xml",
      "res/drawable-mdpi/ic_play_arrow_black.png",
      "res/drawable-hdpi/ic_play_arrow_black.png",
      "src/google/MainActivity.kt",
      "src/google/Util.kt"
    ).forEach(this::verifyMoved);

    // Colors and strings have to be added in the corresponding values files
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <color name=\"background\">#a52222</color>\n" +
                 "</resources>",
                 getTextForFile(LIBRARY_PATH + "res/values/colors.xml"));
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">My Library</string>\n" +
                 "    <string name=\"hello\">@string/hello_string</string>\n" +
                 "    <string name=\"hello_string\">Hello World!</string>\n" +
                 "    <string name=\"msg\">Not really needed</string>\n" +
                 "</resources>\n",
                 getTextForFile(LIBRARY_PATH + "res/values/strings.xml"));

    // The manifests have to be updated because the activity moved
    assertEquals("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    package=\"google.mylibrary\">\n" +
                 "\n" +
                 "    <application\n" +
                 "        android:allowBackup=\"true\"\n" +
                 "        android:label=\"@string/app_name\"\n" +
                 "        android:supportsRtl=\"true\">\n" +
                 "        <activity\n" +
                 "            android:name=\"google.MainActivity\"\n" +
                 "            android:icon=\"@drawable/ic_play\">\n" +
                 "            <intent-filter>\n" +
                 "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                 "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                 "            </intent-filter>\n" +
                 "        </activity>\n" +
                 "    </application>\n" +
                 "</manifest>\n",
                 getTextForFile(LIBRARY_PATH + SdkConstants.FN_ANDROID_MANIFEST_XML));

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    package=\"google\">\n" +
                 "\n" +
                 "    <application android:label=\"@string/app_name\"></application>\n" +
                 "\n" +
                 "</manifest>\n",
                 getTextForFile(SdkConstants.FN_ANDROID_MANIFEST_XML));
  }

  private void verifyMoved(String relativePath) {
    VirtualFile srcFile = myFixture.getTempDirFixture().getFile(relativePath);
    assertFalse("Expected to have moved: " + srcFile, srcFile != null && srcFile.exists());
    VirtualFile destFile = myFixture.getTempDirFixture().getFile(LIBRARY_PATH + relativePath);
    assertTrue("Expected to find: " + LIBRARY_PATH + relativePath, destFile != null && destFile.exists());
  }

  private String getTextForFile(@NotNull String relativePath) {
    VirtualFile file = myFixture.getTempDirFixture().getFile(relativePath);
    assertTrue("File was not created: " + relativePath, file != null && file.exists());
    return myFixture.getPsiManager().findFile(file).getText();
  }
}
