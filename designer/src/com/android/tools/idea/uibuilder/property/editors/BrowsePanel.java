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
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.api.AttributeBrowser;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static com.android.SdkConstants.*;

public class BrowsePanel extends AdtSecondaryPanel {
  private final Context myContext;
  private final ActionButton myBrowseButton;
  private final ActionButton myDesignButton;
  private PropertyDesignState myDesignState;

  public interface Context {
    @Nullable
    NlProperty getProperty();

    // Overridden by table cell editor
    default void cancelEditing() {
    }

    // Overridden by table cell editor
    default void stopEditing(@Nullable Object newValue) {
      NlProperty property = getProperty();
      if (property != null) {
        property.setValue(newValue);
      }
    }

    // Overridden by table cell editor
    default void addDesignProperty() {
      throw new UnsupportedOperationException();
    }

    // Overridden by table cell editor
    default void removeDesignProperty() {
      throw new UnsupportedOperationException();
    }
  }

  public static class ContextDelegate implements Context {
    private NlComponentEditor myEditor;

    @Nullable
    @Override
    public NlProperty getProperty() {
      return myEditor != null ? myEditor.getProperty() : null;
    }

    public void setEditor(@NotNull NlComponentEditor editor) {
      myEditor = editor;
    }
  }

  // This is used from a table cell renderer only
  public BrowsePanel() {
    this(null, true);
  }

  public BrowsePanel(@Nullable Context context, boolean showDesignButton) {
    myContext = context;
    myBrowseButton = createActionButton(new BrowseAction(context));
    myDesignButton = showDesignButton ? createActionButton(createDesignAction()) : null;
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(myBrowseButton);
    myBrowseButton.setFocusable(true);
    if (myDesignButton != null) {
      add(myDesignButton);
      myDesignButton.setFocusable(true);
    }
  }

  public void setDesignState(@NotNull PropertyDesignState designState) {
    myDesignState = designState;
  }

  public void setProperty(@NotNull NlProperty property) {
    myBrowseButton.setVisible(hasBrowseDialog(property));
  }

  public void mousePressed(@NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
    if (event.getX() > rectRightColumn.getX() + rectRightColumn.getWidth() - getDesignButtonWidth()) {
      myDesignButton.click();
    }
    else if (event.getX() > rectRightColumn.getX() + rectRightColumn.getWidth() - getDesignButtonWidth() - getBrowseButtonWidth()) {
      myBrowseButton.click();
    }
  }

  public void mouseMoved(@NotNull PTable table, @NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
    table.setExpandableItemsEnabled(
      event.getX() < rectRightColumn.getX() + rectRightColumn.getWidth() - getDesignButtonWidth() - getBrowseButtonWidth());
  }

  private int getDesignButtonWidth() {
    return myDesignButton != null ? myDesignButton.getWidth() : 0;
  }

  private int getBrowseButtonWidth() {
    return myBrowseButton.isVisible() ? myBrowseButton.getWidth() : 0;
  }

  @NotNull
  private static ActionButton createActionButton(@NotNull AnAction action) {
    return new ActionButton(action,
                            action.getTemplatePresentation().clone(),
                            ActionPlaces.UNKNOWN,
                            ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
  }

  private static class BrowseAction extends AnAction {
    private final Context myContext;

    private BrowseAction(@Nullable Context context) {
      myContext = context;
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.Ellipsis);
      presentation.setText("Pick a Resource");
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
      if (myContext == null) {
        return;
      }
      NlProperty property = myContext.getProperty();
      if (property == null) {
        return;
      }
      String newValue = showBrowseDialog(property);
      myContext.cancelEditing();

      if (newValue != null) {
        myContext.stopEditing(newValue);
      }
    }
  }

  private static ChooseResourceDialog showResourceChooser(@NotNull NlProperty property) {
    Module module = property.getModel().getModule();
    EnumSet<ResourceType> types = getResourceTypes(property);
    String propertyName = property.getName();
    ResourceType defaultResourceType = getDefaultResourceType(propertyName);
    boolean isImageViewDrawable = IMAGE_VIEW.equals(property.getTagName()) &&
                                  (ATTR_SRC_COMPAT.equals(propertyName) ||
                                  ATTR_SRC.equals(propertyName));
    return ChooseResourceDialog.builder()
      .setModule(module)
      .setTypes(types)
      .setCurrentValue(property.getValue())
      .setTag(property.getTag())
      .setDefaultType(defaultResourceType)
      .setFilterColorStateLists(isImageViewDrawable)
      .build();
  }

