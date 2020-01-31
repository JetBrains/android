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
package com.android.tools.idea.lang.multiDexKeep;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for MultiDexKeepFile format files.
 */
public class MultiDexKeepFileTypeFactory extends FileTypeFactory {
  public static final String MULTIDEX_CONFIG = "multidex-config.txt";
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    if (StudioFlags.MULTI_DEX_KEEP_FILE_SUPPORT_ENABLED.get()) {
      consumer.consume(MultiDexKeepFileType.INSTANCE, new ExactFileNameMatcher(MULTIDEX_CONFIG, true));
    }
  }
}
