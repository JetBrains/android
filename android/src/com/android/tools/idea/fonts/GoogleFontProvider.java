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

/**
 * The only known {@link FontProvider}. Others may be available in the future.
 */
public class GoogleFontProvider extends FontProvider {
  public static final String GOOGLE_FONT_NAME = "Google Fonts";
  public static final String GOOGLE_FONT_AUTHORITY = "com.google.android.gms.fonts";
  public static final String GOOGLE_FONT_PACKAGE_NAME = "com.google.android.gms";
  public static final String GOOGLE_FONT_URL = "https://fonts.gstatic.com/s/a/directory.xml";
  public static final String GOOGLE_FONT_CERTIFICATE = "67f20865aaa676c9ac84ae022aea8d4a37003665";
  public static final FontProvider INSTANCE = new GoogleFontProvider();

  private GoogleFontProvider() {
    super(GOOGLE_FONT_NAME, GOOGLE_FONT_AUTHORITY, GOOGLE_FONT_PACKAGE_NAME, GOOGLE_FONT_URL, GOOGLE_FONT_CERTIFICATE);
  }
}
