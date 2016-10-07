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
package com.android.tools.idea.uibuilder.api;

/**
 * Describes how {@link PaletteComponentHandler#getXml} should generate XML.
 */
public enum XmlType {
  /** XML for creating a component in a layout file */
  COMPONENT_CREATION,

  /** XML for previewing a component on the palette */
  PREVIEW_ON_PALETTE,

  /** XML for previewing a component while it is being dragged from the palette */
  DRAG_PREVIEW
}
