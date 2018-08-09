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
package com.android.tools.idea.resourceExplorer.sketchImporter.presenter;

import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorDrawableFile;
import com.android.tools.idea.resourceExplorer.sketchImporter.model.PageOptions;
import com.android.tools.idea.resourceExplorer.sketchImporter.model.SketchFile;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SketchImporterPresenter {
  private SketchFile mySketchFile;

  private ImportOptions myImportOptions;

  public SketchImporterPresenter(@NotNull String sketchFilePath) {
    mySketchFile = SketchParser.read(sketchFilePath);
    if (mySketchFile != null) {
      myImportOptions = new ImportOptions(mySketchFile);
    }
  }

  public ImportOptions getImportOptions() {
    return myImportOptions;
  }

  @Nullable
  public HashMap<String, List<LightVirtualFile>> generateFiles(@NotNull Project project) {
    if (mySketchFile == null) {
      return null;
    }

    HashMap<String, List<LightVirtualFile>> pageIdToFiles = new HashMap<>();

    for (SketchPage page : mySketchFile.getPages()) {
      PageOptions pageOptions = (PageOptions)myImportOptions.getOptions(page.getObjectId());

      if (pageOptions != null) {
        List<LightVirtualFile> filesInPage = generateFiles(page, pageOptions, project);

        pageIdToFiles.put(page.getObjectId(), filesInPage);
      }
    }

    return pageIdToFiles;
  }

  /**
   * @return a list of virtual files based on the content in the {@code SketchPage} and the {@code PageOptions}
   */
  @NotNull
  private static List<LightVirtualFile> generateFiles(@NotNull SketchPage page,
                                                      @NotNull PageOptions pageOptions,
                                                      @NotNull Project project) {
    List<LightVirtualFile> generatedFiles = new ArrayList<>();

    switch (pageOptions.getPageType()) {
      case ICONS:
        for (SketchArtboard artboard : page.getArtboards()) {
          VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(project, artboard);
          LightVirtualFile lightVirtualFile = vectorDrawableFile.generateFile();
          generatedFiles.add(lightVirtualFile);
        }
        break;
      default:
        break;
    }

    return generatedFiles;
  }
}
