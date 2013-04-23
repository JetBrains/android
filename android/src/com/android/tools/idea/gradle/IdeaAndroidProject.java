/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.build.gradle.model.AndroidProject;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class IdeaAndroidProject implements Serializable {
  @NotNull private final AndroidProject myDelegate;
  @NotNull private final String myRootDirPath;
  @NotNull private String mySelectedVariantName;

  /**
   * Creates a new {@link IdeaAndroidProject}.
   *
   * @param rootDirPath         absolute path of the root directory of the imported Android-Gradle project.
   * @param delegate            imported Android-Gradle project.
   * @param selectedVariantName name of the selected build variant.
   */
  public IdeaAndroidProject( @NotNull String rootDirPath, @NotNull AndroidProject delegate, @NotNull String selectedVariantName) {
    myRootDirPath = rootDirPath;
    myDelegate = delegate;
    setSelectedVariantName(selectedVariantName);
  }

  /**
   * @return the absolute path of the root directory of the imported Android-Gradle project.
   */
  @NotNull
  public String getRootDirPath() {
    return myRootDirPath;
  }

  /**
   * @return the imported Android-Gradle project.
   */
  @NotNull
  public AndroidProject getDelegate() {
    return myDelegate;
  }

  /**
   * @return the name of the selected build variant.
   */
  @NotNull
  public String getSelectedVariantName() {
    return mySelectedVariantName;
  }

  /**
   * Updates the name of the selected build variant.
   *
   * @param name the new name.
   */
  public void setSelectedVariantName(@NotNull String name) {
    mySelectedVariantName = name;
  }
}
