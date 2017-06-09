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
package org.jetbrains.android.dom.drawable.fileDescriptions;

import com.android.resources.ResourceFolderType;
import com.google.common.collect.ImmutableSet;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AbstractMultiRootFileDescription;
import org.jetbrains.android.dom.FileDescriptionUtils;
import org.jetbrains.android.dom.drawable.AdaptiveIcon;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class AdaptiveIconDomFileDescription extends AbstractMultiRootFileDescription<AdaptiveIcon> {
  public static final ImmutableSet<String> TAGS = ImmutableSet.of("adaptive-icon", "maskable-icon");

  public AdaptiveIconDomFileDescription() {
    super(AdaptiveIcon.class, EnumSet.of(ResourceFolderType.MIPMAP, ResourceFolderType.DRAWABLE), TAGS);
  }

  public static boolean isAdaptiveIcon(@NotNull XmlFile file) {
    return FileDescriptionUtils.isResourceOfType(file, ResourceFolderType.MIPMAP, TAGS);
  }
}
