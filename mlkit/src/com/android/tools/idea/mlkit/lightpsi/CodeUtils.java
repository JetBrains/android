/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.lightpsi;

import com.android.tools.mlkit.TensorInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Utility method to generate light class.
 */
public class CodeUtils {

  /**
   * Get qualified type name based on {@link Param}
   */
  @NotNull
  public static String getTypeQualifiedName(@NotNull TensorInfo tensorInfo) {
    if (tensorInfo.getSource() == TensorInfo.Source.INPUT) {
      if (tensorInfo.getContentType() == TensorInfo.ContentType.IMAGE) {
        return ClassNames.TENSOR_IMAGE;
      }
      else {
        return ClassNames.TENSOR_BUFFER;
      }
    }
    else {
      if (tensorInfo.getFileType() == TensorInfo.FileType.TENSOR_AXIS_LABELS) {
        return ClassNames.TENSOR_LABEL;
      }
      return ClassNames.TENSOR_BUFFER;
    }
  }
}
