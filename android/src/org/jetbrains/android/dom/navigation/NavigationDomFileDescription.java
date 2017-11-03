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
package org.jetbrains.android.dom.navigation;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NavigationDomFileDescription extends AndroidResourceDomFileDescription<NavDestinationElement> {
  // We don't have access to a project at this point, and thus don't have access to the nav library, so this has to be hardcoded.
  private static final String DEFAULT_ROOT_TAG = "navigation";

  public NavigationDomFileDescription() {
    super(NavDestinationElement.class, DEFAULT_ROOT_TAG, ResourceFolderType.NAVIGATION);
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return isNavFile(file);
  }

  public static boolean isNavFile(@NotNull XmlFile file) {
    // TODO: remove once navigation type is fully supported (e.g. in aapt)
    if (file.getName().equals("mobile_navigation.xml")) {
      return true;
    }

    return ApplicationManager.getApplication().runReadAction(
      (Computable<Boolean>)() -> AndroidResourceDomFileDescription.doIsMyFile(file, ResourceFolderType.NAVIGATION));
  }
}
