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
package com.android.tools.idea.gradle.project.sync.setup.post.cleanup;

import com.android.tools.idea.gradle.project.sync.setup.module.SyncLibraryRegistry;
import com.android.tools.idea.gradle.project.sync.setup.module.SyncLibraryRegistry.LibraryToUpdate;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.Collections;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Test for {@link LibraryCleanupStep}.
 */
public class LibraryCleanupStepTest extends IdeaTestCase {
  @Mock private SyncLibraryRegistry myLibraryRegistry;
  @Mock private IdeModifiableModelsProvider myModelsProvider;

  private LibraryCleanupStep myCleanupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    SyncLibraryRegistry.setFactory(new SyncLibraryRegistry.Factory() {
      @Override
      @NotNull
      public SyncLibraryRegistry createNewInstance(@NotNull Project project) {
        return myLibraryRegistry;
      }
    });

    // Prevent test to set library sources
    when(myModelsProvider.getAllLibraries()).thenReturn(new Library[0]);

    myCleanupStep = new LibraryCleanupStep();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      SyncLibraryRegistry.restoreFactory();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCleanUpProject() throws Exception {
    Library libraryToRemove = mock(Library.class);
    when(myLibraryRegistry.getLibrariesToRemove()).thenReturn(Collections.singleton(libraryToRemove));

    Library changedLibrary = mock(Library.class);

    LibraryToUpdate libraryToUpdate = new LibraryToUpdate(changedLibrary, Collections.singletonList("jar:/new.jar"));
    when(myLibraryRegistry.getLibrariesToUpdate()).thenReturn(Collections.singletonList(libraryToUpdate));

    Library.ModifiableModel libraryToUpdateModel = mock(Library.ModifiableModel.class);
    when(myModelsProvider.getModifiableLibraryModel(changedLibrary)).thenReturn(libraryToUpdateModel);
    when(libraryToUpdateModel.getUrls(CLASSES)).thenReturn(new String[] {"jar:/existing.jar"});

    myCleanupStep.cleanUpProject(getProject(), myModelsProvider, null);

    // Verify library got removed
    verify(myModelsProvider).removeLibrary(libraryToRemove);

    // Verify URLs got updated
    verify(libraryToUpdateModel).removeRoot("jar:/existing.jar", CLASSES);
    verify(libraryToUpdateModel).addRoot("jar:/new.jar", CLASSES);

    //noinspection SSBasedInspection
    verify(myLibraryRegistry).dispose();
  }
}
