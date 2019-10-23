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

import static com.android.SdkConstants.TAG_NAVIGATION;

import com.android.resources.ResourceFolderType;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.ResourceFolderTypeDomFileDescription;
import org.jetbrains.annotations.NotNull;

public class NavigationDomFileDescription extends ResourceFolderTypeDomFileDescription<NavGraphElement> {
  // We don't have access to a project at this point, and thus don't have access to the nav library, so this has to be hardcoded.
  public static final String DEFAULT_ROOT_TAG = TAG_NAVIGATION;

  public NavigationDomFileDescription() {
    super(NavGraphElement.class, ResourceFolderType.NAVIGATION, DEFAULT_ROOT_TAG);
  }

  public static boolean isNavFile(@NotNull XmlFile file) {
    return isFileInResourceFolderType(file, ResourceFolderType.NAVIGATION);
  }
}
