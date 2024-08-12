/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAarTarget.aar_import;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.android.SdkConstants;
import com.android.tools.idea.res.AarResourceRepositoryCache;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.libraries.LibraryFileBuilder;
import com.google.idea.blaze.android.libraries.UnpackedAarUtils;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.android.targetmapbuilder.NbAarTarget;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.PathUtil;
import com.intellij.util.io.URLUtil;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that R class references are properly resolved by the android plugin. */
@RunWith(JUnit4.class)
public class AswbGotoDeclarationTest extends BlazeAndroidIntegrationTestCase {
  @Before
  public void setup() {
    setProjectView(
        "directories:",
        "  java/com/foo/gallery/activities",
        "targets:",
        "  //java/com/foo/gallery/activities:activities",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");
  }

  @After
  public void clearAarCache() {
    AarResourceRepositoryCache.getInstance().clear();
  }

  @Test
  public void gotoDeclaration_withExternalResources() {
    VirtualFile mainActivity =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
            "package com.foo.gallery.activities",
            "import android.app.Activity;",
            "public class MainActivity extends Activity {",
            "  public void referenceResources() {",
            "    System.out.println(R.style.Highlight); // External resource",
            "  }",
            "}");

    // External libraries are exposed as AARs that are unpacked post-sync.  Instead of creating a
    // normal source file, we need to add it to an AAR and then expose it through the target map
    // as the resource folder of the android_library external library target.  When
    // go-to-declaration
    // is invoked on elements declared in the AAR, the IDE should open the resource file inside the
    // unpacked AAR.
    File aarContainingStylesXml =
        LibraryFileBuilder.aar(workspaceRoot, "java/com/foo/libs/libs_aar.aar")
            .addContent(
                "res/values/styles.xml",
                ImmutableList.of(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                    "<resources>",
                    "    <style name=\"Highlight\" parent=\"android:Theme.DeviceDefault\">",
                    "        <item name=\"android:textSize\">30dp</item>",
                    "        <item name=\"android:textColor\">#FF0000</item>",
                    "        <item name=\"android:textStyle\">bold</item>",
                    "    </style>",
                    "    <style name=\"Normal\" parent=\"android:Theme.DeviceDefault\">",
                    "        <item name=\"android:textSize\">15dp</item>",
                    "        <item name=\"android:textColor\">#C0C0C0</item>",
                    "    </style>",
                    "</resources>"))
            .build();

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs")
            .res_folder("//java/com/foo/libs/res", "libs_aar.aar"));
    runFullBlazeSyncWithNoIssues();

