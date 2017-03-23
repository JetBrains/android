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

import com.android.annotations.concurrency.Immutable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

import static com.android.tools.idea.fonts.FontFamily.FILE_PROTOCOL_START;

/**
 * A {@link FontDetail} is a reference to a specific font with weight, width, and italics attributes.
 * Each instance refers to a possibly remote (*.ttf) font file.
 */
@Immutable
public class FontDetail {
  public static final int DEFAULT_WEIGHT = 400;
  public static final int DEFAULT_BOLD_WEIGHT = 700;
  public static final int DEFAULT_WIDTH = 100;

  private final FontFamily myFamily;
  private final int myWeight;
  private final int myWidth;
  private final boolean myItalics;
  private final String myFontUrl;
  private final String myStyleName;

  public FontDetail(@NotNull FontFamily fontFamily,
                    @NotNull Builder builder) {
    myFamily = fontFamily;
    myWeight = builder.myWeight;
    myWidth = builder.myWidth;
    myItalics = builder.myItalics;
    myFontUrl = builder.myFontUrl;
    myStyleName = StringUtil.isEmpty(builder.myStyleName) ? generateStyleName(builder.myWeight, builder.myItalics) : builder.myStyleName;
  }

  public FontDetail(@NotNull FontDetail detail, @NotNull Builder withStyle) {
    myFamily = detail.myFamily;
    myWeight = withStyle.myWeight;
    myWidth = withStyle.myWidth;
    myItalics = withStyle.myItalics;
    myFontUrl = detail.myFontUrl;
    myStyleName = StringUtil.isEmpty(withStyle.myStyleName)
                  ? generateStyleName(withStyle.myWeight, withStyle.myItalics)
                  : withStyle.myStyleName;
  }

  @NotNull
  public FontFamily getFamily() {
    return myFamily;
  }

  public boolean isItalics() {
    return myItalics;
  }

  public int getWeight() {
    return myWeight;
  }

  public int getWidth() {
    return myWidth;
  }

  @NotNull
  public String getFontUrl() {
    return myFontUrl;
  }

  @NotNull
  public String getStyleName() {
    return myStyleName;
  }

  @NotNull
  public File getCachedFontFile() {
    if (myFontUrl.startsWith(FILE_PROTOCOL_START)) {
      return new File(myFontUrl.substring(FILE_PROTOCOL_START.length()));
    }
    DownloadableFontCacheServiceImpl service = DownloadableFontCacheServiceImpl.getInstance();
    return service.getCachedFont(myFamily.getProvider().getAuthority(), myFamily.getFontFolderName(), myFontUrl);
  }

  @NotNull
  private static String generateStyleName(int weight, boolean italics) {
    return getWeightStyleName(weight) + getItalicStyleName(italics);
  }

  @NotNull
  private static String getWeightStyleName(int weight) {
    switch (weight) {
      case 100:
        return "Thin";
      case 200:
        return "Extra-Light";
      case 300:
        return "Light";
      case 400:
        return "Regular";
      case 500:
        return "Medium";
      case 600:
        return "Semi-Bold";
      case 700:
        return "Bold";
      case 800:
        return "Extra-Bold";
      case 900:
        return "Black";
      default:
        if (weight > 400) {
          return "Custom-Bold";
        }
        else {
          return "Custom-Light";
        }
    }
  }

  @NotNull
  private static String getItalicStyleName(boolean italics) {
    return italics ? " Italic" : "";
  }

  @Override
  public int hashCode() {
    return Objects.hash(myWeight, myWidth, myItalics);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof FontDetail)) {
      return false;
    }
    FontDetail otherFamily = (FontDetail)other;
    return myWeight == otherFamily.myWeight &&
           myWidth == otherFamily.myWidth &&
           myItalics == otherFamily.myItalics;
  }

  public int match(@NotNull FontDetail.Builder other) {
    return Math.abs(myWeight - other.myWeight) + Math.abs(myWidth - other.myWidth) + (myItalics != other.myItalics ? 50 : 0);
  }

  public static final class Builder {
    public int myWeight;
    public int myWidth;
    public boolean myItalics;
    public String myFontUrl;
    public String myStyleName;

    Builder() {
      this(DEFAULT_WEIGHT, DEFAULT_WIDTH, false, "", null);
    }

    Builder(int weight, int width, boolean italics, @NotNull String fontUrl, @Nullable String styleName) {
      myWeight = weight;
      myWidth = width;
      myItalics = italics;
      myFontUrl = fontUrl;
      myStyleName = styleName;
    }

    Builder(@NotNull FontDetail detail) {
      myWeight = detail.getWeight();
      myWidth = detail.getWidth();
      myItalics = detail.isItalics();
      myFontUrl = detail.getFontUrl();
      myStyleName = detail.getStyleName();
    }
  }
}
