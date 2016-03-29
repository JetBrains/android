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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A "fake" component whose behavior is specified by the ViewHandler. Used for the Device Screen and PreferenceScreen root nodes in the
 * Structure panel.
 */
final class FakeComponent extends NlComponent {
  private final ViewHandler myViewHandler;

  FakeComponent(@NotNull NlModel model, @NotNull XmlTag tag, @NotNull ViewHandler viewHandler) {
    super(model, tag);
    myViewHandler = viewHandler;
  }

  @NotNull
  @Override
  public ViewHandler getViewHandler() {
    return myViewHandler;
  }

  @Nullable
  @Override
  public ViewGroupHandler getViewGroupHandler() {
    return null;
  }
}
