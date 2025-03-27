/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom.raw;

import static org.jetbrains.android.dom.WatchFaceUtilKt.isDeclarativeWatchFaceFile;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.CustomLogicResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RawDomFileDescription extends CustomLogicResourceDomFileDescription<XmlRawResourceElement> {

  public RawDomFileDescription() {
    super(XmlRawResourceElement.class, ResourceFolderType.RAW, "raw");
  }

  @Override
  public boolean checkFile(@NotNull XmlFile file, @Nullable Module module) {
    return !StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get() || !isDeclarativeWatchFaceFile(file);
  }

  public static boolean isRawFile(@NotNull final XmlFile file) {
    return new RawDomFileDescription().isMyFile(file, null);
  }
}
