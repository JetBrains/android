/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AndroidMultiModuleRenameTest extends AndroidTestCase {
  public static final String LIBRARY_MODULE = "library";
  public static final String LIBRARY_PATH = getContentRootPath(LIBRARY_MODULE);

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, LIBRARY_MODULE, true);
  }

  // Check that renaming package in a library module would rename corresponding reference in AndroidManifest.xml
  public void testLibraryPackageRename() throws Exception {
    final VirtualFile manifestFile =
      myFixture.copyFileToProject("rename/AndroidManifest_library_before.xml", LIBRARY_PATH + "/src/AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(manifestFile);

    // At least one Java source has to be copied for .findPackage to return non-null instance
    myFixture.copyFileToProject("rename/LibraryClass.java", LIBRARY_PATH + "/src/com/works/customization/my/library/LibraryClass.java");
    final PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage("com.works.customization.my.library");
    assertNotNull(aPackage);
    new RenameProcessor(getProject(), aPackage, "com.works.customization.my.library2", true, true).run();
    FileDocumentManager.getInstance().saveAllDocuments();
    myFixture.checkResultByFile("rename/AndroidManifest_library_after.xml");
  }
}
