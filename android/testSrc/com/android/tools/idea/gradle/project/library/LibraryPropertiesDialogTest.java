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
package com.android.tools.idea.gradle.project.library;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;

public class LibraryPropertiesDialogTest extends PlatformTestCase {
  private Library createLibrary(@NotNull String name) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
    Library library = libraryTableModel.createLibrary(name);
    ApplicationManager.getApplication().runWriteAction(libraryTableModel::commit);

    return library;
  }

  public void testApplyChanges() {
    File binaryPath = new File("testLib-1.2.3.jar");
    String binaryUrl = pathToIdeaUrl(binaryPath);
    String sourceUrl = pathToIdeaUrl(new File("testLib-1.2.3-sources.jar"));
    String javadocUrl = pathToIdeaUrl(new File("testLib-1.2.3-javadoc.jar"));
    String javadocUrl2 = pathToIdeaUrl(new File("testLib-1.2.31-javadoc.jar"));

    final Library testLib = createLibrary(binaryPath.getName());

    final Library.ModifiableModel libraryModifiableModel = testLib.getModifiableModel();
    libraryModifiableModel.addRoot(binaryUrl, CLASSES);
    libraryModifiableModel.addRoot(sourceUrl, SOURCES);
    libraryModifiableModel.addRoot(javadocUrl, JavadocOrderRootType.getInstance());

    Application application = ApplicationManager.getApplication();
    application.runWriteAction(libraryModifiableModel::commit);

    final LibraryPropertiesDialog dialog = new LibraryPropertiesDialog(myProject, testLib);
    try {
      final LibraryEditor editor = dialog.getLibraryEditorComponent().getLibraryEditor();

      String[] urls;

      // Add a new root
      editor.addRoot(javadocUrl2, JavadocOrderRootType.getInstance());
      dialog.applyChanges();

      urls = testLib.getUrls(JavadocOrderRootType.getInstance());
      assertThat(urls).hasLength(2);
      assertEquals(javadocUrl, urls[0]);
      assertEquals(javadocUrl2, urls[1]);

      // Remove roots
      editor.removeRoot(javadocUrl, JavadocOrderRootType.getInstance());
      editor.removeRoot(sourceUrl, SOURCES);
      dialog.applyChanges();

      urls = testLib.getUrls(JavadocOrderRootType.getInstance());
      assertThat(urls).hasLength(1);
      assertEquals(javadocUrl2, urls[0]);
      assertEmpty(testLib.getUrls(SOURCES));

      // Binary must be left unchanged
      urls = testLib.getUrls(CLASSES);
      assertThat(urls).hasLength(1);
      assertEquals(binaryUrl, urls[0]);
    }
    finally {
      Disposer.dispose(dialog.getDisposable());
    }
  }
}
