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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.builder.model.ClassField;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * A {@link ResourceItem} for an item that is dynamically defined in a Gradle file. This needs a special class because (1) we can't rely on
 * the normal resource value parser to create resource values from XML, and (2) we need to implement getQualifiers since there is no source
 * file.
 */
public class DynamicResourceValueItem implements ResourceItem {
  @NotNull private final ResourceValue myResourceValue;

  public DynamicResourceValueItem(@NotNull ResourceNamespace namespace,
                                  @NonNull ResourceType type,
                                  @NonNull ClassField field) {
    // Dynamic values are always in the "current module", so they don't live in a namespace.
    myResourceValue = new ResourceValue(namespace, type, field.getName(), field.getValue());
  }


  @NotNull
  public ResolveResult createResolveResult() {
    return new ResolveResult() {
      @Nullable
      @Override
      public PsiElement getElement() {
        // TODO: Try to find the item in the Gradle files
        return null;
      }

      @Override
      public boolean isValidResult() {
        return false;
      }
    };
  }

  @NonNull
  @Override
  public String getName() {
    return myResourceValue.getName();
  }

  @NonNull
  @Override
  public ResourceType getType() {
    return myResourceValue.getResourceType();
  }

  @Nullable
  @Override
  public String getLibraryName() {
    return null;
  }

  @NonNull
  @Override
  public ResourceNamespace getNamespace() {
    return myResourceValue.getNamespace();
  }

  @NonNull
  @Override
  public ResourceReference getReferenceToSelf() {
    return myResourceValue.asReference();
  }

  @NonNull
  @Override
  public FolderConfiguration getConfiguration() {
    return new FolderConfiguration();
  }

  @NonNull
  @Override
  public String getKey() {
    return myResourceValue.getResourceUrl().toString().substring(1);
  }

  @Nullable
  @Override
  public ResourceValue getResourceValue() {
    return myResourceValue;
  }

  @Nullable
  @Override
  public File getFile() {
    return null;
  }

  @Override
  public boolean isFileBased() {
    return false;
  }
}
