/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model;

/**
 * This interface is used for any {@link JList} that uses the {@link DragAndDropListModel}. The list model relies on a unique identifier
 * for each object in the list.
 */
public interface DragAndDropModelListElement {

  /**
   * @return A unique id that helps identify this element in the {@link DragAndDropListModel}
   */
  int getId();
}
