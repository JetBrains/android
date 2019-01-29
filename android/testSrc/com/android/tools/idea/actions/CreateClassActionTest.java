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
package com.android.tools.idea.actions;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.ide.IdeView;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.android.utils.FileUtils.toSystemIndependentPath;

public final class CreateClassActionTest extends AndroidTestCase {
  public void testGetDestinationDirectoryIdeHasOneDirectory() {
    PsiDirectory directory = Mockito.mock(PsiDirectory.class);
    assertEquals(directory, CreateClassAction.getDestinationDirectory(mockIdeView(Collections.singletonList(directory)), myModule));
  }

  public void testGetDestinationDirectoryIdeDoesntHaveOneDirectory() throws IOException {
    AndroidModel oldModel = myFacet.getConfiguration().getModel();

    Path path = FileSystems.getDefault().getPath(myModule.getProject().getBasePath(), "app", "src", "main", "java");
    TestFileUtils.createDirectoriesAndRefreshVfs(path);

    IdeView ide = mockIdeView(Arrays.asList(Mockito.mock(PsiDirectory.class), Mockito.mock(PsiDirectory.class)));

    try {
      myFacet.getConfiguration().setModel(mockAndroidModel(Collections.singletonList(path.toFile())));

      PsiFileSystemItem directory = CreateClassAction.getDestinationDirectory(ide, myModule);
      assert directory != null;

      assertEquals(toSystemIndependentPath(path.toString()), directory.getVirtualFile().getPath());
    }
    finally {
      myFacet.getConfiguration().setModel(oldModel);
    }
  }

  @NotNull
  private static AndroidModel mockAndroidModel(@NotNull Collection<File> files) {
    SourceProvider provider = Mockito.mock(SourceProvider.class);
    Mockito.when(provider.getJavaDirectories()).thenReturn(files);

    AndroidModel model = Mockito.mock(AndroidModel.class);
    Mockito.when(model.getDefaultSourceProvider()).thenReturn(provider);

    return model;
  }

  @NotNull
  private static IdeView mockIdeView(@NotNull Collection<PsiDirectory> directories) {
    IdeView ide = Mockito.mock(IdeView.class);
    Mockito.when(ide.getDirectories()).thenReturn(directories.toArray(PsiDirectory.EMPTY_ARRAY));

    return ide;
  }
}
