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

import static com.android.SdkConstants.TAG_VECTOR;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.UiThread;
import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An asset that represents an image on disk. The image can be either raster, e.g. PNG, JPG, etc,
 * or vector, e.g. XML drawable, SVG or PSD. All methods of the class with an exception of
 * {@link #getXmlDrawable()} have to be called on the event dispatch thread.
 */
public final class ImageAsset extends BaseAsset {
  private static final String IMAGE_PATH_PROPERTY = "imagePath";

  @NotNull private final OptionalValueProperty<File> myImagePath;
  @NotNull private final ObservableBool myIsResizable;
  @NotNull private final BoolValueProperty myXmlDrawableIsResizable = new BoolValueProperty();
  @NotNull private final ObjectValueProperty<Validator.Result> myValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  @NotNull private String myRole = "image"; // The setRole method has to be called immediately after construction.
  @Nullable private File myDefaultImagePath;

  private boolean isClipart;

  @NotNull private final Object myLock = new Object();
  @GuardedBy("myLock")
  @Nullable private File myImageFile;
  @GuardedBy("myLock")
  @Nullable private ListenableFuture<String> myXmlDrawableFuture;
  @GuardedBy("myLock")
  @Nullable private ListenableFuture<BufferedImage> myImageFuture;

  @UiThread
  public ImageAsset() {
    myImagePath = new OptionalValueProperty<>();
    myImagePath.addListener(() -> {
      myXmlDrawableIsResizable.set(false);
      synchronized (myLock) {
        myImageFile = myImagePath.getValueOrNull();
        myXmlDrawableFuture = null;
        myImageFuture = null;
      }
    });

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

  /**
   * Sets the role played by this image asset. Can be used for producing unambiguous error messages in situations
   * when there are multiple image assets. The default role is "image". Has to be called immediately after
   * construction of the object.
   *
   * @param role a short description of the role, e.g. "background image"
   */
  @UiThread
  public void setRole(@NotNull String role) {
    myRole = role;
  }

  /**
   * Sets the default image path. Also sets the current image path if it was the same as default.
   * Has to be called immediately after construction of the object.
   *
   * @param file the default image path
   */
  @UiThread
  public void setDefaultImagePath(@Nullable File file) {
    boolean wasDefault = FileUtil.filesEqual(myImagePath.getValueOrNull(), myDefaultImagePath);
    myDefaultImagePath = file;
    if (wasDefault) {
      myImagePath.setNullableValue(myDefaultImagePath);
    }
  }

  /**
   * Sets the clipart designation of the image asset.
   */
  @UiThread
  public void setClipart(boolean clipart) {
    isClipart = clipart;
  }

  /**
   * Checks if the image is clipart. All clipart images are black on a transparent background.
   */
  @UiThread
  public boolean isClipart() {
    return isClipart;
  }

  @UiThread
  @Override
  public boolean isColorable() {
    return isClipart;
  }

  /**
   * Returns the path to the image asset.
   */
  @UiThread
  @NotNull
  public OptionalValueProperty<File> imagePath() {
    return myImagePath;
  }

  @UiThread
  @Override
  @NotNull
  public ObservableBool isResizable() {
    return myIsResizable;
  }

  /**
   * Returns an observable reflecting the latest error or warning encountered while reading
   * the file or processing its contents.
   */
  @UiThread
  @NotNull
  public ObjectValueProperty<Validator.Result> getValidityState() {
    return myValidityState;
  }

  @UiThread
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

  @UiThread
  @Override
  public @NotNull PersistentState getState() {
    PersistentState state = super.getState();
    state.setEncoded(IMAGE_PATH_PROPERTY, myImagePath.getValueOrNull(),
                     file -> FileUtil.filesEqual(file, myDefaultImagePath) ? null : file.getPath());
    return state;
  }

  @UiThread
  @Override
  public void loadState(@NotNull PersistentState state) {
    super.loadState(state);
    File file = state.getDecoded(IMAGE_PATH_PROPERTY, imagePath -> {
      if (imagePath == null) {
        return null;
      }

      Path path = Paths.get(imagePath);
      while (Files.notExists(path)) {
        path = path.getParent();
        if (path == null) {
          String userHomePath = System.getProperty("user.home");
          if (userHomePath != null) {
            path = Paths.get(userHomePath);
          }
          break;
        }
      }
      return path == null ? null : path.toFile();
    });

    if (file == null) {
      file = myDefaultImagePath;
    }
    if (file != null) {
      myImagePath.setValue(file);
    }
  }

  /**
   * Returns the text of the XML drawable as a future, or null if the image asset does not represent a drawable.
   * For an SVG or a PSD file this method returns the result of conversion to an Android drawable.
   */
  @AnyThread
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
    Validator.Result validityState = checkFileExistence(file);

    if (validityState.getSeverity() == Validator.Severity.OK) {
      FileType fileType = FileType.fromFile(file);
      try {
        switch (fileType) {
          case XML_DRAWABLE:
            xmlText = Files.readString(file.toPath());
            break;

          case SVG:
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            String message = Svg2Vector.parseSvgToXml(file.toPath(), outStream);
            xmlText = outStream.toString(UTF_8);
            if (xmlText.isEmpty()) {
              xmlText = null;
            }
            if (!message.isEmpty()) {
              Validator.Severity severity = xmlText == null ? Validator.Severity.ERROR : Validator.Severity.WARNING;
              validityState = createValidatorResult(severity, message);
            }
            break;

          case LAYERED_IMAGE:
            xmlText = new LayeredImageConverter().toVectorDrawableXml(file);
            break;

          default:
            break;
        }
      }
      catch (Exception e) {
        StringBuilder message = new StringBuilder();
        message.append("Error while parsing ").append(file.getName());
        String errorDetail = e.getLocalizedMessage();
        if (errorDetail != null) {
          message.append(" - ").append(errorDetail);
        }
        validityState = Validator.Result.fromNullableMessage(message.toString());
      }
    }

    if (xmlText == null && validityState.getSeverity() == Validator.Severity.OK) {
      validityState = Validator.Result.fromNullableMessage("The " + myRole + " file could not be parsed. Please choose another file.");
    }
    boolean resizable = xmlText != null && TAG_VECTOR.equals(XmlUtils.getRootTagName(xmlText));

    updateValidityStateAndResizability(file, validityState, resizable);
    return xmlText;
  }


  @NotNull
  private Validator.Result createValidatorResult(@NotNull Validator.Severity severity, @NotNull String errors) {
    if (errors.indexOf('\n') < 0) {
      // Single-line error message.
      return new Validator.Result(severity, "The " + myRole + " may be incomplete: " + errors);
    }

    // Multi-line error message.
    String shortMessage = "<html>The " + myRole + " may be incomplete due to encountered <a href=\"issues\">issues</a></html>";
    return new Validator.Result(severity, shortMessage, errors);
  }

  @Nullable
  private BufferedImage loadImage(@NotNull File file) {
    BufferedImage image = null;
    Validator.Result validityState = checkFileExistence(file);

    if (validityState.getSeverity() == Validator.Severity.OK) {
      FileType fileType = FileType.fromFile(file);
      if (fileType == FileType.RASTER_IMAGE_CANDIDATE) {
        try {
          image = ImageIO.read(file);
        }
        catch (IOException e) {
          validityState = Validator.Result.fromThrowable(e);
        }
      }
    }

    updateValidityStateAndResizability(file, validityState, image != null);
    return image;
  }

  @NotNull
  private Validator.Result checkFileExistence(@NotNull File file) {
    if (!file.exists()) {
      return Validator.Result.fromNullableMessage("File " + file.getName() + " does not exist");
    }
    if (file.isDirectory()) {
      return new Validator.Result(Validator.Severity.WARNING, "Please select " + getIndefiniteArticlePrefixFor(myRole) + myRole + " file");
    }

    return Validator.Result.OK;
  }

  @NotNull
  private static String getIndefiniteArticlePrefixFor(@NotNull String nounClause) {
    if (nounClause.isEmpty()) {
      return "";
    }
    return StringUtil.isVowel(Character.toLowerCase(nounClause.charAt(0))) ? "an " : "a ";
  }

  /**
   * Updates validity state and resizability asynchronously on the UI thread.
   */
  @AnyThread
  private void updateValidityStateAndResizability(@NotNull File file, @NotNull Validator.Result validityState, boolean resizable) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (FileUtil.filesEqual(file, myImagePath.getValueOrNull())) {
        myValidityState.set(validityState);
        myXmlDrawableIsResizable.set(resizable);
      }
    });
  }

  private static boolean isVectorGraphics(@Nullable FileType fileType) {
    return fileType == FileType.XML_DRAWABLE || fileType == FileType.SVG || fileType == FileType.LAYERED_IMAGE;
  }

  @Nullable
  private static FileType getFileType(@Nullable File file) {
    return file == null ? null : FileType.fromFile(file);
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
