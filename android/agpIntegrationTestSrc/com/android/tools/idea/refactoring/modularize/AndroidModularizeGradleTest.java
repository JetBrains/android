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

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.getTextForFile;
import static com.android.tools.idea.testing.TestProjectPaths.MOVE_WITH_RESOURCES;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

@RunsInEdt
public class AndroidModularizeGradleTest {
  AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public TestRule rule = RuleChain.outerRule(projectRule).around(new EdtRule());

  @Test
  public void test() throws Exception {
    projectRule.loadProject(MOVE_WITH_RESOURCES);
    projectRule.generateSources();
    Project project = projectRule.getProject();
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    PsiElement activity =
      JavaPsiFacade.getInstance(project).findClass("google.MainActivity", GlobalSearchScope.allScope(project));
    DataContext context = SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_MODULE, TestModuleUtil.findModule(project, "library"));

    new AndroidModularizeHandler().invoke(project, new PsiElement[]{activity}, context);

    // The layout has to move, as well as the icon drawable which is referenced from the manifest
    Lists.newArrayList(
      "src/main/res/layout/activity_main.xml",
      "src/main/res/drawable/ic_play.xml",
      "src/main/res/drawable-mdpi/ic_play_arrow_black.png",
      "src/main/res/drawable-hdpi/ic_play_arrow_black.png"
    ).forEach(this::verifyMoved);

    // Colors and strings have to be added in the corresponding values files
    assertThat(getTextForFile(project, "library/src/main/res/values/colors.xml"))
      .isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <color name=\"background\">#a52222</color>\n" +
                 "</resources>");
    assertThat(getTextForFile(project, "library/src/main/res/values/strings.xml"))
      .isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">My Library</string>\n" +
                 "    <string name=\"hello\">@string/hello_string</string>\n" +
                 "    <string name=\"hello_string\">@string/dynamic_hello_world</string>\n" +
                 "</resources>\n");

    // The manifests have to be updated because the activity moved
    assertThat(StringUtil.equalsIgnoreWhitespaces(
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
      getTextForFile(project, "library/src/main/AndroidManifest.xml")))
      .isTrue();

    assertThat(StringUtil.equalsIgnoreWhitespaces(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "\n" +
      "    <application android:label=\"@string/app_name\"></application>\n" +
      "\n" +
      "</manifest>\n",
      getTextForFile(project, "app/src/main/AndroidManifest.xml")))
      .isTrue();
  }

  private void verifyMoved(String relativePath) {
    Project project = projectRule.getProject();
    VirtualFile srcFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).findFileByRelativePath("app/" + relativePath);
    assertThat(srcFile != null && srcFile.exists()).named("Expected to have moved: " + srcFile).isFalse();
    VirtualFile destFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).findFileByRelativePath("library/" + relativePath);
    assertThat(destFile != null && destFile.exists())
      .named("Expected to find: " + PlatformTestUtil.getOrCreateProjectBaseDir(project) + "/library/" + relativePath)
      .isTrue();
  }
}
