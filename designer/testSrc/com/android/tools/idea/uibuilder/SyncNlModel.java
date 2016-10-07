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
package com.android.tools.idea.uibuilder;

import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link NlModel} that runs all the operations synchronously for testing
 */
public class SyncNlModel extends NlModel {

  @NotNull
  public static NlModel create(@NotNull DesignSurface surface,
                               @Nullable Disposable parent,
                               @NotNull AndroidFacet facet,
                               @NotNull XmlFile file) {
    return new SyncNlModel(surface, parent, facet, file);
  }

  private SyncNlModel(@NotNull DesignSurface surface,
                     @Nullable Disposable parent,
                     @NotNull AndroidFacet facet, @NotNull XmlFile file) {
    super(surface, parent, facet, file);
  }

  @Override
  public void requestRender() {
    render();
  }

  @Override
  protected void requestModelUpdate() {
    updateModel();
  }
}
