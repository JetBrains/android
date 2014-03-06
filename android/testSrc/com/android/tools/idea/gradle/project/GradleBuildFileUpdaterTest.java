/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestCase;

import java.io.IOException;

import static com.android.tools.idea.gradle.parser.Dependency.Scope.COMPILE;
import static com.android.tools.idea.gradle.parser.Dependency.Type.EXTERNAL;
import static com.android.tools.idea.gradle.parser.Dependency.Type.FILES;

public class GradleBuildFileUpdaterTest extends IdeaTestCase {
  private Document myDocument;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = null;
  }

  public void testSupportLib() throws Exception {
    convertAndTest("/Applications/Android Studio.app/sdk/extras/android/m2repository/com/android/support/support-v4/19.0.1/support-v4-19.0.1.jar",
                   new Dependency(COMPILE, EXTERNAL, "com.android.support:support-v4:19.0.1"));
  }

  public void testGradleCachedPath() throws Exception {
    convertAndTest("/Users/foo/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/16.0.1/5fa98cd1a63c99a44dd8d3b77e4762b066a5d0c5/guava-16.0.1.jar",
                   new Dependency(COMPILE, EXTERNAL, "com.google.guava:guava:16.0.1"));
  }

  public void testIgnoresOldExplodedBundles() throws Exception {
    convertAndTest("/Users/foo/AndroidStudioProjects/ASProject/app/build/exploded-bundles/ComAndroidSupportAppcompatV71901.aar/classes.jar",
                   null);
  }

  public void testExplodedAar() throws Exception {
    convertAndTest("/Users/foo/AndroidStudioProjects/ASProject/app/build/exploded-aar/com.android.support/appcompat-v7/19.0.1/classes.jar",
                   new Dependency(COMPILE, EXTERNAL, "com.android.support:appcompat-v7:19.0.1@aar"));
  }

  public void testLibFile() throws Exception {
    GradleBuildFile buildFile = getBuildFile();
    VirtualFile root = buildFile.getFile().getParent();
    VirtualFile libsDir = root.createChildDirectory(this, "lib");
    VirtualFile fakeLibrary = libsDir.createChildData(this, "foo.jar");
    GradleBuildFileUpdater updater = new GradleBuildFileUpdater(myProject);
    Dependency newDep = updater.convertLibraryPathToDependency(buildFile, COMPILE, fakeLibrary);
    assertEquals(new Dependency(COMPILE, FILES, "lib/foo.jar"), newDep);
  }

  private void convertAndTest(String path, Dependency expected) throws Exception {
    GradleBuildFile buildFile = getBuildFile();
    GradleBuildFileUpdater updater = new GradleBuildFileUpdater(myProject);
    VirtualFile stubFile = getStubFile(path);
    Dependency newDep = updater.convertLibraryPathToDependency(buildFile, COMPILE, stubFile);
    if (expected == null) {
      assertNull(newDep);
    } else {
      assertEquals(expected, newDep);
    }
  }

  private GradleBuildFile getBuildFile() throws Exception {
    VirtualFile vf = getVirtualFile(createTempFile(SdkConstants.FN_BUILD_GRADLE, "dependencies {\n}"));
    myDocument = FileDocumentManager.getInstance().getDocument(vf);
    return new GradleBuildFile(vf, getProject());
  }

  private void assertContents(GradleBuildFile file, String expected) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitDocument(myDocument);
    String actual = myDocument.getText();
    assertEquals(expected, actual);
  }

  private VirtualFile getStubFile(final String path) {
    return new StubVirtualFile() {
      @Override
      public String getPath() {
        return path;
      }
    };
  }
}
