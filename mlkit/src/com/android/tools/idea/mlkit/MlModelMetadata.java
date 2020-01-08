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
package com.android.tools.idea.mlkit;

import com.android.tools.mlkit.MetadataExtractor;
import com.android.tools.mlkit.ModelData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores metadata of model file used by the light model class generator.
 */
public class MlModelMetadata {
  public final String myModelFileUrl;
  public final String myClassName;

  @Nullable
  private final ModelData myModelData;

  private static final Logger LOG = Logger.getInstance(MlModelMetadata.class);

  public MlModelMetadata(@NotNull String modelFileUrl, @NotNull String className) {
    myModelFileUrl = modelFileUrl;
    myClassName = className;

    ByteBuffer byteBuffer = null;
    try {
      // Load model as ByteBuffer
      VirtualFile modelFile = VirtualFileManager.getInstance().findFileByUrl(modelFileUrl);
      if (modelFile != null) {
        byte[] data = modelFile.contentsToByteArray();
        byteBuffer = ByteBuffer.wrap(data);
      }
    } catch (IOException e) {
      LOG.error("IO Exception when loading model: " + modelFileUrl, e);
    }
    myModelData = byteBuffer != null
                  ? ModelData.buildFrom(new MetadataExtractor(byteBuffer))
                  : null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MlModelMetadata && myModelFileUrl.equals(((MlModelMetadata)obj).myModelFileUrl);
  }

  @Override
  public int hashCode() {
    return myModelFileUrl.hashCode();
  }
}
