/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.service.image;


import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import org.jetbrains.annotations.NotNull;

public abstract class Format implements BinaryObject {
  public final static Format ALPHA = new FmtAlpha();
  public final static Format ATC_RGBA_EXPLICIT_ALPHA_AMD = new FmtATCRGBAEXPLICITALPHAAMD();
  public final static Format ATC_RGBA_INTERPOLATED_ALPHA_AMD = new FmtATCRGBAINTERPOLATEDALPHAAMD();
  public final static Format ATC_RGB_AMD = new FmtATCRGBAMD();
  public final static Format D24S8 = new FmtD24S8();
  public final static Format ETC1_RGB8 = new FmtETC1RGB8();
  public final static Format ETC2_RGB8 = new FmtETC2RGB8();
  public final static Format ETC2_RGBA8_EAC = new FmtETC2RGBA8EAC();
  public final static Format HALF_FLOAT = new FmtFloat16();
  public final static Format FLOAT32 = new FmtFloat32();
  public final static Format LUMINANCE = new FmtLuminance();
  public final static Format LUMINANCE_ALPHA = new FmtLuminanceAlpha();
  public final static Format PNG = new FmtPNG();
  public final static Format RED = new FmtRed();
  public final static Format RGB = new FmtRGB();
  public final static Format RGB565 = new FmtRGB565();
  public final static Format RGBA = new FmtRGBA();
  public final static Format RGBA4444 = new FmtRGBA4444();
  public final static Format RGBA5551 = new FmtRGBA5551();
  public final static Format RGBAF16 = new FmtRGBAF16();
  public final static Format RGBAF32 = new FmtRGBAF32();

  @NotNull
  public static Format wrap(BinaryObject object) {
    return (object instanceof Format) ? (Format)object : new UnknownFormat(object);
  }

  @NotNull
  public BinaryObject unwrap() {
    return this;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  public String getDisplayName() {
    String result = klass().entity().getName();
    if (result.toLowerCase().startsWith("fmt")) {
      result = result.substring(3);
    }
    return result;
  }

  private static class UnknownFormat extends Format {
    private final BinaryObject myFormat;

    public UnknownFormat(@NotNull BinaryObject format) {
      myFormat = format;
    }

    @NotNull
    @Override
    public BinaryClass klass() {
      return myFormat.klass();
    }

    @NotNull
    @Override
    public BinaryObject unwrap() {
      return myFormat;
    }
  }
}
