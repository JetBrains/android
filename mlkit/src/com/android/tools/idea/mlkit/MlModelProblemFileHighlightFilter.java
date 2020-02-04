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
package com.android.tools.idea.mlkit;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Used to whitelist model file types so we can highlight it with red squiggly line if it's problematic, e.g. some dependencies are missing
 * by the auto-generated classes.
 */
public class MlModelProblemFileHighlightFilter implements Condition<VirtualFile> {
  @Override
  public boolean value(VirtualFile file) {
    return file.getFileType() == TfliteModelFileType.INSTANCE;
  }
}
