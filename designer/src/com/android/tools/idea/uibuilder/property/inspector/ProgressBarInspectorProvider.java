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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.*;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class ProgressBarInspectorProvider implements InspectorProvider {
  private InspectorComponent myComponent;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
    return components.size() == 1 && components.get(0).getTagName().equals(PROGRESS_BAR);
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new ProgressBarInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties);
    return myComponent;
  }

  /**
   * ProgressBar inspector component. Has a dual view depending on the value of determinate.
   */
  private static class ProgressBarInspectorComponent implements InspectorComponent {
    private final NlReferenceEditor myStyleEditor;
    private final NlReferenceEditor myDrawableEditor;
    private final NlReferenceEditor myTintEditor;
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
    private NlProperty myVisibility;
    private NlProperty myDesignVisibility;
    private NlProperty myIndeterminate;
    private JLabel myDrawableLabel;
    private JLabel myTintLabel;
    private JLabel myMaxLabel;
    private JLabel myProgressLabel;

    public ProgressBarInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      Project project = propertiesManager.getProject();
      myStyleEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDrawableEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myTintEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myMaxEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myProgressEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myVisibilityEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      myDesignVisibilityEditor = NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      myIndeterminateEditor = NlBooleanEditor.createForInspector(createIndeterminateListener());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
      myStyle = properties.get(ATTR_STYLE);
      myProgressDrawable = properties.get(ATTR_PROGRESS_DRAWABLE);
      myIndeterminateDrawable = properties.get(ATTR_INDETERMINATE_DRAWABLE);
      myProgressTint = properties.get(ATTR_PROGRESS_TINT);
      myIndeterminateTint = properties.get(ATTR_INDETERMINATE_TINT);
      myMax = properties.get(ATTR_MAXIMUM);
      myProgress = properties.get(ATTR_PROGRESS);
      myVisibility = properties.get(ATTR_VISIBILITY);
      myDesignVisibility = myVisibility.getDesignTimeProperty();
      myIndeterminate = properties.get(ATTR_INDETERMINATE);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 9;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      inspector.addTitle("ProgressBar");
      inspector.addComponent(ATTR_STYLE, myStyle.getTooltipText(), myStyleEditor.getComponent());
      myDrawableLabel = inspector.addComponent(ATTR_PROGRESS_DRAWABLE, myProgressDrawable.getTooltipText(), myDrawableEditor.getComponent());
      myTintLabel = inspector.addComponent(ATTR_PROGRESS_TINT, myProgressTint.getTooltipText(), myTintEditor.getComponent());
      myMaxLabel = inspector.addComponent(ATTR_MAXIMUM, myMax.getTooltipText(), myMaxEditor.getComponent());
      myProgressLabel = inspector.addComponent(ATTR_PROGRESS, myProgress.getTooltipText(), myProgressEditor.getComponent());
      inspector.addComponent(ATTR_VISIBILITY, myVisibility.getTooltipText(), myVisibilityEditor.getComponent());
      JLabel designVisibility = inspector.addComponent(ATTR_VISIBILITY, myDesignVisibility.getTooltipText(), myDesignVisibilityEditor.getComponent());
      designVisibility.setIcon(AndroidIcons.NeleIcons.DesignProperty);
      inspector.addComponent(ATTR_INDETERMINATE, myIndeterminate.getTooltipText(), myIndeterminateEditor.getComponent());
      refresh();
    }

    @Override
    public void refresh() {
      myStyleEditor.setProperty(myStyle);
      boolean indeterminate = VALUE_TRUE.equalsIgnoreCase(myIndeterminate.getResolvedValue());
      if (indeterminate) {
        myDrawableEditor.setProperty(myIndeterminateDrawable);
        myDrawableLabel.setText(ATTR_INDETERMINATE_DRAWABLE);
        myTintEditor.setProperty(myIndeterminateTint);
        myTintLabel.setText(ATTR_INDETERMINATE_TINT);
      }
      else {
        myDrawableEditor.setProperty(myProgressDrawable);
        myDrawableLabel.setText(ATTR_PROGRESS_DRAWABLE);
        myTintEditor.setProperty(myProgressTint);
        myTintLabel.setText(ATTR_PROGRESS_TINT);
        myMaxEditor.setProperty(myMax);
        myProgressEditor.setProperty(myProgress);
      }
      myMaxEditor.getComponent().setVisible(!indeterminate);
      myMaxLabel.setVisible(!indeterminate);
      myProgressEditor.getComponent().setVisible(!indeterminate);
      myProgressLabel.setVisible(!indeterminate);
      myVisibilityEditor.setProperty(myVisibility);
      myDesignVisibilityEditor.setProperty(myDesignVisibility);
      myIndeterminateEditor.setProperty(myIndeterminate);
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
