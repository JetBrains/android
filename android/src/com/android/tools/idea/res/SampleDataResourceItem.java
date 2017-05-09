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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.SourcelessResourceItem;
import com.android.resources.ResourceType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.w3c.dom.Node;

public class SampleDataResourceItem extends SourcelessResourceItem {
  private final String myValue;
  private final PsiElement mySourceElement;

  private SampleDataResourceItem(@NonNull String name,
                                 @NonNull String value,
                                 PsiElement sourceElement) {
    super(name, null, ResourceType.SAMPLE_DATA, null, null);

    myValue = value;
    // We use SourcelessResourceItem as parent because we don't really obtain a FolderConfiguration or Qualifiers from
    // the source element (since it's not within the resources directory).
    mySourceElement = sourceElement;
  }

  public SampleDataResourceItem(@NonNull String name,
                                @NonNull VirtualFile virtualFile,
                                PsiElement sourceElement) {
    this(name, virtualFile.getPath(), sourceElement);
  }

  @Nullable
  @Override
  public Node getValue() {
    throw new UnsupportedOperationException("SampleDataResourceItem does not support getValue");
  }

  @Nullable
  @Override
  public String getValueText() {
    return myValue;
  }

  @NonNull
  @Override
  public String getQualifiers() {
    return "";
  }

  @Nullable
  @Override
  public ResourceValue getResourceValue(boolean isFrameworks) {
    return new ResourceValue(getResourceUrl(isFrameworks), getValueText());
  }

  @Nullable
  public PsiElement getPsiElement() {
    return mySourceElement;
  }
}
