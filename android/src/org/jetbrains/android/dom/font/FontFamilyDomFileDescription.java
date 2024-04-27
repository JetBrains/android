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

package org.jetbrains.android.dom.font;

import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.SingleRootResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class FontFamilyDomFileDescription extends SingleRootResourceDomFileDescription<FontFamily> {
  public static final String TAG_NAME = "font-family";

  public FontFamilyDomFileDescription() {
    super(FontFamily.class, TAG_NAME, ResourceFolderType.FONT);
  }

  public static boolean isFontFamilyFile(@NotNull XmlFile file) {
    return isFileInResourceFolderType(file, ResourceFolderType.FONT);
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet, @NotNull ResourceFolderType folderType) {
    AndroidVersion sdkVersion = StudioAndroidModuleInfo.getInstance(facet).getBuildSdkVersion();

    if (sdkVersion == null || sdkVersion.getFeatureLevel() >= 26 || ApplicationManager.getApplication().isUnitTestMode()) {
      return Collections.singletonList(TAG_NAME);
    }

    return Collections.emptyList();
  }
}
