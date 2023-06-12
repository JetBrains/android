/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.testing.TestProjectPaths.MOVE_WITH_RESOURCES;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;

public class AndroidModularizeGradleTest extends AndroidGradleTestCase {

  public void test() throws Exception {
    loadProject(MOVE_WITH_RESOURCES);
    generateSources();

    Project project = getProject();
    PsiElement activity =
      JavaPsiFacade.getInstance(project).findClass("google.MainActivity", GlobalSearchScope.allScope(project));
    DataContext context = dataId -> {
      if (LangDataKeys.TARGET_MODULE.is(dataId)) {
        return TestModuleUtil.findModule(getProject(), "library");
      }
      return null;
    };

    new AndroidModularizeHandler().invoke(project, new PsiElement[]{activity}, context);

    // The layout has to move, as well as the icon drawable which is referenced from the manifest
    Lists.newArrayList(
      "src/main/res/layout/activity_main.xml",
      "src/main/res/drawable/ic_play.xml",
      "src/main/res/drawable-mdpi/ic_play_arrow_black.png",
      "src/main/res/drawable-hdpi/ic_play_arrow_black.png"
    ).forEach(this::verifyMoved);

    // Colors and strings have to be added in the corresponding values files
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <color name=\"background\">#a52222</color>\n" +
                 "</resources>",
                 getTextForFile("library/src/main/res/values/colors.xml"));
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">My Library</string>\n" +
                 "    <string name=\"hello\">@string/hello_string</string>\n" +
                 "    <string name=\"hello_string\">@string/dynamic_hello_world</string>\n" +
                 "</resources>\n",
                 getTextForFile("library/src/main/res/values/strings.xml"));

    // The manifests have to be updated because the activity moved
    assertTrue(StringUtil.equalsIgnoreWhitespaces(
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
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
      getTextForFile("library/src/main/AndroidManifest.xml")));

    assertTrue(StringUtil.equalsIgnoreWhitespaces(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "\n" +
      "    <application android:label=\"@string/app_name\"></application>\n" +
      "\n" +
      "</manifest>\n",
      getTextForFile("app/src/main/AndroidManifest.xml")));
  }

  private void verifyMoved(String relativePath) {
    VirtualFile srcFile = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).findFileByRelativePath("app/" + relativePath);
    assertFalse("Expected to have moved: " + srcFile, srcFile != null && srcFile.exists());
    VirtualFile destFile = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).findFileByRelativePath("library/" + relativePath);
    assertTrue("Expected to find: " + PlatformTestUtil.getOrCreateProjectBaseDir(getProject()) + "/library/" + relativePath,
               destFile != null && destFile.exists());
  }
}
