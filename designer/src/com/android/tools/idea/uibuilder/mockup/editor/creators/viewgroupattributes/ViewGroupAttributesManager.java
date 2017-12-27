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
package com.android.tools.idea.uibuilder.mockup.editor.creators.viewgroupattributes;

import com.android.tools.idea.common.model.AttributesTransaction;

/**
 * Implement this interface if the container of the widget created by a
 * {@link com.android.tools.idea.uibuilder.mockup.editor.creators.BaseWidgetCreator}
 * needs to add special attributes to its children
 *
 * @see com.android.tools.idea.uibuilder.mockup.editor.creators.BaseWidgetCreator#setViewGroupAttributesManager(ViewGroupAttributesManager)
 */
public interface ViewGroupAttributesManager {
  void addLayoutAttributes(AttributesTransaction transaction);
}
