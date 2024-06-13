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

import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import java.util.Collections;
import org.jetbrains.android.AndroidTestCase;

public final class CreateTypedResourceFileActionTest extends AndroidTestCase {

  public void testDoIsAvailableForTypedResourceDirectory() {
    SimpleDataContext.Builder dataContext = SimpleDataContext.builder();
    for (ResourceFolderType folderType: ResourceFolderType.values()) {
      String filePath = "res/" + folderType.getName() + "/my_" + folderType.getName() + ".xml";
      dataContext.add(CommonDataKeys.PSI_ELEMENT, myFixture.addFileToProject(filePath, "").getParent());
      assertTrue("Failed for " + folderType.name(), CreateTypedResourceFileAction.doIsAvailable(dataContext.build(), folderType.getName()));
    }

    VirtualFile resDir = myFixture.findFileInTempDir("res");
    PsiDirectory psiResDir = myFixture.getPsiManager().findDirectory(resDir);
    dataContext.add(CommonDataKeys.PSI_ELEMENT, psiResDir);
    // Should fail when the directory is not a type specific resource directory (e.g: res/drawable).
    assertFalse(CreateTypedResourceFileAction.doIsAvailable(dataContext.build(), ResourceFolderType.DRAWABLE.getName()));
  }

  public void testAddPreferencesScreenAndroidxPreferenceLibraryHandling() {
    // First check without having androidx.preference as a library
    assertEquals("PreferenceScreen", CreateTypedResourceFileAction.getDefaultRootTagByResourceType(myModule, ResourceFolderType.XML));

    // Now with the dependency, the handler should return "androidx.preference.PreferenceScreen"
    TestProjectSystem testProjectSystem = new TestProjectSystem(getProject(), Collections.emptyList());
    testProjectSystem.useInTests();
    testProjectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_PREFERENCE, myFacet.getModule(), new GradleVersion(1, 1));
    assertEquals("androidx.preference.PreferenceScreen", CreateTypedResourceFileAction.getDefaultRootTagByResourceType(myModule, ResourceFolderType.XML));
  }
}