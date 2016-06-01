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
package com.android.tools.idea.ui.resourcechooser;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ResourceItem {
  private final boolean myIsAttr;
  private final @NotNull ResourceGroup myGroup;
  private final @Nullable String myNamespace;
  private final @NotNull String myName;
  private final @Nullable VirtualFile myFile;
  private @Nullable Icon myIcon;

  public ResourceItem(@NotNull ResourceGroup group, @Nullable String namespace, @NotNull String name, @Nullable VirtualFile file) {
    myGroup = group;
    myNamespace = namespace;
    myName = name;
    myFile = file;
    myIsAttr = false;
  }

  public ResourceItem(@NotNull ResourceGroup group, @Nullable String namespace, @NotNull String name, boolean isAttr) {
    myGroup = group;
    myNamespace = namespace;
    myName = name;
    myFile = null;
    myIsAttr = isAttr;
  }

  @NotNull
  public ResourceGroup getGroup() {
    return myGroup;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getQualifiedName() {
    return getNamespace() == null ? getName() : getNamespace() + ":" + getName();
  }

  @Nullable
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public String getResourceUrl() {
    if (myIsAttr) {
      return String.format("?%sattr/%s", getNamespace() == null ? "" : getNamespace() + ":", getName());
    }
    return String.format("@%s%s/%s", getNamespace() == null ? "" : getNamespace() + ":", myGroup.getType().getName(), myName);
  }

  @NotNull
  public ResourceValue getResourceValue() {
    // No need to try and find the resource as we know exactly what it's going to be like
    return new ResourceValue(myGroup.getType(), getName(), myFile == null ? getResourceUrl() : myFile.getPath(), isFramework(), null);
  }

  @Override
  public String toString() {
    if (myIsAttr) {
      return getQualifiedName(); // as all attrs are inside 1 section, we show the full name
    }
    // we need to return JUST the name so quicksearch in JList works
    return getName();
  }

  @Nullable("if no icon has been set on this item")
  public Icon getIcon() {
    return myIcon;
  }

  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  @Nullable("null for app namespace")
  public String getNamespace() {
    return myNamespace;
  }

  public boolean isFramework() {
    return SdkConstants.ANDROID_NS_NAME.equals(getNamespace());
  }

  public boolean isAttr() {
    return myIsAttr;
  }
}
