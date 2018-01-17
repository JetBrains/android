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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.*;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class ProgressBarInspectorProvider implements InspectorProvider<NlPropertiesManager> {
  private InspectorComponent<NlPropertiesManager> myComponent;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    return components.size() == 1 && components.get(0).getTagName().equals(PROGRESS_BAR);
  }

  @NotNull
  @Override
  public InspectorComponent<NlPropertiesManager> createCustomInspector(@NotNull List<NlComponent> components,
                                                                       @NotNull Map<String, NlProperty> properties,
                                                                       @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new ProgressBarInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties, propertiesManager);
    return myComponent;
  }

  @Override
  public void resetCache() {
    myComponent = null;
  }

  /**
   * ProgressBar inspector component. Has a dual view depending on the value of determinate.
   */
  private static class ProgressBarInspectorComponent implements InspectorComponent<NlPropertiesManager> {
    private final InspectorPanel myInspector;
    private final NlEnumEditor myStyleEditor;
    private final NlReferenceEditor myDrawableEditor;
    private final NlReferenceEditor myIndeterminateDrawableEditor;
    private final NlReferenceEditor myTintEditor;
    private final NlReferenceEditor myIndeterminateTintEditor;
    private final NlReferenceEditor myMaxEditor;
    private final NlReferenceEditor myProgressEditor;
    private final NlEnumEditor myVisibilityEditor;
    private final NlEnumEditor myDesignVisibilityEditor;
    private final NlBooleanEditor myIndeterminateEditor;

    private NlProperty myStyle;
    private NlProperty myProgressDrawable;
    private NlProperty myIndeterminateDrawable;
    private NlProperty myProgressTint;
    private NlProperty myIndeterminateTint;
    private NlProperty myMax;
    private NlProperty myProgress;
    @Nullable private NlProperty myVisibility;
    @Nullable private NlProperty myDesignVisibility;
    private NlProperty myIndeterminate;

    public ProgressBarInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      myInspector = propertiesManager.getInspector();
      Project project = propertiesManager.getProject();
      myStyleEditor = NlEnumEditor.createForInspectorWithBrowseButton(DEFAULT_LISTENER);
      myDrawableEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myIndeterminateDrawableEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myTintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myIndeterminateTintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myMaxEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myProgressEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myVisibilityEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myDesignVisibilityEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myIndeterminateEditor = NlBooleanEditor.createForInspector(createIndeterminateListener());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myStyle = properties.get(ATTR_STYLE);
      myProgressDrawable = properties.get(ATTR_PROGRESS_DRAWABLE);
      myIndeterminateDrawable = properties.get(ATTR_INDETERMINATE_DRAWABLE);
      myProgressTint = properties.get(ATTR_PROGRESS_TINT);
      myIndeterminateTint = properties.get(ATTR_INDETERMINATE_TINT);
      myMax = properties.get(ATTR_MAXIMUM);
      myProgress = properties.get(ATTR_PROGRESS);
      myVisibility = properties.get(ATTR_VISIBILITY);
      myDesignVisibility = myVisibility != null ? myVisibility.getDesignTimeProperty() : null;
      myIndeterminate = properties.get(ATTR_INDETERMINATE);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 11;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      // Call refresh first to hide the indeterminate/determinate fields without flicker
      refresh();
      inspector.addTitle("ProgressBar");
      inspector.addComponent(ATTR_STYLE, myStyle.getTooltipText(), myStyleEditor.getComponent());
      myDrawableEditor
        .setLabel(inspector.addComponent(ATTR_PROGRESS_DRAWABLE, myProgressDrawable.getTooltipText(), myDrawableEditor.getComponent()));
      myIndeterminateDrawableEditor.setLabel(inspector.addComponent(ATTR_INDETERMINATE_DRAWABLE, myIndeterminateDrawable.getTooltipText(),
                                                                    myIndeterminateDrawableEditor.getComponent()));
      myTintEditor.setLabel(inspector.addComponent(ATTR_PROGRESS_TINT,
                                                   myProgressTint != null ? myProgressTint.getTooltipText() : null,
                                                   myTintEditor.getComponent()));
      myIndeterminateTintEditor.setLabel(inspector.addComponent(ATTR_INDETERMINATE_TINT,
                                                                myIndeterminateTint != null ? myIndeterminateTint.getTooltipText() : null,
                                                                myIndeterminateTintEditor.getComponent()));
      myMaxEditor.setLabel(inspector.addComponent(ATTR_MAXIMUM, myMax.getTooltipText(), myMaxEditor.getComponent()));
      myProgressEditor.setLabel(inspector.addComponent(ATTR_PROGRESS, myProgress.getTooltipText(), myProgressEditor.getComponent()));
      inspector.addComponent(ATTR_VISIBILITY,
                             myVisibility != null ? myVisibility.getTooltipText() : null,
                             myVisibilityEditor.getComponent());
      JLabel designVisibility = inspector.addComponent(ATTR_VISIBILITY,
                                                       myDesignVisibility != null ? myDesignVisibility.getTooltipText() : null,
                                                       myDesignVisibilityEditor.getComponent());
      designVisibility.setIcon(StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY);
      inspector.addComponent(ATTR_INDETERMINATE, myIndeterminate.getTooltipText(), myIndeterminateEditor.getComponent());
    }

    @Override
    public void refresh() {
      myStyleEditor.setProperty(myStyle);
      myDrawableEditor.setProperty(myProgressDrawable);
      myIndeterminateDrawableEditor.setProperty(myIndeterminateDrawable);
      if (myProgressTint != null) {
        myTintEditor.setProperty(myProgressTint);
      }
      if (myIndeterminateTint != null) {
        myIndeterminateTintEditor.setProperty(myIndeterminateTint);
      }
      myMaxEditor.setProperty(myMax);
      myProgressEditor.setProperty(myProgress);
      if (myVisibility != null) {
        myVisibilityEditor.setProperty(myVisibility);
      }
      if (myDesignVisibility != null) {
        myDesignVisibilityEditor.setProperty(myDesignVisibility);
      }
      myIndeterminateEditor.setProperty(myIndeterminate);
      updateVisibility();
    }

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return ImmutableList.of(
        myStyleEditor,
        myDrawableEditor,
        myIndeterminateDrawableEditor,
        myTintEditor,
        myIndeterminateTintEditor,
        myMaxEditor,
        myProgressEditor,
        myVisibilityEditor,
        myDesignVisibilityEditor,
        myIndeterminateEditor);
    }

    @Override
    public void updateVisibility() {
      if (!myInspector.getFilter().isEmpty()) {
        return;
      }
      boolean indeterminate = VALUE_TRUE.equalsIgnoreCase(myIndeterminate.getResolvedValue());
      myDrawableEditor.setVisible(!indeterminate);
      myTintEditor.setVisible(myProgressTint != null && !indeterminate);
      myIndeterminateDrawableEditor.setVisible(myIndeterminateTint != null && indeterminate);
      myIndeterminateTintEditor.setVisible(indeterminate);
      myMaxEditor.setVisible(!indeterminate);
      myProgressEditor.setVisible(!indeterminate);
    }

    private NlEditingListener createIndeterminateListener() {
      return new NlEditingListener() {
        @Override
        public void stopEditing(@NotNull NlComponentEditor editor, @Nullable Object value) {
          myIndeterminate.setValue(value);
          refresh();
        }

        @Override
        public void cancelEditing(@NotNull NlComponentEditor editor) {
        }
      };
    }
  }
}