  public static boolean hasBrowseDialog(@NotNull NlProperty property) {
    return property != EmptyProperty.INSTANCE && (getBrowser(property) != null || !getResourceTypes(property).isEmpty());
  }

  /**
   * Show a browse dialog depending on the property type.
   *
   * @return a new value or null if the dialog was cancelled.
   */
  @Nullable
  public static String showBrowseDialog(@NotNull NlProperty property) {
    AttributeBrowser browser = getBrowser(property);
    if (browser != null) {
      ViewEditor editor = new ViewEditorImpl(property.getModel());
      return browser.browse(editor, property.getValue());
    }
    else if (!getResourceTypes(property).isEmpty()) {
      ChooseResourceDialog dialog = showResourceChooser(property);
      if (dialog.showAndGet()) {
        return dialog.getResourceName();
      }
    }
    return null;
  }

  @Nullable
  private static AttributeBrowser getBrowser(@NotNull NlProperty property) {
    Project project = property.getModel().getProject();

    if (project.isDisposed()) {
      return null;
    }

    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(project);

    for (NlComponent component : property.getComponents()) {
      ViewHandler handler = viewHandlerManager.getHandler(component);
      if (handler != null) {
        AttributeBrowser browser = handler.getBrowser(property.getName());
        if (browser != null) {
          return browser;
        }
      }
    }
    return null;
  }

  @NotNull
  public static EnumSet<ResourceType> getResourceTypes(@NotNull NlProperty property) {
    String propertyName = property.getName();
    if (propertyName.equals(ATTR_ID)) {
      // Don't encourage the use of android IDs
      return EnumSet.noneOf(ResourceType.class);
    }
    AttributeDefinition definition = property.getDefinition();
    Set<AttributeFormat> formats = definition != null ? definition.getFormats() : EnumSet.allOf(AttributeFormat.class);
    // for some special known properties, we can narrow down the possible types (rather than the all encompassing reference type)
    Collection<ResourceType> types = AndroidDomUtil.getSpecialResourceTypes(propertyName);
    return types.isEmpty() ? AttributeFormat.convertTypes(formats) : EnumSet.copyOf(types);
  }

  /**
   * For some attributes, it make more sense the display a specific type by default.
   * <p>
   * For example <code>textColor</code> has more chance to have a color value than a drawable value,
   * so in the {@link ChooseResourceDialog}, we need to select the Color tab by default.
   *
   * @param propertyName The property name to get the associated default type from.
   * @return The {@link ResourceType} that should be selected by default for the provided property name.
   */
  @Nullable
  private static ResourceType getDefaultResourceType(@NotNull String propertyName) {
    String lowerCaseProperty = propertyName.toLowerCase(Locale.ENGLISH);
    if (lowerCaseProperty.contains("color")
        || lowerCaseProperty.contains("tint")) {
      return ResourceType.COLOR;
    }
    else if (lowerCaseProperty.contains("drawable")
      || propertyName.equals(ATTR_SRC)
      || propertyName.equals(ATTR_SRC_COMPAT)) {
      return ResourceType.DRAWABLE;
    }
    return null;
  }

  private AnAction createDesignAction() {
    return new AnAction() {
      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        switch (myDesignState) {
          case MISSING_DESIGN_PROPERTY:
            presentation.setIcon(StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY);
            presentation.setText("Specify Design Property");
            presentation.setVisible(true);
            presentation.setEnabled(true);
            break;
          case IS_REMOVABLE_DESIGN_PROPERTY:
            presentation.setIcon(AllIcons.Actions.Delete);
            presentation.setText("Remove this Design Property");
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
        if (myContext == null) {
          return;
        }
        switch (myDesignState) {
          case MISSING_DESIGN_PROPERTY:
            myContext.addDesignProperty();
            break;
          case IS_REMOVABLE_DESIGN_PROPERTY:
            myContext.removeDesignProperty();
            break;
          default:
        }
      }
    };
  }
}
