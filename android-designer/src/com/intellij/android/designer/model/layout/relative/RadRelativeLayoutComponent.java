/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model.layout.relative;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewContainer;
import com.intellij.designer.model.IComponentDeletionParticipant;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadRelativeLayoutComponent extends RadViewContainer implements IComponentDeletionParticipant {
  @Override
  public boolean deleteChildren(@NotNull RadComponent parent, @NotNull List<RadComponent> deleted) {
    List<RadViewComponent> deletedComponents = RadViewComponent.getViewComponents(deleted);
    List<RadViewComponent> movedComponents = Collections.emptyList();
    RadViewComponent relativeLayout = (RadViewComponent)parent;
    final DeletionHandler handler = new DeletionHandler(deletedComponents, movedComponents, relativeLayout);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        handler.updateConstraints();
      }
    });
    return false;
  }

  @Override
  public void setViewInfo(ViewInfo viewInfo) {
    super.setViewInfo(viewInfo);
    DependencyGraph.refresh(this);
  }

  @Nullable
  public static String parseIdValue(String idValue) {
    if (!StringUtil.isEmpty(idValue)) {
      if (idValue.startsWith("@android:id/")) {
        return idValue;
      }
      return idValue.substring(idValue.indexOf('/') + 1);
    }
    return null;
  }
}