/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tools.ndk.NdkHelper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.SymbolsProperties.SymbolsKind;
import com.jetbrains.cidr.lang.symbols.symtable.building.OCSymbolTablesBuildingActivity;
import java.util.List;
import org.junit.After;
import org.junit.Before;

/** Base C++ test class for integration tests. */
public class BlazeCppIntegrationTestCase extends BlazeIntegrationTestCase {

  @Before
  public void enableCppLanguageSupport() {
    NdkHelper.disableCppLanguageSupport(getProject(), false);

    enableSymbolTableBuildingInTests(getProject(), true, true);
  }

  @VisibleForTesting
  private static void enableSymbolTableBuildingInTests(
      Project project, boolean loadPrevSymbols, boolean saveSymbols) {
    FileSymbolTablesCache.setShouldBuildTablesInTests(
        new FileSymbolTablesCache.SymbolsProperties(
            // We may want to allow ALL_INCLUDING_UNUSED_SYSTEM_HEADERS as well, but for now
            // ONLY_USED is enough
            SymbolsKind.ONLY_USED, loadPrevSymbols, saveSymbols));

    // Normally, CidrProjectFixture will setShouldBuildTablesInTests before a project is created
    // and thus before FileSymbolTablesCache is instantiated for a project.
    //
    // When FileSymbolTablesCache is created, it should queue OCSymbolTablesBuildingActivity.
    //
    // However, because we run well after a project is created, we need to manually queue
    // OCSymbolTablesBuildingActivity after toggling on setShouldBuildTablesInTests.

    OCSymbolTablesBuildingActivity.getInstance(project).rebuildSymbols();
  }

  @After
  public void disableCppLanguageSupport() {
    FileSymbolTablesCache.setShouldBuildTablesInTests(null);
    NdkHelper.disableCppLanguageSupport(getProject(), true);
  }

  protected OCFile createFile(String relativePath, String... contentLines) {
    PsiFile file = workspace.createPsiFile(new WorkspacePath(relativePath), contentLines);
    assertThat(file).isInstanceOf(OCFile.class);
    return (OCFile) file;
  }

  protected OCFile createNonWorkspaceFile(String relativePath, String... contentLines) {
    PsiFile file = fileSystem.createPsiFile(relativePath, contentLines);
    assertThat(file).isInstanceOf(OCFile.class);
    return (OCFile) file;
  }

  protected OCFile createFileWithEditor(String relativePath, String... contentLines) {
    VirtualFile virtualFile = workspace.createFile(new WorkspacePath(relativePath), contentLines);
    testFixture.configureFromExistingVirtualFile(virtualFile);
    PsiFile file = testFixture.getFile();
    assertThat(file).isInstanceOf(OCFile.class);
    return (OCFile) file;
  }

  protected static void assertText(OCFile file, String... expectedLines) {
    List<String> actualLines = Splitter.on('\n').splitToList(file.getText());
    int i = 0;
    for (String actualLine : actualLines) {
      if (i < expectedLines.length) {
        assertWithMessage(
                String.format(
                    "Diff on line %s\nActual full text:\n%s\nExpected full text:\n%s",
                    i, file.getText(), String.join("\n", expectedLines)))
            .that(actualLine)
            .isEqualTo(expectedLines[i]);
      }
      i++;
    }
    assertThat(actualLines).hasSize(expectedLines.length);
  }
}
