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
package com.android.tools.idea.uibuilder.surface;

import org.jetbrains.annotations.NotNull;

/** Describes different types of zoom actions */
public enum ZoomType {
  /**
   * Zoom to fit (the screen view port)
   */
  FIT("Zoom to Fit Screen"),

  /**
   * Zoom to fit, but do not zoom more than 100%
   */
  FIT_INTO("Zoom out to Fit Screen"),

  /**
   * Zoom to actual size (100%)
   */
  ACTUAL("100%"),

  /**
   * Zoom in
   */
  IN("Zoom In"),

  /**
   * Zoom out
   */
  OUT("Zoom Out"),

  /**
   * Zoom to match the exact device size (depends on the monitor dpi)
   */
  SCREEN("Exact Device Size");

  ZoomType(@NotNull String label) {
    myLabel = label;
  }

  /** Describes the zoom action to the user */
  public String getLabel() {
    return myLabel;
  }

  /** Returns true if this zoom type should be shown to the user */
  public boolean showInMenu() {
    return this != FIT_INTO && this != SCREEN; // these are not yet supported
  }

  @Override
  public String toString() {
    return getLabel();
  }

  private final String myLabel;
}