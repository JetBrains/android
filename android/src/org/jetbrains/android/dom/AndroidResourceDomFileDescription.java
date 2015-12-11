/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidResourceDomFileDescription<T extends DomElement> extends DomFileDescription<T> {
  protected final ResourceFolderType myResourceType;

  public AndroidResourceDomFileDescription(final Class<T> rootElementClass,
                                           @NonNls final String rootTagName,
                                           @NotNull ResourceFolderType resourceType) {
    super(rootElementClass, rootTagName);
    myResourceType = resourceType;
  }

  @Override
  public boolean isMyFile(@NotNull final XmlFile file, @Nullable Module module) {
    return doIsMyFile(file, myResourceType);
  }

  public static boolean doIsMyFile(final XmlFile file, final ResourceFolderType resourceType) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (file.getProject().isDisposed()) {
          return false;
        }
        if (AndroidResourceUtil.isInResourceSubdirectory(file, resourceType.getName())) {
          return AndroidFacet.getInstance(file) != null;
        }
        return false;
      }
    });
  }

  @Override
  protected void initializeFileDescription() {
    registerNamespacePolicy(AndroidUtils.NAMESPACE_KEY, SdkConstants.NS_RESOURCES);
  }

  @NotNull
  public ResourceFolderType getResourceType() {
    return myResourceType;
  }
}
