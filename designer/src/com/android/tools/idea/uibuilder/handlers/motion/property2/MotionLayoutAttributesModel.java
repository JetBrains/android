/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property2.api.PropertiesModel;
import com.android.tools.idea.common.property2.api.PropertiesModelListener;
import com.android.tools.idea.common.property2.api.PropertiesTable;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesModel} for motion layout property editor.
 */
public class MotionLayoutAttributesModel implements PropertiesModel<MotionPropertyItem>, Disposable {
  private final AndroidFacet myFacet;
  private final List<PropertiesModelListener> myListeners;
  private final MotionLayoutPropertyProvider myPropertyProvider;
  private final TimelineHelper myTimelineHelper;
  private PropertiesTable<MotionPropertyItem> myPropertiesTable;

  public MotionLayoutAttributesModel(@NotNull Disposable parentDisposable, @NotNull AndroidFacet facet) {
    myFacet = facet;
    myListeners = new ArrayList<>();
    myPropertyProvider = new MotionLayoutPropertyProvider(this);
    myTimelineHelper = new TimelineHelper(new PropertiesTimelineListener());
    myPropertiesTable = PropertiesTable.Companion.emptyTable();
    Disposer.register(parentDisposable, this);
  }

  @Override
  public void dispose() {
    myTimelineHelper.setSurface(null);
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public Project getProject() {
    return myFacet.getModule().getProject();
  }

  @Nullable
  public DesignSurface getSurface() {
    return myTimelineHelper.getSurface();
  }

  public void setSurface(@Nullable DesignSurface surface) {
    myTimelineHelper.setSurface(surface);
  }

  @NotNull
  @Override
  public PropertiesTable<MotionPropertyItem> getProperties() {
    return myPropertiesTable;
  }

  @Override
  public void deactivate() {
    myPropertiesTable = PropertiesTable.Companion.emptyTable();
  }

  @Override
  public void addListener(@NotNull PropertiesModelListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull PropertiesModelListener listener) {
    myListeners.remove(listener);
  }

  private void displayItem(@NotNull SmartPsiElementPointer<XmlTag> tagPointer, @Nullable NlComponent component) {
    if (component == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      myPropertiesTable = myPropertyProvider.getProperties(component, tagPointer);
      //noinspection unchecked
      myListeners.forEach(listener -> listener.propertiesGenerated(this));
    });
  }

  private class PropertiesTimelineListener implements TimelineListener {
    @Override
    public void updateTransition(@NotNull MotionSceneModel.TransitionTag transition, @Nullable NlComponent component) {
      displayItem(transition.getTag(), component);
    }

    @Override
    public void updateConstraintSet(@NotNull MotionSceneModel.ConstraintSet constraintSet, @Nullable NlComponent component) {
      displayItem(constraintSet.getTag(), component);
    }

    @Override
    public void updateSelection(@NotNull MotionSceneModel.KeyFrame keyFrame, @Nullable NlComponent component) {
      displayItem(keyFrame.getTag(), component);
    }
  }
}
