/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.transition.TransitionDomUtil;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateResourceFileActionGroup extends DefaultActionGroup {
  private final CreateResourceFileAction myMajorAction;

  public CreateResourceFileActionGroup() {
    CreateResourceFileAction a = new CreateResourceFileAction();
    a.add(new CreateMultiRootResourceFileAction(ResourceType.LAYOUT.getDisplayName(), ResourceFolderType.LAYOUT));

    a.add(new CreateMultiRootResourceFileAction(ResourceType.XML.getDisplayName(), ResourceFolderType.XML) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidXmlResourcesUtil.getPossibleRoots(facet);
      }
    });

    a.add(new CreateMultiRootResourceFileAction(ResourceType.DRAWABLE.getDisplayName(), ResourceFolderType.DRAWABLE) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidDrawableDomUtil.getPossibleRoots(facet);
      }
    });

    a.add(new CreateTypedResourceFileAction(ResourceType.COLOR.getDisplayName(), ResourceFolderType.COLOR, false, false));
    a.add(new CreateTypedResourceFileAction("Values", ResourceFolderType.VALUES, true, false));
    a.add(new CreateTypedResourceFileAction(ResourceType.MENU.getDisplayName(), ResourceFolderType.MENU, false, false));

    a.add(new CreateMultiRootResourceFileAction(ResourceType.ANIM.getDisplayName(), ResourceFolderType.ANIM) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidAnimationUtils.getPossibleRoots();
      }
    });

    a.add(new CreateMultiRootResourceFileAction(ResourceType.ANIMATOR.getDisplayName(), ResourceFolderType.ANIMATOR) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidAnimatorUtil.getPossibleRoots();
      }
    });

    a.add(new CreateMultiRootResourceFileAction(ResourceType.TRANSITION.getDisplayName(), ResourceFolderType.TRANSITION) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return TransitionDomUtil.getPossibleRoots();
      }
    });

    myMajorAction = a;
    add(a);
    for (CreateTypedResourceFileAction subaction : a.getSubactions()) {
      add(subaction);
    }
  }

  @NotNull
  public CreateResourceFileAction getCreateResourceFileAction() {
    return myMajorAction;
  }
}
