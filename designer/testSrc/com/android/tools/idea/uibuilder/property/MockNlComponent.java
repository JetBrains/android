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
package com.android.tools.idea.uibuilder.property;

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.psi.xml.XmlTag;

import static org.mockito.Mockito.mock;

public class MockNlComponent extends NlComponent {
  private MockNlComponent(@NotNull NlModel model, @NotNull XmlTag tag) {
    super(model, tag);
  }

  public static NlComponent create(@NotNull XmlTag tag) {
    return new MockNlComponent(mock(NlModel.class), tag);
  }
}
