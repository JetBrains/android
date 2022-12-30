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
package org.jetbrains.android.dom.color;

import com.android.resources.ResourceFolderType;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

public final class AndroidColorDomUtil {
  private AndroidColorDomUtil() {}

  private static final ImmutableList<String> ROOT_TAGS = ImmutableList.of("selector", "gradient");

  public static ImmutableList<String> getPossibleRoots() {
    return ROOT_TAGS;
  }

  public static boolean isColorResourceFile(@NotNull XmlFile file) {
    return AndroidResourceDomFileDescription.isFileInResourceFolderType(file, ResourceFolderType.COLOR);
  }
}
