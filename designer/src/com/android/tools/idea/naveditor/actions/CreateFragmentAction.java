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
package com.android.tools.idea.naveditor.actions;

import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IconUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

/**
 * Action to create a new fragment in the navigation editor.
 */
public class CreateFragmentAction extends AnAction {
  public static final String ACTION_ID = "CreateFragment";

  private final NavDesignSurface mySurface;

  public CreateFragmentAction(@NotNull NavDesignSurface surface) {
    super(IconUtil.getAddIcon());
    mySurface = surface;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    new WriteCommandAction(mySurface.getProject(), "Create Fragment", mySurface.getModel().getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        NlComponent parent = mySurface.getCurrentNavigation();
        XmlTag tag = parent.getTag().createChildTag(NavigationSchema.TAG_FRAGMENT, null, null, false);
        mySurface.getModel().createComponent(tag, parent, null);
      }
    }.execute();
  }
}
