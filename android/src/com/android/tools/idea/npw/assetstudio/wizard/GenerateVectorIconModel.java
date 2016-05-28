/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * A {@link GenerateIconsModel} that converts its vector source asset into a single .xml vector
 * file which can be used by Android.
 */
public final class GenerateVectorIconModel extends GenerateIconsModel {
  public GenerateVectorIconModel(@NotNull AndroidFacet androidFacet) {
    super(androidFacet);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(GenerateVectorIconModel.class);
  }

  @Override
  protected void generateIntoPath(@NotNull AndroidProjectPaths paths, @NotNull AndroidIconGenerator iconGenerator) {
    // We always know that this model is used on a step that requires VectorAssets
    VectorAsset vectorAsset = (VectorAsset)iconGenerator.sourceAsset().getValue();
    VectorAsset.ParseResult result = vectorAsset.parse();

    Map<File, BufferedImage> fileMap = iconGenerator.generateIntoFileMap(paths);
    ArrayList<File> outputFiles = Lists.newArrayList(fileMap.keySet());
    // Vector asset generator ONLY generates a single XML file
    assert outputFiles.size() == 1;
    File file = outputFiles.get(0);

    VirtualFile directory = null;
    try {
      directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile xmlFile = directory.findChild(file.getName());
      if (xmlFile == null || !xmlFile.exists()) {
        xmlFile = directory.createChildData(this, file.getName());
      }
      VfsUtil.saveText(xmlFile, result.getXmlContent());
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }
}
