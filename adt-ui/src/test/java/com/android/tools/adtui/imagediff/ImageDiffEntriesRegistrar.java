/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.imagediff;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers and keeps a register of {@link ImageDiffEntry} items.
 *
 * An entries registrar should create one or more {@link ImageDiffEntry} items and pass them to the {@link #register} method.
 * All the entries belonging to a entries registrar should be added to {@link ImageDiffUtil.IMAGE_DIFF_ENTRIES}.
 */
abstract class ImageDiffEntriesRegistrar {


  private List<ImageDiffEntry> myImageDiffEntries = new ArrayList<>();

  /**
   * Registers an {@link ImageDiffEntry} by adding it to the list of entries of this class.
   */
  protected final void register(ImageDiffEntry imageDiffEntry) {
    myImageDiffEntries.add(imageDiffEntry);
  }

  /**
   * Returns the list of registered {@link ImageDiffEntry}.
   * The entries are used for running the tests of a subclass and exporting its baseline images.
   */
  final List<ImageDiffEntry> getImageDiffEntries() {
    return myImageDiffEntries;
  }
}
