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
package com.android.tools.idea.apk.debugging;

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;

/**
 * Kotlin-specific version of JavaProjectStructureDetector, registered as a ProjectStructureDetector
 * extension.
 *
 * In APK projects, JavaProjectStructureDetector does not detect directories that contain only Kotlin
 * files (i.e., no Java files. Mixed Java-Kotlin work fine). In this case, smali files from those
 * packages cannot be mapped to the Kotlin files.
 */
public class KotlinProjectStructureDetector extends JavaSourceRootDetector {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  protected String getLanguageName() {
    return "Kotlin";
  }

  @NotNull
  @Override
  protected String getFileExtension() {
    return KotlinFileType.EXTENSION;
  }

  @Override
  protected @NotNull NullableFunction<CharSequence, String> getPackageNameFetcher() {
    return charSequence -> JavaSourceRootDetectionUtil.getPackageName(charSequence);
  }
}