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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.resources.ResourceType;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.module.Module;
import icons.AndroidIcons;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;
import java.util.Set;

import static com.android.SdkConstants.TOOLS_URI;

public class BrowsePanel extends JPanel {
  private final Context myContext;

  public interface Context {
    @Nullable
    NlProperty getProperty();

    @Nullable
    default NlProperty getDesignProperty() {
      throw new UnsupportedOperationException();
    }

    default void cancelEditing() {
    }

    default void stopEditing(@Nullable Object newValue) {
      NlProperty property = getProperty();
      if (property != null) {
        property.setValue(newValue);
      }
    }

    default void addDesignProperty() {
      throw new UnsupportedOperationException();
    }

    default void removeDesignProperty() {
      throw new UnsupportedOperationException();
    }
  }

  public BrowsePanel(@NotNull Context context) {
    this(context, true);
  }

  public BrowsePanel(@NotNull Context context, boolean showDesignButton) {
    myContext = context;
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(createActionButton(createBrowseAction()));
    if (showDesignButton) {
      add(createActionButton(createDesignAction()));
    }
  }

  private static ActionButton createActionButton(@NotNull AnAction action) {
    return new ActionButton(action,
                            action.getTemplatePresentation().clone(),
                            ActionPlaces.UNKNOWN,
                            ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
  }

  private AnAction createBrowseAction() {
    return new AnAction() {
      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        NlProperty property = myContext.getProperty();
        if (property != null && hasResourceChooser(property)) {
          presentation.setIcon(AllIcons.General.Ellipsis);
          presentation.setText("Click to pick a resource");
          presentation.setVisible(true);
          presentation.setEnabled(true);
        }
        else {
          presentation.setIcon(null);
          presentation.setText(null);
          presentation.setVisible(false);
          presentation.setEnabled(false);
        }
      }

      @Override
      public void actionPerformed(AnActionEvent event) {
        displayResourcePicker();
      }
    };
  }

  private void displayResourcePicker() {
    NlProperty property = myContext.getProperty();
    if (property == null) {
      return;
    }
    ChooseResourceDialog dialog = showResourceChooser(property);
    myContext.cancelEditing();

    if (dialog.showAndGet()) {
      myContext.stopEditing(dialog.getResourceName());
    }
  }

  public static ChooseResourceDialog showResourceChooser(@NotNull NlProperty property) {
    Module module = property.getModel().getModule();
    AttributeDefinition definition = property.getDefinition();
    EnumSet<ResourceType> types = getResourceTypes(property.getName(), definition);
    //return new ChooseResourceDialog(module, types, property.getValue(), property.getTag());
    return ChooseResourceDialog.builder()
      .setModule(module)
      .setTypes(types)
      .setCurrentValue(property.getValue())
      .setTag(property.getTag())
      .build();
  }

  public static boolean hasResourceChooser(@NotNull NlProperty property) {
    return !getResourceTypes(property.getName(), property.getDefinition()).isEmpty();
  }

  @NotNull
  public static EnumSet<ResourceType> getResourceTypes(@NotNull String propertyName, @Nullable AttributeDefinition definition) {
    Set<AttributeFormat> formats = definition != null ? definition.getFormats() : EnumSet.allOf(AttributeFormat.class);
    // for some special known properties, we can narrow down the possible types (rather than the all encompassing reference type)
    ResourceType type = AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(propertyName);
    return type == null ? AttributeFormat.convertTypes(formats) : EnumSet.of(type);
  }

  private enum DesignState {NOT_APPLICABLE, IS_DESIGN_PROPERTY, HAS_DESIGN_PROPERTY, MISSING_DESIGN_PROPERTY}

  private AnAction createDesignAction() {
    return new AnAction() {
      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        switch (checkDesignState()) {
          case MISSING_DESIGN_PROPERTY:
            presentation.setIcon(AndroidIcons.NeleIcons.DesignProperty);
            presentation.setText("Click to specify design property");
            presentation.setVisible(true);
            presentation.setEnabled(true);
            break;
          case IS_DESIGN_PROPERTY:
            presentation.setIcon(AllIcons.Actions.Delete);
            presentation.setText("Click to remove this design property");
            presentation.setVisible(true);
            presentation.setEnabled(true);
            break;
          default:
            presentation.setIcon(null);
            presentation.setText(null);
            presentation.setVisible(false);
            presentation.setEnabled(false);
            break;
        }
      }

      @Override
      public void actionPerformed(AnActionEvent event) {
        switch (checkDesignState()) {
          case MISSING_DESIGN_PROPERTY:
            myContext.addDesignProperty();
            break;
          case IS_DESIGN_PROPERTY:
            myContext.removeDesignProperty();
            break;
          default:
        }
      }

      private DesignState checkDesignState() {
        NlProperty property = myContext.getProperty();
        if (property == null) {
          return DesignState.NOT_APPLICABLE;
        }
        if (TOOLS_URI.equals(property.getNamespace())) {
          return DesignState.IS_DESIGN_PROPERTY;
        }
        NlProperty nextProperty = myContext.getDesignProperty();
        if (nextProperty != null && nextProperty.getName().equals(property.getName()) && TOOLS_URI.equals(nextProperty.getNamespace())) {
          return DesignState.HAS_DESIGN_PROPERTY;
        }
        return DesignState.MISSING_DESIGN_PROPERTY;
      }
    };
  }
}
