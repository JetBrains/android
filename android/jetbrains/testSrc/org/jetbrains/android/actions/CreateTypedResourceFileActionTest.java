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
package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.android.AndroidTestCase;

public final class CreateTypedResourceFileActionTest extends AndroidTestCase {

  private MapDataContext myDataContext = new MapDataContext();

  public void testDoIsAvailableForTypedResourceDirectory() {
    for (ResourceFolderType folderType: ResourceFolderType.values()) {
      String filePath = "res/" + folderType.getName() + "/my_" + folderType.getName() + ".xml";
      myDataContext.put(CommonDataKeys.PSI_ELEMENT, myFixture.addFileToProject(filePath, "").getParent());
      assertTrue("Failed for " + folderType.name(), CreateTypedResourceFileAction.doIsAvailable(myDataContext, folderType.getName()));
    }

    VirtualFile resDir = myFixture.findFileInTempDir("res");
    PsiDirectory psiResDir = myFixture.getPsiManager().findDirectory(resDir);
    myDataContext.put(CommonDataKeys.PSI_ELEMENT, psiResDir);
    // Should fail when the directory is not a type specific resource directory (e.g: res/drawable).
    assertFalse(CreateTypedResourceFileAction.doIsAvailable(myDataContext, ResourceFolderType.DRAWABLE.getName()));
  }
}