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
package org.jetbrains.android.dom;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.io.TestFileUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public final class FileDescriptionUtilsTest extends AndroidTestCase {
  private static final Collection<String> ROOT_TAGS = Collections.singleton("GridLayout");

  private Path myAndroidManifestXml;
  private Path myContentScrollingXml;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    FileSystem fileSystem = FileSystems.getDefault();
    String basePath = myModule.getProject().getBasePath();

    myAndroidManifestXml = fileSystem.getPath(basePath, "app", "src", "main", "AndroidManifest.xml");
    myContentScrollingXml = fileSystem.getPath(basePath, "app", "src", "main", "res", "layout", "content_scrolling.xml");
  }

  public void testNewIsResourceOfTypeComputableProjectIsDisposed() {
    Project project = Mockito.mock(Project.class);
    Mockito.when(project.isDisposed()).thenReturn(true);

    XmlFile file = Mockito.mock(XmlFile.class);
    Mockito.when(file.getProject()).thenReturn(project);

    assertFalse(FileDescriptionUtils.newIsResourceOfTypeComputable(file, ResourceFolderType.LAYOUT, Collections.emptySet()).compute());
  }

  public void testNewIsResourceOfTypeComputableFileIsNotInResourceSubdirectory() throws IOException {
    TestFileUtils.writeFileAndRefreshVfs(myAndroidManifestXml, "");

    XmlFile file = getXmlFile(myAndroidManifestXml);
    assertFalse(FileDescriptionUtils.newIsResourceOfTypeComputable(file, ResourceFolderType.LAYOUT, Collections.emptySet()).compute());
  }

  public void testNewIsResourceOfTypeComputableRootTagsIsEmpty() throws IOException {
    TestFileUtils.writeFileAndRefreshVfs(myAndroidManifestXml, "");
    TestFileUtils.writeFileAndRefreshVfs(myContentScrollingXml, "");

    XmlFile file = getXmlFile(myContentScrollingXml);
    assertTrue(FileDescriptionUtils.newIsResourceOfTypeComputable(file, ResourceFolderType.LAYOUT, Collections.emptySet()).compute());
  }

  public void testNewIsResourceOfTypeComputableRootTagsDoesNotContainRootTag() throws IOException {
    TestFileUtils.writeFileAndRefreshVfs(myAndroidManifestXml, "");

    TestFileUtils.writeFileAndRefreshVfs(
      myContentScrollingXml,

      "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\" />\n");

    XmlFile file = getXmlFile(myContentScrollingXml);
    assertFalse(FileDescriptionUtils.newIsResourceOfTypeComputable(file, ResourceFolderType.LAYOUT, ROOT_TAGS).compute());
  }

  public void testNewIsResourceOfTypeComputableRootTagsContainsRootTag() throws IOException {
    TestFileUtils.writeFileAndRefreshVfs(myAndroidManifestXml, "");

    TestFileUtils.writeFileAndRefreshVfs(
      myContentScrollingXml,

      "<GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\" />\n");

    XmlFile file = getXmlFile(myContentScrollingXml);
    assertTrue(FileDescriptionUtils.newIsResourceOfTypeComputable(file, ResourceFolderType.LAYOUT, ROOT_TAGS).compute());
  }

  @NotNull
  private XmlFile getXmlFile(@NotNull Path path) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path.toString());
    assert virtualFile != null;

    XmlFile xmlFile = (XmlFile)PsiManager.getInstance(myModule.getProject()).findFile(virtualFile);
    assert xmlFile != null;

    return xmlFile;
  }
}
