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

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.npw.assetstudio.TextRenderUtil;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An asset that represents a text value and related settings.
 */
@SuppressWarnings("UseJBColor")
public final class TextAsset extends BaseAsset {
  private static final String TEXT_PROPERTY = "text";
  private static final String FONT_FAMILY_PROPERTY = "fontFamily";

  private static final String DEFAULT_TEXT = "Aa";
  private static final String PREFERRED_FONT_FAMILY = "Roboto";
  private static final int FONT_SIZE = 144;  // Large value for crisp icons.

  private final StringProperty myText = new StringValueProperty(DEFAULT_TEXT);
  private final StringProperty myFontFamily = new StringValueProperty();
  private final List<String> myAllFontFamilies;

  @NotNull private String myDefaultText = DEFAULT_TEXT;
  @NotNull private String myDefaultFontFamily;

  @NotNull private final Object myLock = new Object();
  @GuardedBy("myLock")
  @Nullable private ListenableFuture<String> myXmlDrawableFuture;

  public TextAsset() {
    myAllFontFamilies = ImmutableList.copyOf(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
    myDefaultFontFamily = normalizeFontFamily(PREFERRED_FONT_FAMILY);
    if (!myDefaultFontFamily.isEmpty()) {
      myFontFamily.set(myDefaultFontFamily);
    }
    InvalidationListener listener = () -> {
      synchronized (myLock) {
        myXmlDrawableFuture = null;
      }
    };
    myText.addListener(listener);
    myFontFamily.addListener(listener);
    color().setValue(Color.BLACK);
    color().addListener(listener);
  }

  /**
   * Sets the default text. Also sets the current text if it was the same as default.
   * Has to be called immediately after construction of the object.
   *
   * @param text the default text
   */
  @UiThread
  public void setDefaultText(@NotNull String text) {
    boolean wasDefault = myText.get().equals(myDefaultText);
    myDefaultText = text;
    if (wasDefault) {
      myText.set(myDefaultText);
    }
  }

  /**
   * Sets the default font family. Also sets the selected font family if it was the same as default.
   * Has to be called immediately after construction of the object.
   *
   * @param fontFamily the default font family
   */
  @UiThread
  public void setDefaultFontFamily(@NotNull String fontFamily) {
    boolean wasDefault = myFontFamily.get().equals(myDefaultFontFamily);
    myDefaultFontFamily = normalizeFontFamily(fontFamily);
    if (wasDefault) {
      selectFontFamily(myDefaultFontFamily);
    }
  }

  private void selectFontFamily(@NotNull String fontFamily) {
    String family = normalizeFontFamily(fontFamily);
    if (!family.isEmpty()) {
      myFontFamily.set(family);
    }
  }

  @NotNull
  private String normalizeFontFamily(@NotNull String fontFamily) {
    return myAllFontFamilies.contains(fontFamily) ?
           fontFamily :
           myAllFontFamilies.isEmpty() ?
           "" :
           myAllFontFamilies.get(0);
  }

  /**
   * Returns all font families available for text rendering.
   */
  @NotNull
  public static List<String> getAllFontFamilies() {
    return ImmutableList.copyOf(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
  }

  /**
   * The text value that will be rendered into the final asset.
   */
  @NotNull
  public StringProperty text() {
    return myText;
  }

  /**
   * The font family associated with this text. Use {@link #getAllFontFamilies()} to get a list of
   * suitable values for this property.
   */
  @NotNull
  public StringProperty fontFamily() {
    return myFontFamily;
  }

  /**
   * Returns the default font family, or an empty string if no font families are available in the graphics environment.
   */
  @NotNull
  public String defaultFontFamily() {
    if (myAllFontFamilies.contains(PREFERRED_FONT_FAMILY)) {
      return PREFERRED_FONT_FAMILY;
    }
    return myAllFontFamilies.isEmpty() ? "" : myAllFontFamilies.get(0);
  }

  /**
   * Renders the given text to an XML drawable and returns its string representation as a future.
   */
  @AnyThread
  @NotNull
  public ListenableFuture<String> getXmlDrawable() {
    synchronized (myLock) {
      if (myXmlDrawableFuture == null) {
        String text = myText.get();
        String fontFamily = myFontFamily.get();
        Color color = color().getValueOrNull();
        int opacityPercent = opacityPercent().get();
        myXmlDrawableFuture = FutureUtils.executeOnPooledThread(() ->
            VectorTextRenderer.renderToVectorDrawable(text, fontFamily, FONT_SIZE, color, opacityPercent / 100.));
      }
      return myXmlDrawableFuture;
    }
  }

  @Override
  @NotNull
  public ListenableFuture<BufferedImage> toImage() {
    TextRenderUtil.Options options = new TextRenderUtil.Options();
    options.font = Font.decode(myFontFamily + " " + FONT_SIZE);
    options.foregroundColor = color().getValueOr(Color.BLACK).getRGB();
    return Futures.immediateFuture(TextRenderUtil.renderTextImage(myText.get(), 1, options));
  }


  @UiThread
  @Override
  public PersistentState getState() {
    PersistentState state = super.getState();
    state.set(TEXT_PROPERTY, myText.get(), myDefaultText);
    state.set(FONT_FAMILY_PROPERTY, myFontFamily.get(), myDefaultFontFamily);
    return state;
  }

  @UiThread
  @Override
  public void loadState(@NotNull PersistentState state) {
    super.loadState(state);
    String text = state.get(TEXT_PROPERTY, myDefaultText);
    if (!text.isEmpty()) {
      myText.set(text);
    }
    String fontFamily = state.get(FONT_FAMILY_PROPERTY, myDefaultFontFamily);
    selectFontFamily(fontFamily);
  }
}
