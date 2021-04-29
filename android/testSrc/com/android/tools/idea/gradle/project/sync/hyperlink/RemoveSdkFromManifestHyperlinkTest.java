/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.facet.AndroidRootUtil.getPrimaryManifestFile;

import com.android.tools.idea.gradle.project.sync.issues.SdkInManifestIssuesReporter.SdkProperty;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.io.IOException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link RemoveSdkFromManifestHyperlink}.
 */
public class RemoveSdkFromManifestHyperlinkTest extends AndroidGradleTestCase {
  private RemoveSdkFromManifestHyperlink myHyperlink;

  public void testRemoveMinSdkVersion() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    Module appModule = getModule("app");

    // Rewrite the manifest file so that it contains minSdkVersion.
    writeManifestWithUsesSdkVersion(appModule, "<uses-sdk android:minSdkVersion='21'/>");
    // Rewrite the build file so that it contains a different minSdkVersion.
    File buildFile = new File(getProjectFolderPath(), join("app", FN_BUILD_GRADLE));
    writeBuildFile(buildFile, "minSdkVersion 23\n");

    try {
      requestSyncAndWait();
    }
    catch (Throwable expected) {
      // Sync issues are expected.
    }

    myHyperlink = new RemoveSdkFromManifestHyperlink(ImmutableList.of(appModule), SdkProperty.MIN);
    myHyperlink.execute(project);

    // Verify the text is accurate.
    assertThat(myHyperlink.toHtml()).contains("Remove minSdkVersion and sync project");

    // Verify that minSdkVersion, and the empty uses-sdk is removed from manifest file.
    String manifestContent = Files.asCharSource(getManifestFile(appModule), Charsets.UTF_8).read();
    assertThat(manifestContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      "</manifest>\n"
    );

