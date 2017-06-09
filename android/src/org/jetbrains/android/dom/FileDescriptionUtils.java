/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.dom;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class FileDescriptionUtils {
  private FileDescriptionUtils() {
  }

  public static boolean isResourceOfType(@NotNull XmlFile file,
                                         @NotNull ResourceFolderType folderType,
                                         @NotNull Collection<String> rootTags) {
    return ApplicationManager.getApplication().runReadAction(newIsResourceOfTypeComputable(file, folderType, rootTags));
  }

  @NotNull
  @VisibleForTesting
  static Computable<Boolean> newIsResourceOfTypeComputable(@NotNull XmlFile file,
                                                           @NotNull ResourceFolderType folderType,
                                                           @NotNull Collection<String> rootTags) {
    return () -> {
      if (file.getProject().isDisposed()) {
        return false;
      }

      if (!AndroidResourceUtil.isInResourceSubdirectory(file, folderType.getName())) {
        return false;
      }

      if (rootTags.isEmpty()) {
        return true;
      }

      PsiNamedElement rootTag = file.getRootTag();
      assert rootTag != null;

      return rootTags.contains(rootTag.getName());
    };
  }
}
