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
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for creating {@link com.intellij.util.xml.DomFileDescription} classes describing
 * Android XML resources with several possible root tags. Subclasses should provide no-arguments
 * constructor and call "super" with required parameter values there.
 * <p/>
 * Class doesn't have any abstract methods but is marked as abstract to ensure that it wouldn't
 * be registered directly.
 *
 * @param <T> type of root tag DOM element interface
 */
public abstract class AbstractMultiRootFileDescription<T extends DomElement> extends AndroidResourceDomFileDescription<T> {
  private final ImmutableSet<String> myTagNames;

  public AbstractMultiRootFileDescription(@NotNull Class<T> aClass, @NotNull ResourceFolderType resourceFolderType, @NotNull ImmutableSet<String> tagNames) {
    super(aClass, tagNames.iterator().next(), resourceFolderType);
    myTagNames = tagNames;
  }

  public AbstractMultiRootFileDescription(@NotNull Class<T> aClass, @NotNull ResourceFolderType resourceFolderType, @NotNull String... tagNames) {
    super(aClass, tagNames[0], resourceFolderType);
    myTagNames = ImmutableSet.copyOf(tagNames);
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
    return rootTag != null && myTagNames.contains(rootTag.getName());
  }
}

