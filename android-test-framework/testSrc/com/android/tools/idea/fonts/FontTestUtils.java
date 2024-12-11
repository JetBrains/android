/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.fonts;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontSource;
import com.android.ide.common.fonts.MutableFontDetail;
import com.android.resources.ResourceFolderType;
import com.android.tools.fonts.DownloadableFontCacheServiceImpl;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

public final class FontTestUtils {

  private FontTestUtils() {
  }

  @NotNull
  public static FontDetail createFontDetail(@NotNull String fontName, int weight, float width, float italics) {
    String folderName = DownloadableFontCacheServiceImpl.convertNameToFilename(fontName);
    String urlStart = "http://dontcare/fonts/" + folderName + "/v6/";
    FontFamily family = new FontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.DOWNLOADABLE, fontName, urlStart + "some.ttf", "",
                                       Collections.singletonList(
                                         new MutableFontDetail(weight, width, italics, urlStart + "other.ttf", "", false, false)));
    return family.getFonts().get(0);
  }

  @NotNull
  public static String getResourceFileContent(@NotNull AndroidFacet facet, @NotNull ResourceFolderType type, @NotNull String fileName) throws
                                                                                                                                       IOException {
    @SuppressWarnings("deprecation")
    VirtualFile resourceDirectory = checkNotNull(ResourceFolderManager.getInstance(facet).getPrimaryFolder());
    VirtualFile resourceFolder = checkNotNull(resourceDirectory.findChild(type.getName()));
    VirtualFile file = checkNotNull(resourceFolder.findChild(fileName));
    file.refresh(false, false);
    return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
  }
}
