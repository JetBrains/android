/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.ui.dataviewer;

import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A class which provides a view for a target data buffer. For example, an image may be rendered directly, while an xml
 * file will be shown in syntax highlighted manner. If a file cannot be displayed, an {@link INVALID} viewer is returned.
 */
public interface DataViewer {

  enum Style {
    /**
     * A style that indicates we should attempt to render data as is, without formatting it. This
     * may indicate text data that we want to render unmodified, or it may indicate binary data.
     */
    RAW,

    /**
     * A style for text data that is properly formatted in some way, so that the view can do this
     * like color fields or reconfigure indentation.
     * <p>
     * For example, suppose the raw bytes for JSON data are a single line string
     * <code>{"menu": {id": "file", "value": "File"}}</code>; this text may get expanded onto
     * multiple lines and provide controls for collapsing / expanding JSON objects.
     */
    PRETTY,

    /**
     * A fallback style for when things have gone wrong, such as expecting text data but receiving
     * binary, trying to load a corrupt image, etc. An invalid style will present a useful error
     * message to the user.
     */
    INVALID
  }

  @NotNull
  JComponent getComponent();

  @NotNull
  Style getStyle();
}

