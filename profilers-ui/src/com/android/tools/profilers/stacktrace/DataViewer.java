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
package com.android.tools.profilers.stacktrace;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A class which provides a view for a target data buffer. For example, an image may be rendered directly, while an xml
 * file will be shown in syntax highlighted manner. If a file cannot be displayed, a message indicating that a preview
 * is not available will be shown.
 */
public interface DataViewer {

  @NotNull
  JComponent getComponent();

  /**
   * The (width x height) size of the target image data, or {@code null} if the concept of a size
   * doesn't make sense for the file type (e.g. txt, xml)
   */
  @Nullable
  Dimension getDimension();
}
