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
package org.jetbrains.android.dom;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractSingleRootFileDescription<T extends DomElement> extends AndroidResourceDomFileDescription<T> {
  private final String myTagName;

  public AbstractSingleRootFileDescription(@NotNull Class<T> aClass,
                                           @NotNull String tagName,
                                           @NotNull ResourceFolderType resourceFolderType) {
    super(aClass, tagName, resourceFolderType);
    myTagName = tagName;
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    if (!super.isMyFile(file, module)) {
      return false;
    }

    final XmlTag rootTag = file.getRootTag();
    if (rootTag == null) {
      return false;
    }

    return myTagName.equals(rootTag.getName());
  }
}

