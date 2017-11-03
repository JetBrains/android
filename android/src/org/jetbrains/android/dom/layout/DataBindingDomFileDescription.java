/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom.layout;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.TAG_LAYOUT;

/**
 * Data binding root tag: {@code <layout>}
 */
public class DataBindingDomFileDescription extends LayoutDomFileDescription<Layout> {
  public DataBindingDomFileDescription() {
    super(Layout.class, TAG_LAYOUT);
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return super.isMyFile(file, module) && hasDataBindingRootTag(file);
  }

  public static boolean hasDataBindingRootTag(@NotNull XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    return rootTag != null && TAG_LAYOUT.equals(rootTag.getName());
  }
}

