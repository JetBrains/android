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
package org.jetbrains.android.dom.structure.manifest;

import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.psi.PsiClass;
import icons.StudioIcons;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ActivityPresentationProvider extends PresentationProvider<Activity> {
  @Nullable
  @Override
  public Icon getIcon(Activity activity) {
    return StudioIcons.Shell.Filetree.ACTIVITY;
  }

  @Nullable
  @Override
  public String getName(Activity activity) {
    final PsiClass value = activity.getActivityClass().getValue();
    return value == null ? null : value.getName();
  }

  @Nullable
  @Override
  public String getTypeName(Activity activity) {
    return "Activity";
  }
}