    testFixture.configureFromExistingVirtualFile(mainActivity);
    assertGotoDeclarationOpensFile(
        "Highlight", getResourceFile(aarContainingStylesXml, "values/styles.xml"));
  }

  @Test
  public void gotoDeclaration_withLocalResources() {
    VirtualFile mainActivity =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
            "package com.foo.gallery.activities",
            "import android.app.Activity;",
            "public class MainActivity extends Activity {",
            "  public void referenceResources() {",
            "    System.out.println(R.menu.settings); // Local resource",
            "  }",
            "}");

    VirtualFile settingsXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/res/menu/settings.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<menu xmlns:tools=\"http://schemas.android.com/tools\"",
            "    xmlns:android=\"http://schemas.android.com/apk/res/android\">",
            "    <item",
            "        android:id=\"@+id/action_settings\"",
            "        android:orderInCategory=\"1\"",
            "        android:title=\"@string/settings_title\"/>",
            "</menu>");

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .res("res"));
    runFullBlazeSyncWithNoIssues();

    testFixture.configureFromExistingVirtualFile(mainActivity);
    assertGotoDeclarationOpensFile("settings", settingsXml);
  }

  @Test
  public void gotoDeclaration_fromSrcToAarResources() {
    VirtualFile mainActivity =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
            "package com.foo.gallery.activities",
            "import android.app.Activity;",
            "public class MainActivity extends Activity {",
            "  public void referenceResources() {",
            "    System.out.println(R.color.aarColor);",
            "    System.out.println(R.layout.activity_aar);",
            "  }",
            "}");

    NbAarTarget aarTarget =
        aar_import("//third_party/aar:lib_aar")
            .aar("lib_aar.aar")
            .generated_jar("classes_and_libs_merged.jar");

    File aarLibraryFile =
        LibraryFileBuilder.aar(workspaceRoot, aarTarget.getAar().getRelativePath())
            .addContent(
                "res/layout/activity_aar.xml",
                ImmutableList.of(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                    "    xmlns:tools=\"http://schemas.android.com/tools\"",
                    "    android:layout_width=\"fill_parent\"",
                    "    android:layout_height=\"fill_parent\"",
                    "    android:paddingLeft=\"16dp\"",
                    "    android:paddingRight=\"16dp\"",
                    "    tools:context=\".MainActivity\" >",
                    "</RelativeLayout>"))
            .addContent(
                "res/values/colors.xml",
                ImmutableList.of(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                    "<resources>",
                    "    <color name=\"aarColor\">#ffffff</color>",
                    "</resources>"))
            .build();

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//third_party/aar:lib_aar")
            .res("res"),
        aarTarget);
    runFullBlazeSyncWithNoIssues();
    VirtualFile aarColorXml = getResourceFile(aarLibraryFile, "values/colors.xml");
    VirtualFile aarLayoutXml = getResourceFile(aarLibraryFile, "layout/activity_aar.xml");
    testFixture.configureFromExistingVirtualFile(mainActivity);
    assertGotoDeclarationOpensFile("aarColor", aarColorXml);
    assertGotoDeclarationOpensFile("activity_aar", aarLayoutXml);
  }

  @Test
  public void gotoDeclaration_fromResourceToAarResources() {
    VirtualFile colorXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/res/values/colors.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<resources>",
            "    <color name=\"primaryColor\">@color/aarColor</color>",
            "</resources>");

    NbAarTarget aarTarget =
        aar_import("//third_party/aar:lib_aar")
            .aar("lib_aar.aar")
            .generated_jar("classes_and_libs_merged.jar");
    File aarLibraryFile =
        LibraryFileBuilder.aar(workspaceRoot, aarTarget.getAar().getRelativePath())
            .addContent(
                "res/values/colors.xml",
                ImmutableList.of(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                    "<resources>",
                    "    <color name=\"aarColor\">#ffffff</color>",
                    "</resources>"))
            .build();

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .dep("//third_party/aar:lib_aar")
            .res("res"),
        aarTarget);
    runFullBlazeSyncWithNoIssues();

    VirtualFile aarColorXml = getResourceFile(aarLibraryFile, "values/colors.xml");

    testFixture.configureFromExistingVirtualFile(colorXml);
    assertGotoDeclarationOpensFile("@color/aarColor", aarColorXml);
  }

  @Test
  public void gotoDeclaration_fromAarResourceToAarResources() {
    workspace.createDirectory(new WorkspacePath("java/com/foo/gallery/activities"));
    NbAarTarget aarTarget =
        aar_import("//third_party/aar:lib_aar")
            .aar("lib_aar.aar")
            .generated_jar("classes_and_libs_merged.jar");
    File aarLibraryFile =
        LibraryFileBuilder.aar(workspaceRoot, aarTarget.getAar().getRelativePath())
            .addContent(
                "res/values/colors.xml",
                ImmutableList.of(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                    "<resources>",
                    "    <color name=\"baseColor\">#000000</color>",
                    "    <color name=\"colorPrimary\">#008577</color>",
                    "    <color name=\"colorPrimaryDark\">@color/baseColor</color>",
                    "    <string name=\"app_name\">My Application</string>",
                    "    <style name=\"AppTheme\">",
                    "        <item name=\"android:textColor\">@color/colorPrimary</item>",
                    "    </style>",
                    "</resources>"))
            .build();

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .dep("//third_party/aar:lib_aar")
            .res("res"),
        aarTarget);
    runFullBlazeSyncWithNoIssues();

    VirtualFile aarColorXml = getResourceFile(aarLibraryFile, "values/colors.xml");
    testFixture.configureFromExistingVirtualFile(aarColorXml);
    assertGotoDeclarationOpensFile("@color/baseColor", aarColorXml);
    // b/120106463
    // assertGotoDeclarationOpensFile("@color/colorPrimary", aarColorXml);
  }

  private void assertGotoDeclarationOpensFile(String highLightElement, VirtualFile expectedFile) {
    int referenceIndex = testFixture.getEditor().getDocument().getText().indexOf(highLightElement);
    PsiElement foundElement =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), referenceIndex);

    assertThat(foundElement).isNotNull();
    assertThat(foundElement.getContainingFile()).isNotNull();
    assertThat(foundElement.getContainingFile().getVirtualFile()).isEqualTo(expectedFile);
  }

  private VirtualFile getResourceFile(File aarLibraryFile, String relativePathToResourceFile) {
    String path = aarLibraryFile.getAbsolutePath();
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(path));
    String aarDirName =
        UnpackedAarUtils.generateAarDirectoryName(name, path.hashCode()) + SdkConstants.DOT_AAR;
    UnpackedAars unpackedAars = UnpackedAars.getInstance(getProject());
    File aarDir = new File(unpackedAars.getCacheDir(), aarDirName);
    File resourceDir = UnpackedAarUtils.getResDir(aarDir);
    return VirtualFileManager.getInstance()
        .findFileByUrl(
            URLUtil.FILE_PROTOCOL
                + URLUtil.SCHEME_SEPARATOR
                + resourceDir.getPath()
                + File.separator
                + relativePathToResourceFile);
  }
}
