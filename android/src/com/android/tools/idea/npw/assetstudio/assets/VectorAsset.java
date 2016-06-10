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
package com.android.tools.idea.npw.assetstudio.assets;

import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.ide.common.vectordrawable.VdOverrideInfo;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.tools.idea.npw.assetstudio.AssetStudioUtils;
import com.android.tools.idea.ui.properties.core.*;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * An asset which represents a vector graphics image. This can be loaded either from an SVG file,
 * a layered image supported by the pixelprobe library or a Vector Drawable file.
 *
 * After setting {@link #path()}, call one of the {@link #parse()} to attempt to read it and
 * generate a result.
 */
public final class VectorAsset extends BaseAsset {

  private static final String ERROR_EMPTY_PREVIEW = "Could not generate a preview";

  private final ObjectProperty<FileType> myFileType = new ObjectValueProperty<>(FileType.SVG);
  private final ObjectProperty<File> myPath = new ObjectValueProperty<>(new File(System.getProperty("user.home")));
  private final IntProperty myOpacity = new IntValueProperty(100);
  private final BoolProperty myAutoMirrored = new BoolValueProperty();
  private final IntProperty myOutputWidth = new IntValueProperty();
  private final IntProperty myOutputHeight = new IntValueProperty();

  /**
   * The format of the file specified by {@link #path()}
   */
  public enum FileType {
    SVG,
    LAYERED_IMAGE,
    VECTOR_DRAWABLE,
  }

  public VectorAsset(@NotNull FileType fileType) {
    type().set(fileType);
  }

  @NotNull
  public static FileType typeFromExtension(@NotNull String path) {
    String fullPath = new File(path).getAbsolutePath();
    int index = fullPath.lastIndexOf('.');
    String extension = path.substring(index + 1);
    switch (extension.toLowerCase(Locale.ROOT)) {
      case "svg":
        return FileType.SVG;
      case "psd":
        return FileType.LAYERED_IMAGE;
    }
    return FileType.VECTOR_DRAWABLE;
  }

  @NotNull
  public ObjectProperty<FileType> type() {
    return myFileType;
  }

  @NotNull
  public ObjectProperty<File> path() {
    return myPath;
  }

  @NotNull
  public IntProperty opacity() {
    return myOpacity;
  }

  @NotNull
  public BoolProperty autoMirrored() {
    return myAutoMirrored;
  }

  /**
   * Since vector assets can be rendered at any size, set this width to a positive value if you
   * want to override the final output width.
   *
   * Otherwise, the asset's default width (as parsed from the file) will be used.
   *
   * @see #outputHeight()
   */
  @NotNull
  public IntProperty outputWidth() {
    return myOutputWidth;
  }

  /**
   * Since vector assets can be rendered at any size, set this height to a positive value if you
   * want to override the final output height.
   *
   * Otherwise, the asset's default height (as parsed from the file) will be used.
   *
   * @see #outputWidth()
   */
  @NotNull
  public IntProperty outputHeight() {
    return myOutputHeight;
  }

  /**
   * Parse the file specified by the {@link #path()} property, overriding its final width which is
   * useful for previewing this vector asset in some UI component of the same width.
   */
  @NotNull
  public ParseResult parse(int previewWidth) {
    return tryParse(previewWidth);
  }

  /**
   * Parse the file specified by the {@link #path()} property.
   */
  @NotNull
  public ParseResult parse() {
    return parse(0);
  }

  @NotNull
  @Override
  protected BufferedImage createAsImage(@NotNull Color color) {
    return parse().getImage();
  }

  /**
   * Attempt to parse an SVG file, a Vector Drawable file or a layered image based on current settings.
   *
   * @param previewWidth If set to a positive value, this will override the current output width
   *                     value (in effect, scaling the final result).
   */
  @NotNull
  private ParseResult tryParse(int previewWidth) {
    StringBuilder errorBuffer = new StringBuilder();

    File path = myPath.get();
    if (!path.exists() || path.isDirectory()) {
      return ParseResult.INVALID;
    }

    String xmlFileContent = null;
    FileType fileType = myFileType.get();

    if (fileType.equals(FileType.SVG)) {
      OutputStream outStream = new ByteArrayOutputStream();
      String errorLog = Svg2Vector.parseSvgToXml(path, outStream);
      errorBuffer.append(errorLog);
      xmlFileContent = outStream.toString();
    }
    else if (fileType.equals(FileType.LAYERED_IMAGE)) {
      try {
        xmlFileContent = new LayeredImageConverter().toVectorDrawableXml(path);
      }
      catch (IOException e) {
        errorBuffer.append(e.getMessage());
      }
    }
    else {
      try {
        xmlFileContent = Files.toString(path, Charsets.UTF_8);
      }
      catch (IOException e) {
        errorBuffer.append(e.getMessage());
      }
    }

    BufferedImage image = null;
    int originalWidth = 0;
    int originalHeight = 0;
    if (xmlFileContent != null) {
      Document vdDocument = VdPreview.parseVdStringIntoDocument(xmlFileContent, errorBuffer);
      if (vdDocument != null) {
        VdPreview.SourceSize vdOriginalSize = VdPreview.getVdOriginalSize(vdDocument);
        originalWidth = vdOriginalSize.getWidth();
        originalHeight = vdOriginalSize.getHeight();

        String overriddenXml = overrideXmlFileContent(vdDocument, vdOriginalSize, errorBuffer);
        if (overriddenXml != null) {
          xmlFileContent = overriddenXml;
        }

        if (previewWidth <= 0) {
          previewWidth = myOutputWidth.get() > 0 ? myOutputWidth.get() : originalWidth;
        }

        final VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createSizeFromWidth(previewWidth);
        image = VdPreview.getPreviewFromVectorXml(imageTargetSize, xmlFileContent, errorBuffer);
      }
    }

    if (image == null) {
      errorBuffer.insert(0, ERROR_EMPTY_PREVIEW + "\n");
      return new ParseResult(errorBuffer.toString());
    }
    else {
      return new ParseResult(errorBuffer.toString(), image, originalWidth, originalHeight, xmlFileContent);
    }
  }

  /**
   * Modify the source XML content with custom values set by the user, such as final output size
   * and opacity.
   */
  @Nullable
  private String overrideXmlFileContent(@NotNull Document vdDocument,
                                        @NotNull VdPreview.SourceSize vdOriginalSize,
                                        @NotNull StringBuilder errorBuffer) {
    int finalWidth = vdOriginalSize.getWidth();
    int finalHeight = vdOriginalSize.getHeight();

    Integer outputWidth = myOutputWidth.get();
    Integer outputHeight = myOutputHeight.get();
    if (outputWidth > 0) {
      finalWidth = outputWidth;
    }
    if (outputHeight > 0) {
      finalHeight = outputHeight;
    }

    finalWidth = Math.max(VdPreview.MIN_PREVIEW_IMAGE_SIZE, finalWidth);
    finalHeight = Math.max(VdPreview.MIN_PREVIEW_IMAGE_SIZE, finalHeight);
    finalWidth = Math.min(VdPreview.MAX_PREVIEW_IMAGE_SIZE, finalWidth);
    finalHeight = Math.min(VdPreview.MAX_PREVIEW_IMAGE_SIZE, finalHeight);

    VdOverrideInfo overrideInfo = new VdOverrideInfo(finalWidth, finalHeight, myOpacity.get(), myAutoMirrored.get());
    return VdPreview.overrideXmlContent(vdDocument, overrideInfo, errorBuffer);
  }

  /**
   * A parse result returned after calling {@link #parse()}. Check {@link #isValid()} to see if
   * the parse was successful.
   */
  public static final class ParseResult {
    /**
     * A special instance which will be returned when we can't successfully parse vector data.
     *
     * Users shouldn't worry about this detail and should instead just check {@link #isValid()}
     */
    private static final ParseResult INVALID = new ParseResult();

    @NotNull private final String myErrors;
    @NotNull private final BufferedImage myImage;
    private final int myOriginalWidth;
    private final int myOriginalHeight;
    private final boolean myIsValid;
    @NotNull private final String myXmlContent;

    /**
     * Use {@link #INVALID} instead.
     */
    private ParseResult() {
      this("", AssetStudioUtils.createDummyImage(), 0, 0, "");
    }

    public ParseResult(@NotNull String errors) {
      this(errors, INVALID.getImage(), 0, 0, "");
    }

    public ParseResult(@NotNull String errors,
                       @NotNull BufferedImage image,
                       int originalWidth,
                       int originalHeight,
                       @NotNull String xmlContent) {
      myErrors = errors;
      myImage = image;
      myOriginalWidth = originalWidth;
      myOriginalHeight = originalHeight;
      myXmlContent = xmlContent;
      myIsValid = (originalWidth > 0 && originalHeight > 0);
    }

    /**
     * If {@code true}, we could successfully parse the target file into a valid vector resource.
     *
     * Note that a result can still be valid even with errors. See {@link #getErrors()} for more
     * information.
     */
    public boolean isValid() {
      return myIsValid;
    }

    /**
     * The preferred width specified in the SVG file (although a Vector file can be rendered to any
     * width).
     */
    public int getOriginalWidth() {
      return myOriginalWidth;
    }

    /**
     * The preferred height specified in the SVG file (although a Vector file can be rendered to
     * any height).
     */
    public int getOriginalHeight() {
      return myOriginalHeight;
    }

    /**
     * Return a block of text which contains any errors encountered during parsing, or the empty
     * string, if none.
     *
     * Errors are not always fatal, as we still make a best effort to produce a valid result even
     * if we encounter tags or attributes in the input file we don't recognize or can't handle.
     */
    @NotNull
    public String getErrors() {
      return myErrors;
    }

    /**
     * An image preview of the final vector asset.
     */
    @NotNull
    public BufferedImage getImage() {
      return myImage;
    }

    /**
     * The XML which represents the final Android vector resource. It will be different from the
     * source file as some values will be overridden based on user values.
     */
    @NotNull
    public String getXmlContent() {
      return myXmlContent;
    }
  }
}
