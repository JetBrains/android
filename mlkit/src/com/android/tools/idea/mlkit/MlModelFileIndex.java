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

import com.android.tools.idea.mlkit.viewer.TfliteModelFileType;
import com.android.tools.mlkit.MlConstants;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.TfliteModelException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.VirtualFileGist;
import com.intellij.util.io.DataExternalizer;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Indexes machine learning model (e.g. TFLite model) files under the ml folder.
 */
public final class MlModelFileIndex {
  private static final VirtualFileGist<MlModelMetadata> ourGist = GistManager.getInstance()
    .newVirtualFileGist(
      "MlModelFileGist",
      1,
      new DataExternalizer<MlModelMetadata>() {
        @Override
        public void save(@NotNull DataOutput out, @NotNull MlModelMetadata value) throws IOException {
          out.writeUTF(value.myModelFileUrl);
          value.myModelInfo.save(out);
        }

        @Override
        public MlModelMetadata read(@NotNull DataInput in) throws IOException {
          String fileUrl = in.readUTF();
          ModelInfo modelInfo = new ModelInfo(in);
          return new MlModelMetadata(fileUrl, modelInfo);
        }
      },
      (project, file) -> {
        try {
          if (file.getLength() > MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES) return null;
          byte[] bytes = Files.readAllBytes(VfsUtilCore.virtualToIoFile(file).toPath());
          ModelInfo modelInfo = ModelInfo.buildFrom(ByteBuffer.wrap(bytes));
          return new MlModelMetadata(file.getUrl(), modelInfo);
        }
        catch (TfliteModelException e) {
          Logger.getInstance(MlModelFileIndex.class).warn("Failed to gist " + file.getUrl());
        }
        catch (Exception e) {
          Logger.getInstance(MlModelFileIndex.class).warn("Failed to gist " + file.getUrl(), e);
        }
        return null;
      }
    );

  @NotNull
  public static Set<MlModelMetadata> getModelMetadataSet(Module module) {
    GlobalSearchScope searchScope = MlModelFilesSearchScope.inModule(module);
    Collection<VirtualFile> modelFiles =
      FilenameIndex.getAllFilesByExt(module.getProject(), TfliteModelFileType.INSTANCE.getDefaultExtension(), searchScope);
    return modelFiles.stream().map(it -> ourGist.getFileData(module.getProject(), it)).filter(Objects::nonNull).collect(Collectors.toSet());
  }
}
