/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import com.android.ide.common.util.PathString;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.res.ResolvableResourceItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for AAR value resource items.
 */
abstract class AbstractAarValueResourceItem extends AbstractAarResourceItem implements ResolvableResourceItem {
  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   */
  public AbstractAarValueResourceItem(@NotNull String name,
                                      @NotNull AarConfiguration configuration,
                                      @NotNull ResourceVisibility visibility) {
    super(name, configuration, visibility);
  }

  @Override
  @Nullable
  public String getValue() {
    return null;
  }

  @Override
  public final boolean isFileBased() {
    return false;
  }

  @Override
  @Nullable
  public final PathString getSource() {
    // TODO(sprigogin): Implement using a source attachment.
    return null;
  }

  @Override
  @NotNull
  public final ResolveResult createResolveResult() {
    return new ResolveResult() {
      @Override
      @Nullable
      public PsiElement getElement() {
        // TODO(sprigogin): Parse the attached source and return the corresponding element.
        return null;
      }

      @Override
      public boolean isValidResult() {
        return false;
      }
    };
  }
}
