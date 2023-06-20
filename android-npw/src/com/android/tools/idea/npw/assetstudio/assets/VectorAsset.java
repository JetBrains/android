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

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.roundToInt;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.ide.common.vectordrawable.VdOverrideInfo;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.Validator.Severity;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.DoubleProperty;
import com.android.tools.idea.observable.core.DoubleValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.utils.SdkUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.EdtInvocationManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * An asset which represents a vector graphics image. This can be loaded either from an SVG file,
 * a layered image supported by the pixelprobe library, or a Vector Drawable file.
 * <p>
 * After setting {@link #path()}, call one of the {@link #generatePreview()} methods to attempt to
 * read it and generate a result.
 */
public final class VectorAsset extends BaseAsset {
  private static final String ERROR_EMPTY_PREVIEW = "Could not generate a preview";
  private static final VectorDrawableInfo SELECT_A_FILE =
      new VectorDrawableInfo(new Validator.Result(Severity.WARNING, "Please select a file"));

  @NotNull private final OptionalValueProperty<File> myPath = new OptionalValueProperty<>(new File(System.getProperty("user.home")));
  @NotNull private final BoolProperty myAutoMirrored = new BoolValueProperty();
  @NotNull private final DoubleProperty myOutputWidth = new DoubleValueProperty();
  @NotNull private final DoubleProperty myOutputHeight = new DoubleValueProperty();

  @NotNull private final ObjectProperty<VectorDrawableInfo> myVectorDrawableInfo = new ObjectValueProperty<>(SELECT_A_FILE);

  public VectorAsset() {
    InvalidationListener listener = () -> {
      File file = myPath.getValueOrNull();
      if (file != null) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          VectorDrawableInfo drawableInfo = convertToVectorDrawable(file);
          EdtInvocationManager.getInstance().invokeLater(() -> {
            if (isCurrentFile(file)) {
              myVectorDrawableInfo.set(drawableInfo);
            }
          });
        });
      }
      else {
        myVectorDrawableInfo.set(SELECT_A_FILE);
      }
    };
    myPath.addListener(listener);
    listener.onInvalidated();
  }

  public boolean isCurrentFile(@Nullable Object file) {
    return Objects.equals(file, myPath.getValueOrNull());
  }

  @NotNull
  public OptionalValueProperty<File> path() {
    return myPath;
  }

  @NotNull
  public BoolProperty autoMirrored() {
    return myAutoMirrored;
  }

  /**
   * Since vector assets can be rendered at any size, set this width to a positive value if you
   * want to override the final output width.
   * <p>
   * Otherwise, the asset's default width (as parsed from the file) will be used.
   *
   * @see #outputHeight()
   */
  @NotNull
  public DoubleProperty outputWidth() {
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
  public DoubleProperty outputHeight() {
    return myOutputHeight;
  }

  @NotNull
  public ObjectProperty<VectorDrawableInfo> getVectorDrawableInfo() {
    return myVectorDrawableInfo;
  }

  @NotNull
  private static VectorDrawableInfo convertToVectorDrawable(@NotNull File file) {
    String filename = file.getName();
    if (!file.exists()) {
      return new VectorDrawableInfo("File " + filename + " does not exist");
    }
    if (file.isDirectory()) {
      return SELECT_A_FILE;
    }

    String xmlFileContent = null;
    StringBuilder errors = new StringBuilder();

    try {
      FileType fileType = FileType.fromFile(file);
      switch (fileType) {
        case SVG: {
          ByteArrayOutputStream outStream = new ByteArrayOutputStream();
          String errorMessage = Svg2Vector.parseSvgToXml(file.toPath(), outStream);
          xmlFileContent = outStream.toString(UTF_8);
          errors.append(errorMessage);
          break;
        }

        case LAYERED_IMAGE:
          xmlFileContent = new LayeredImageConverter().toVectorDrawableXml(file);
          break;

        case VECTOR_DRAWABLE:
          xmlFileContent = Files.readString(file.toPath());
          break;
      }
    } catch (Exception e) {
      errors.append("Error while parsing ").append(filename);
      String errorDetail = e.getLocalizedMessage();
      if (errorDetail != null) {
        errors.append(" - ").append(errorDetail);
      }
      return new VectorDrawableInfo(errors.toString());
    }

    double originalWidth = 0;
    double originalHeight = 0;
    if (!Strings.isNullOrEmpty(xmlFileContent)) {
      // TODO: Use XML pull parser to make parsing faster.
      Document document = parseXml(xmlFileContent, errors.length() == 0 ? errors : null);
      if (document == null) {
        xmlFileContent = null; // XML content is invalid, discard it.
      }
      else {
        Element root = document.getDocumentElement();
        originalWidth = parseDoubleAttributeValue(root, "android:width", "dp");
        originalHeight = parseDoubleAttributeValue(root, "android:height", "dp");
      }
    }

    boolean valid = originalWidth > 0 && originalHeight > 0;
    if (!valid && errors.length() == 0) {
      return new VectorDrawableInfo("The specified asset could not be parsed. Please choose another asset.");
    }

    Severity severity = !valid ? Severity.ERROR : errors.length() == 0 ? Severity.OK : Severity.WARNING;
    Validator.Result validityState = createValidatorResult(severity, errors.toString());
    return new VectorDrawableInfo(validityState, xmlFileContent, originalWidth, originalHeight);
  }

  @NotNull
  private static Validator.Result createValidatorResult(@NotNull Validator.Severity severity, @NotNull String errors) {
    if (errors.indexOf('\n') < 0) {
      // Single-line error message.
      return new Validator.Result(severity, "The image may be incomplete: " + errors);
    }

    // Multi-line error message.
    String shortMessage = "<html>The image may be incomplete due to encountered <a href=\"issues\">issues</a></html>";
    return new Validator.Result(severity, shortMessage, errors);
  }

  /**
   * Parses the file specified by the {@link #path()} property, overriding its final width,
   * which is useful for previewing this vector asset in some UI component of the same width.
   *
   * @param previewWidth width of the display component
   */
  @NotNull
  public Preview generatePreview(int previewWidth) {
    File file = myPath.getValueOrNull();
    VectorDrawableInfo drawableInfo = file == null ? SELECT_A_FILE : convertToVectorDrawable(file);
    return generatePreview(drawableInfo, previewWidth, null);
  }

  /**
   * Generates preview of the asset.
   *
   * @param drawableInfo information about the vector drawable produced from the asset
   * @param previewSize the width (and height) of the display component
   * @param overrideInfo adjustments to the drawable parameters
   */
  @NotNull
  public static Preview generatePreview(@NotNull VectorDrawableInfo drawableInfo, int previewSize, @Nullable VdOverrideInfo overrideInfo) {
    Preconditions.checkArgument(previewSize > 0);

    Validator.Result validityState = drawableInfo.getValidityState();
    if (!drawableInfo.isValid()) {
      return new Preview(validityState);
    }

    String xmlFileContent = drawableInfo.getXmlContent();
    assert xmlFileContent != null;
    Document document = parseXml(xmlFileContent, null);
    assert document != null;

    StringBuilder errors = new StringBuilder();

    if (overrideInfo != null) {
      String overriddenXml = VdPreview.overrideXmlContent(document, overrideInfo, errors);
      if (overriddenXml != null) {
        xmlFileContent = overriddenXml;
      }
    }

    BufferedImage image = null;
    VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createFromMaxDimension(previewSize);
    try {
      image = VdPreview.getPreviewFromVectorXml(imageTargetSize, xmlFileContent, errors);
    } catch (Throwable e) {
      Logger.getInstance(VectorAsset.class).error(e);
    }

    if (validityState.getSeverity() == Severity.OK && errors.length() != 0) {
      validityState = image == null ?
                      new Validator.Result(Severity.ERROR, ERROR_EMPTY_PREVIEW) :
                      createValidatorResult(Severity.WARNING, errors.toString());
    }

    return new Preview(validityState, image, xmlFileContent);
  }

  /**
   * Parses a vector drawable XML file into a {@link Document} object.
   *
   * @param xmlFileContent the content of the VectorDrawable's XML file.
   * @param errorLog when errors were found, log them in this builder if it is not null.
   * @return parsed document or null if errors happened.
   */
  @Nullable
  public static Document parseXml(@NotNull String xmlFileContent, @com.android.annotations.Nullable StringBuilder errorLog) {
    try {
      DocumentBuilder builder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
      return builder.parse(new InputSource(new StringReader(xmlFileContent)));
    }
    catch (Exception e) {
      if (errorLog != null) {
        errorLog.append("Exception while parsing XML file:\n").append(e.getMessage());
      }
      return null;
    }
  }

  /**
   * Parses the file specified by the {@link #path()} property.
   */
  @NotNull
  public Preview generatePreview() {
    int previewSize = roundToInt(Math.max(myOutputWidth.get(), myOutputHeight.get()));
    VdOverrideInfo overrideInfo = createOverrideInfo();
    return generatePreview(myVectorDrawableInfo.get(), previewSize, overrideInfo);
  }

  private static double parseDoubleAttributeValue(@NotNull Element element, @NotNull String attributeName, @NotNull String expectedSuffix) {
    String value = element.getAttribute(attributeName);
    if (value == null || !value.endsWith(expectedSuffix)) {
      return 0;
    }
    try {
      return Double.parseDouble(value.substring(0, value.length() - expectedSuffix.length()));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  @NotNull
  public ListenableFuture<BufferedImage> toImage() {
    return FutureUtils.executeOnPooledThread(() -> generatePreview().getImage());
  }

  /**
   * Creates {@link VdOverrideInfo} reflecting the current state of the asset parameters.
   */
  @NotNull
  public VdOverrideInfo createOverrideInfo() {
    double width = myOutputWidth.get();
    double height = myOutputHeight.get();
    if (width <= 0 || height <= 0) {
      // Preserve original dimensions.
      width = 0;
      height = 0;
    }
    return new VdOverrideInfo(width, height, color().getValueOrNull(), opacityPercent().get() / 100., myAutoMirrored.get());
  }

  /**
   * Vector drawable data returned by the {@link #convertToVectorDrawable(File)} method. Check {@link #isValid()}
   * to see if the conversion was successful.
   */
  public static final class VectorDrawableInfo {
    @NotNull private final Validator.Result myValidityState;
    @Nullable private final String myXmlContent;
    private final double myOriginalWidth;
    private final double myOriginalHeight;

    private VectorDrawableInfo(@NotNull String errorMessage) {
      this(Validator.Result.fromNullableMessage(errorMessage));
    }

    private VectorDrawableInfo(@NotNull Validator.Result validityState) {
      this(validityState, null, 0, 0);
    }

    private VectorDrawableInfo(@NotNull Validator.Result validityState, @Nullable String xmlContent,
                               double originalWidth, double originalHeight) {
      myValidityState = validityState;
      myXmlContent = xmlContent;
      myOriginalWidth = originalWidth;
      myOriginalHeight = originalHeight;
    }

    @Nullable
    public String getXmlContent() {
      return myXmlContent;
    }

    /**
     * Returns true if the content of the target file was successfully converted to a vector drawable.
     * <p>
     * Note that a result can still be valid even with errors. See {@link #getValidityState()} for more information.
     */
    public boolean isValid() {
      return myOriginalWidth > 0 && myOriginalHeight > 0;
    }

    /**
     * The preferred width specified in the SVG file (although a vector drawable file can be rendered to any width).
     */
    public double getOriginalWidth() {
      return myOriginalWidth;
    }

    /**
     * The preferred height specified in the SVG file (although a vector drawable file can be rendered to any height).
     */
    public double getOriginalHeight() {
      return myOriginalHeight;
    }

    /**
     * Returns errors, warnings or informational messages produced during parsing.
     */
    @NotNull
    public Validator.Result getValidityState() {
      return myValidityState;
    }
  }

  /**
   * Preview data returned by the {@link #generatePreview} methods. Check {@link #isValid()} to see
   * if the preview generation was successful.
   */
  public static final class Preview {
    @NotNull private final Validator.Result myValidityState;
    @Nullable private final BufferedImage myImage;
    @Nullable private final String myXmlContent;

    public Preview(@NotNull String errorMessage) {
      this(Validator.Result.fromNullableMessage(errorMessage));
    }

    public Preview(@NotNull Validator.Result validityState) {
      this(validityState, null, null);
    }

    public Preview(@NotNull Validator.Result validityState, @Nullable BufferedImage image, @Nullable String xmlContent) {
      myValidityState = validityState;
      myImage = image;
      myXmlContent = xmlContent;
    }

    /**
     * Returns true if the content of the target file was successfully converted to a vector drawable.
     * <p>
     * Note that a result can still be valid even with errors. See {@link #getValidityState()} for more
     * information.
     */
    public boolean isValid() {
      return myValidityState.getSeverity() != Severity.ERROR;
    }

    /**
     * Returns errors, warnings or informational messages produced during parsing.
     */
    @NotNull
    public Validator.Result getValidityState() {
      return myValidityState;
    }

    /**
     * An image preview of the final vector asset.
     */
    @Nullable
    public BufferedImage getImage() {
      return myImage;
    }

    /**
     * The XML that represents the final Android vector resource. It will be different from
     * the source file as some values may be overridden based on user values.
     */
    @Nullable
    public String getXmlContent() {
      return myXmlContent;
    }
  }

  /** The format of the file. */
  private enum FileType {
    SVG,
    LAYERED_IMAGE,
    VECTOR_DRAWABLE;

    @NotNull
    static FileType fromFile(@NotNull File file) {
      String path = file.getPath();
      if (SdkUtils.endsWithIgnoreCase(path,".svg")) {
        return SVG;
      }
      if (SdkUtils.endsWithIgnoreCase(path,".psd")) {
        return LAYERED_IMAGE;
      }
      return VECTOR_DRAWABLE;
    }
  }
}
