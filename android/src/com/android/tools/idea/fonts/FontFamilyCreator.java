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
package com.android.tools.idea.fonts;

import com.android.ide.common.res2.ValueXmlHelper;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Create a font xml file for a new downloadable font.
 */
public class FontFamilyCreator {
  private static final String FONT_FOLDER = "font";
  private final AndroidFacet myFacet;

  public FontFamilyCreator(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  public String createFontFamily(@NotNull FontDetail font, @NotNull String fontName, boolean downloadable) throws IOException {
    VirtualFile fontFolder = getTargetFontFolder();
    Project project = myFacet.getModule().getProject();
    TransactionGuard.submitTransaction(project, () -> new WriteCommandAction.Simple(project, "Create new font file") {
      @Override
      protected void run() throws Throwable {
        if (downloadable) {
          String content = createFontFamilyContent(font);
          fontFolder.createChildData(this, fontName + ".xml")
            .setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
        }
        else {
          File cachedFile = font.getCachedFontFile();
          fontFolder.createChildData(this, fontName + "." + FileUtilRt.getExtension(cachedFile.getName()))
            .setBinaryContent(FileUtil.loadFileBytes(cachedFile));
        }
      }
    }.execute());
    return "@font/" + fontName;
  }

  @NotNull
  private VirtualFile getTargetFontFolder() throws IOException {
    @SuppressWarnings("deprecation")
    VirtualFile resourceDirectory = myFacet.getPrimaryResourceDir();

    if (resourceDirectory == null) {
      throw new IOException("PrimaryResourceDirectory is null");
    }

    VirtualFile fontFolder = resourceDirectory.findChild(FONT_FOLDER);

    if (fontFolder == null) {
      fontFolder = resourceDirectory.createChildDirectory(this, FONT_FOLDER);
    }
    return fontFolder;
  }

  public static String getFontName(@NotNull FontDetail font) {
    String name = font.getFamily().getName();
    String styleName = StringUtil.trimStart(font.getStyleName(), "Regular").trim();
    if (!styleName.isEmpty()) {
      name += " " + styleName;
    }
    return DownloadableFontCacheServiceImpl.convertNameToFilename(name);
  }

  @NotNull
  @Language("XML")
  private static String createFontFamilyContent(@NotNull FontDetail font) {
    FontFamily family = font.getFamily();
    FontProvider provider = family.getProvider();
    return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
           "        android:fontProviderAuthority=\"" + escape(provider.getAuthority()) + "\"\n" +
           "        android:fontProviderQuery=\"" + escape(getQuery(font)) + "\">\n" +
           "</font-family>\n";
  }

  @NotNull
  private static String escape(@NotNull String value) {
    return ValueXmlHelper.escapeResourceString(value);
  }

  private static String getQuery(@NotNull FontDetail font) {
    FontFamily family = font.getFamily();
    assert family.getName().indexOf('&') < 0 : "Font name: " + family.getName() + " contains &";

    StringBuilder query = new StringBuilder()
      .append("name=").append(escape(family.getName()));
    if (font.getWeight() != FontDetail.DEFAULT_WEIGHT) {
      query.append("&weight=").append(font.getWeight());
    }
    if (font.isItalics()) {
      query.append("&italics=1");
    }
    if (font.getWidth() != FontDetail.DEFAULT_WIDTH) {
      query.append("&width=").append(font.getWidth());
    }
    return query.toString();
  }
}
