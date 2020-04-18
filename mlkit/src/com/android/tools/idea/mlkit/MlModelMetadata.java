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

import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.TensorInfo;
import com.android.tools.mlkit.exception.TfliteModelException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores metadata of model file used by the light model class generator.
 */
public class MlModelMetadata {
  public final String myModelFileUrl;
  public final String myClassName;

  @Nullable
  private ModelInfo myModelData;

  private static final Logger LOG = Logger.getInstance(MlModelMetadata.class);

  public MlModelMetadata(@NotNull String modelFileUrl, @NotNull String className) {
    myModelFileUrl = modelFileUrl;
    myClassName = className;

    try {
      // Load model as ByteBuffer
      VirtualFile modelFile = VirtualFileManager.getInstance().findFileByUrl(modelFileUrl);
      if (modelFile != null) {
        myModelData = ModelInfo.buildFrom(ByteBuffer.wrap(modelFile.contentsToByteArray()));
      }
    }
    catch (TfliteModelException e) {
      LOG.warn("Failed to get model info from model:: " + modelFileUrl, e);
    }
    catch (IOException e) {
      LOG.warn("IO Exception when loading model: " + modelFileUrl, e);
    }
  }

  public boolean isValidModel() {
    return myModelData != null;
  }

  @NotNull
  public List<TensorInfo> getInputTensorInfos() {
    if (isValidModel()) {
      return myModelData.getInputs();
    }

    return Collections.emptyList();
  }

  @NotNull
  public List<TensorInfo> getOutputTensorInfos() {
    if (isValidModel()) {
      return myModelData.getOutputs();
    }

    return Collections.emptyList();
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
