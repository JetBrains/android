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
import com.android.tools.idea.concurrent.FutureUtils;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.TAG_VECTOR;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An asset that represents an image on disk. The image can be either raster, e.g. PNG, JPG, etc,
 * or vector, e.g. XML drawable, SVG or PSD. All methods of class with an exception of
 * {#link {@link #getXmlDrawable()} have to be called on the event dispatch thread.
 */
public final class ImageAsset extends BaseAsset {
  @NotNull private final OptionalValueProperty<File> myImagePath;
  @NotNull private final ObservableBool myIsVectorGraphics;
  @NotNull private final ObservableBool myIsResizable;
  @NotNull private final BoolValueProperty myXmlDrawableIsResizable = new BoolValueProperty();
  @NotNull private final OptionalValueProperty<String> myError = new OptionalValueProperty<>();
  @NotNull private final OptionalValueProperty<String> myWarning = new OptionalValueProperty<>();

  @NotNull private final Object myLock = new Object();
  @GuardedBy("myLock")
  @Nullable private File myImageFile;
  @GuardedBy("myLock")
  @Nullable private ListenableFuture<String> myXmlDrawableFuture;
  @GuardedBy("myLock")
  @Nullable private ListenableFuture<BufferedImage> myImageFuture;

  public ImageAsset() {
    myImagePath = new OptionalValueProperty<>();
    myImagePath.addListener((v) -> {
      myXmlDrawableIsResizable.set(false);
      synchronized (myLock) {
        myImageFile = myImagePath.getValueOrNull();
        myXmlDrawableFuture = null;
        myImageFuture = null;
      }
    });

    myIsVectorGraphics = new BooleanExpression(myImagePath) {
      @Override
      @NotNull
      public Boolean get() {
        return isVectorGraphics(getFileType(myImagePath.getValueOrNull()));
      }
    };

    myIsResizable = new BooleanExpression(myImagePath, myXmlDrawableIsResizable) {
      @Override
      @NotNull
      public Boolean get() {
        FileType fileType = getFileType(myImagePath.getValueOrNull());
        if (fileType == null) {
          return false;
        }
        if (fileType == FileType.RASTER_IMAGE_CANDIDATE) {
          return true;
        }

        getXmlDrawable();  // Initiate loading/conversion of the drawable file if it hasn't been done already.

        return myXmlDrawableIsResizable.get();
      }
    };
  }

  @Nullable
  private static FileType getFileType(@Nullable File file) {
    return file == null ? null : FileType.fromFile(file);
  }

  /**
   * Returns the path to the image asset.
   */
  @NotNull
  public OptionalValueProperty<File> imagePath() {
    return myImagePath;
  }

  /**
   * Returns an observable boolean reflecting whether the image asset represents a vector graphics file or not.
   */
  @NotNull
  public ObservableBool isVectorGraphics() {
    return myIsVectorGraphics;
  }

  @Override
  @NotNull
  public ObservableBool isResizable() {
    return myIsResizable;
  }

  /**
   * Returns an observable reflecting the latest error encountered while reading the file or processing its contents.
   */
  @NotNull
  public OptionalValueProperty<String> getError() {
    return myError;
  }

  /**
   * Returns an observable reflecting the latest warning encountered while reading the file or processing its contents.
   */
  @NotNull
  public OptionalValueProperty<String> getWarning() {
    return myWarning;
  }

  @Override
  @Nullable
  public ListenableFuture<BufferedImage> toImage() {
    synchronized (myLock) {
      if (myImageFuture == null) {
        File file = myImageFile;
        if (file == null || isVectorGraphics(FileType.fromFile(file))) {
          return null;
        }
        myImageFuture = FutureUtils.executeOnPooledThread(() -> loadImage(file));
      }
      return myImageFuture;
    }
  }

  private void setError(@Nullable String message) {
    myError.setNullableValue(message);
    myWarning.setNullableValue(null);
  }

  /**
   * Returns the text of the XML drawable as a future, or null if the image asset does not represent a drawable.
   * For an SVG or a PSD file this method returns the result of conversion to an Android drawable.
   * <p>
   * This method may be called on any thread.
   */
  @Nullable
  public ListenableFuture<String> getXmlDrawable() {
    synchronized (myLock) {
      if (myXmlDrawableFuture == null) {
        if (myImageFile == null) {
          return null;
        }
        FileType fileType = FileType.fromFile(myImageFile);
        if (!isVectorGraphics(fileType)) {
          return null;
        }
        File file = myImageFile;
        myXmlDrawableFuture = FutureUtils.executeOnPooledThread(() -> loadXmlDrawable(file));
      }
      return myXmlDrawableFuture;
    }
  }

  @Nullable
  private String loadXmlDrawable(@NotNull File file) {
    String xmlText = null;
    String error = null;
    String warning = null;

    FileType fileType = FileType.fromFile(file);
    try {
      switch (fileType) {
        case XML_DRAWABLE:
          xmlText = Files.toString(file, UTF_8);
          break;

        case SVG:
          ByteArrayOutputStream outStream = new ByteArrayOutputStream();
          warning = Svg2Vector.parseSvgToXml(file, outStream);
          xmlText = outStream.toString(UTF_8.name());
          break;

        case LAYERED_IMAGE:
          xmlText = new LayeredImageConverter().toVectorDrawableXml(file);
          break;

        default:
          break;
      }
    }
    catch (IOException e) {
      error = e.getMessage();
    }

    boolean resizable = xmlText != null && TAG_VECTOR.equals(XmlUtils.getRootTagName(xmlText));
    String finalError = error;
    String finalWarning = error != null ? null : warning;
    UIUtil.invokeLaterIfNeeded(() -> {
      if (FileUtil.filesEqual(file, myImagePath.getValueOrNull())) {
        myXmlDrawableIsResizable.set(resizable);
        myError.setNullableValue(finalError);
        myWarning.setNullableValue(finalWarning);
      }
    });

    return xmlText;
  }

  @Nullable
  private BufferedImage loadImage(@NotNull File file) {
    BufferedImage image = null;
    String error = null;

    FileType fileType = FileType.fromFile(file);
    if (fileType == FileType.RASTER_IMAGE_CANDIDATE) {
      try {
        image = ImageIO.read(file);
      }
      catch (IOException e) {
        error = e.getMessage();
      }
    }

    BufferedImage finalImage = image;
    String finalError = error;
    UIUtil.invokeLaterIfNeeded(() -> {
      if (FileUtil.filesEqual(file, myImagePath.getValueOrNull())) {
        myXmlDrawableIsResizable.set(finalImage != null);
        myError.setNullableValue(finalError);
        myWarning.setNullableValue(null);
      }
    });

    return image;
  }

  private static boolean isVectorGraphics(@Nullable FileType fileType) {
    return fileType == FileType.XML_DRAWABLE || fileType == FileType.SVG || fileType == FileType.LAYERED_IMAGE;
  }

  private enum FileType {
    XML_DRAWABLE,
    SVG,
    LAYERED_IMAGE,
    RASTER_IMAGE_CANDIDATE;

    @NotNull
    static FileType fromFile(@NotNull File file) {
      String path = file.getPath();
      if (SdkUtils.endsWithIgnoreCase(path,".xml")) {
        return XML_DRAWABLE;
      }
      if (SdkUtils.endsWithIgnoreCase(path,".svg")) {
        return SVG;
      }
      if (SdkUtils.endsWithIgnoreCase(path,".psd")) {
        return LAYERED_IMAGE;
      }
      return RASTER_IMAGE_CANDIDATE;
    }
  }
}
