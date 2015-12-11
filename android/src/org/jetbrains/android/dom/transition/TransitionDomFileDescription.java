/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.dom.transition;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.android.dom.transition.TransitionDomUtil.TRANSITION_SET_TAG;

public class TransitionDomFileDescription extends AndroidResourceDomFileDescription<TransitionSet> {
  public TransitionDomFileDescription() {
    super(TransitionSet.class, TRANSITION_SET_TAG, ResourceFolderType.TRANSITION);
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  public static boolean isTransitionFile(@NotNull final XmlFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return new TransitionDomFileDescription().isMyFile(file, null);
      }
    });
  }
}
