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
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;

public abstract class AndroidResourceDomFileDescription<T extends DomElement> extends DomFileDescription<T> {
  protected final EnumSet<ResourceFolderType> myResourceType;

  public AndroidResourceDomFileDescription(final Class<T> rootElementClass,
                                           @NonNls final String rootTagName,
                                           @NotNull EnumSet<ResourceFolderType> resourceTypes) {
    super(rootElementClass, rootTagName);
    myResourceType = EnumSet.copyOf(resourceTypes);
  }

  public AndroidResourceDomFileDescription(final Class<T> rootElementClass,
                                           @NonNls final String rootTagName,
                                           @NotNull ResourceFolderType resourceType) {
    this(rootElementClass, rootTagName, EnumSet.of(resourceType));
  }

  @Override
  public boolean isMyFile(@NotNull final XmlFile file, @Nullable Module module) {
    for (ResourceFolderType folderType : myResourceType) {
      if (doIsMyFile(file, folderType)) {
        return true;
      }
    }

    return false;
  }

  public static boolean doIsMyFile(@NotNull XmlFile file, @NotNull ResourceFolderType folderType) {
    return FileDescriptionUtils.isResourceOfTypeWithRootTag(file, folderType, Collections.emptySet());
  }

  @Override
  protected void initializeFileDescription() {
    registerNamespacePolicy(AndroidUtils.NAMESPACE_KEY, SdkConstants.NS_RESOURCES);
  }

  @NotNull
  public EnumSet<ResourceFolderType> getResourceTypes() {
    return myResourceType;
  }
}
