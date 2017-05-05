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

import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;
import java.net.URL;

/**
 * A {@link FontProvider} is a service that provides a font directory with links
 * to individual font *.ttf files that can be downloaded an cached.`
 */
@Immutable
public class FontProvider implements Comparable<FontProvider> {
  public static final FontProvider EMPTY_PROVIDER = new FontProvider("", "", "", "", "", "");

  private final String myName;
  private final String myAuthority;
  private final String myPackageName;
  private final String myUrl;
  private final String myCertificate;
  private final String myDevelopmentCertificate;

  public FontProvider(@NotNull String name,
                      @NotNull String authority,
                      @NotNull String packageName,
                      @NotNull String url,
                      @NotNull String certificate,
                      @NotNull String developmentCertificate) {
    myName = name;
    myAuthority = authority;
    myPackageName = packageName;
    myUrl = url;
    myCertificate = certificate;
    myDevelopmentCertificate = developmentCertificate;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getAuthority() {
    return myAuthority;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getCertificate() {
    return myCertificate;
  }

  @NotNull
  public String getDevelopmentCertificate() {
    return myDevelopmentCertificate;
  }

  @NotNull
  public String getCertificateResourceName() {
    return myAuthority.replace('.', '_') + "_certs";
  }

  public URL getFallbackResourceUrl() {
    String filename = myAuthority.equals(GoogleFontProvider.GOOGLE_FONT_AUTHORITY) ?
                      "google_font_directory.xml" : "empty_font_directory.xml";
    return ResourceUtil.getResource(FontDirectoryDownloadService.class, "fonts", filename);
  }

  @Override
  public int compareTo(@NotNull FontProvider other) {
    return myAuthority.compareTo(other.getAuthority());
  }

  @Override
  public int hashCode() {
    return myAuthority.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof FontProvider)) {
      return false;
    }
    FontProvider otherProvider = (FontProvider)other;
    return myAuthority.equals(otherProvider.myAuthority);
  }
}