    // Verify that minSdkVersion in build file is not changed.
    String actualBuildFileContent = Files.asCharSource(buildFile, Charsets.UTF_8).read();
    assertThat(actualBuildFileContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "apply plugin: 'com.android.application'\n" +
      "android {\n" +
      "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion 23\n" +
      "    }\n" +
      "}\n"
    );
  }

  public void testRemoveMinSdkVersionWithOtherSdkAttributes() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    Module appModule = getModule("app");

    // Rewrite the manifest file so that it contains minSdkVersion and targetSdkVersion.
    writeManifestWithUsesSdkVersion(appModule, "<uses-sdk android:minSdkVersion='21' android:targetSdkVersion='27'/>\n");

    // Rewrite the build file so that it contains a different minSdkVersion.
    File buildFile = new File(getProjectFolderPath(), join("app", FN_BUILD_GRADLE));
    writeBuildFile(buildFile, "minSdkVersion 23\n");
    try {
      requestSyncAndWait();
    }
    catch (Throwable expected) {
      // Sync issues are expected.
    }

    myHyperlink = new RemoveSdkFromManifestHyperlink(ImmutableList.of(appModule), SdkProperty.MIN);
    myHyperlink.execute(project);

    // Verify the text is accurate.
    assertThat(myHyperlink.toHtml()).contains("Remove minSdkVersion and sync project");

    // Verify that minSdkVersion is removed from manifest file, and other attributes are not changed.
    String manifestContent = Files.asCharSource(getManifestFile(appModule), Charsets.UTF_8).read();
    assertThat(manifestContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      "<uses-sdk  android:targetSdkVersion='27'/>\n" +
      "</manifest>\n"
    );

    // Verify that minSdkVersion in build file is not changed.
    String actualBuildFileContent = Files.asCharSource(buildFile, Charsets.UTF_8).read();
    assertThat(actualBuildFileContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "apply plugin: 'com.android.application'\n" +
      "android {\n" +
      "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion 23\n" +
      "    }\n" +
      "}\n"
    );
  }

  public void testMoveTargetSdkVersionWithOtherSdkAttributes() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    Module appModule = getModule("app");

    // Rewrite the manifest file so that it contains maxSdkVersion and targetSdkVersion.
    writeManifestWithUsesSdkVersion(appModule, "<uses-sdk android:maxSdkVersion='21' android:targetSdkVersion='27'/>\n");

    // Rewrite the build file so that it doesn't contain targetSdkVersion.
    File buildFile = new File(getProjectFolderPath(), join("app", FN_BUILD_GRADLE));
    writeBuildFile(buildFile, "");
    try {
      requestSyncAndWait();
    }
    catch (Throwable expected) {
      // Sync issues are expected.
    }

    myHyperlink = new RemoveSdkFromManifestHyperlink(ImmutableList.of(appModule), SdkProperty.TARGET);
    myHyperlink.execute(project);

    // Verify the text is accurate.
    assertThat(myHyperlink.toHtml()).contains("Move targetSdkVersion to build file and sync project");

    // Verify that targetSdkVersion is removed from manifest file, and other attributes are not changed.
    String manifestContent = Files.asCharSource(getManifestFile(appModule), Charsets.UTF_8).read();
    assertThat(manifestContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      "<uses-sdk android:maxSdkVersion='21' />\n" +
      "</manifest>\n"
    );

    // Verify that targetSdkVersion in build file is not changed.
    String actualBuildFileContent = Files.asCharSource(buildFile, Charsets.UTF_8).read();
    assertThat(actualBuildFileContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "apply plugin: 'com.android.application'\n" +
      "android {\n" +
      "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
      "    defaultConfig {\n" +
      "            targetSdk 27\n" +
      "    }\n" +
      "}\n"
    );
  }

  public void testMoveMinSdkVersionToBuildFile() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    Module appModule = getModule("app");

    // Rewrite the manifest file so that it contains minSdkVersion.
    writeManifestWithUsesSdkVersion(appModule, "<uses-sdk android:minSdkVersion='21'/>");

    // Rewrite the build file so that it doesn't contain minSdkVersion.
    File buildFile = new File(getProjectFolderPath(), join("app", FN_BUILD_GRADLE));
    writeBuildFile(buildFile, "");

    try {
      requestSyncAndWait();
    }
    catch (Throwable expected) {
      // Sync issues are expected.
    }

    myHyperlink = new RemoveSdkFromManifestHyperlink(ImmutableList.of(appModule), SdkProperty.MIN);
    myHyperlink.execute(project);

    // Verify the text is accurate.
    assertThat(myHyperlink.toHtml()).contains("Move minSdkVersion to build file and sync project");

    // Verify that minSdkVersion is removed from manifest file.
    String actualManifestContent = Files.asCharSource(getManifestFile(appModule), Charsets.UTF_8).read();
    assertThat(actualManifestContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      "</manifest>\n"
    );

    // Verify that minSdkVersion is added to build file.
    String actualBuildFileContent = Files.asCharSource(buildFile, Charsets.UTF_8).read();
    assertThat(actualBuildFileContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "apply plugin: 'com.android.application'\n" +
      "android {\n" +
      "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
      "    defaultConfig {\n" +
      "            minSdk 21\n" +
      "    }\n" +
      "}\n"
    );
  }

  public void testMoveMinSdkVersionOnMultipleModules() throws Exception {
    loadProject(DEPENDENT_MODULES);
    Project project = getProject();
    Module appModule = getModule("app");
    Module libModule = getModule("lib");

    // Rewrite the manifest file so that it contains minSdkVersion and targetSdkVersion.
    writeManifestWithUsesSdkVersion(appModule, "<uses-sdk android:minSdkVersion='21' android:targetSdkVersion='27'/>\n");
    writeManifestWithUsesSdkVersion(libModule, "<uses-sdk android:minSdkVersion='24' android:targetSdkVersion='26'/>\n");

    // Rewrite the build file so that it contains a different minSdkVersion.
    File appBuildFile = new File(getProjectFolderPath(), join("app", FN_BUILD_GRADLE));
    File libBuildFile = new File(getProjectFolderPath(), join("lib", FN_BUILD_GRADLE));
    writeBuildFile(appBuildFile, "minSdkVersion 23\n");
    writeBuildFile(libBuildFile, "minSdkVersion 25\n");
    try {
      requestSyncAndWait();
    }
    catch (Throwable expected) {
      // Sync issues are expected.
    }

    myHyperlink = new RemoveSdkFromManifestHyperlink(ImmutableList.of(appModule, libModule), SdkProperty.MIN);
    myHyperlink.execute(project);

    // Verify the text is accurate.
    assertThat(myHyperlink.toHtml()).contains("Remove minSdkVersion and sync project");

    // Verify that minSdkVersion is removed from manifest file, and other attributes are not changed.
    String appManifestContent = Files.asCharSource(getManifestFile(appModule), Charsets.UTF_8).read();
    assertThat(appManifestContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      "<uses-sdk  android:targetSdkVersion='27'/>\n" +
      "</manifest>\n"
    );

    String libManifestContent = Files.asCharSource(getManifestFile(libModule), Charsets.UTF_8).read();
    assertThat(libManifestContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      "<uses-sdk  android:targetSdkVersion='26'/>\n" +
      "</manifest>\n"
    );

    // Verify that minSdkVersion in build file is not changed.
    String appBuildFileContent = Files.asCharSource(appBuildFile, Charsets.UTF_8).read();
    assertThat(appBuildFileContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "apply plugin: 'com.android.application'\n" +
      "android {\n" +
      "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion 23\n" +
      "    }\n" +
      "}\n"
    );

    String libBuildFileContent = Files.asCharSource(libBuildFile, Charsets.UTF_8).read();
    assertThat(libBuildFileContent.replace(System.lineSeparator(), "\n")).isEqualTo(
      "apply plugin: 'com.android.application'\n" +
      "android {\n" +
      "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion 25\n" +
      "    }\n" +
      "}\n"
    );
  }

  private static void writeManifestWithUsesSdkVersion(@NotNull Module module, @NotNull String usesSdk) throws IOException {
    String manifestContent =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    package=\"google.simpleapplication\" >\n" +
      usesSdk +
      "</manifest>\n";
    writeToFile(getManifestFile(module), manifestContent);
  }

  @NotNull
  private static File getManifestFile(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    assertNotNull(androidFacet);
    return virtualToIoFile(getPrimaryManifestFile(androidFacet));
  }

  private void writeBuildFile(@NotNull File buildFile, @NotNull String minSdkVersion) throws IOException {
    String buildFileContent = "apply plugin: 'com.android.application'\n" +
                              "android {\n" +
                              "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
                              "    defaultConfig {\n" +
                              "        " + minSdkVersion +
                              "    }\n" +
                              "}\n";
    // We need to write the contents to the build file via the Document, as of 30/10/18 updating via the physical file or
    // virtual file results in the contents written not being picked up by the PsiFile and the ProjectBuildModel
    // still sees the old contents.
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(buildFile);
      PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      doc.setText(buildFileContent);
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    });
  }
}
