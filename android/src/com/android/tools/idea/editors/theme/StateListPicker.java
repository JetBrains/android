/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.GraphicalResourceRendererEditor;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class StateListPicker extends JPanel {
  private static final String API_ERROR_TEXT = "This resource requires at least an API level of %d";
  private static final ResourceType[] DIMENSIONS_ONLY = {ResourceType.DIMEN};

  public static final String PRIVATE_ERROR_PATTERN = "%s is a private framework resource";
  public static final String NON_EXISTENT_ERROR_PATTERN = "The resource %s does not exist";

  private final Module myModule;
  private final Configuration myConfiguration;
  private @Nullable ResourceHelper.StateList myStateList;
  private List<StateComponent> myStateComponents;
  private final @NotNull RenderTask myRenderTask;

  private boolean myIsBackgroundStateList;
  /** If not empty, it contains colors to compare with the state list items colors to find out any possible contrast problems,
   *  and descriptions to use in case there is a problem. */
  private @NotNull ImmutableMap<String, Color> myContrastColorsWithDescription = ImmutableMap.of();

  public StateListPicker(@Nullable ResourceHelper.StateList stateList,
                         @NotNull Module module,
                         @NotNull Configuration configuration) {

    myModule = module;
    myConfiguration = configuration;
    myRenderTask = DrawableRendererEditor.configureRenderTask(module, configuration);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    if (stateList != null) {
      setStateList(stateList);
    }
  }

  public void setStateList(@NotNull ResourceHelper.StateList stateList) {
    myStateList = stateList;
    myStateComponents = Lists.newArrayListWithCapacity(myStateList.getStates().size());
    removeAll();
    if (myStateList.getStates().isEmpty()) {
      add(new JLabel("Empty " + myStateList.getType() + " StateList"));
    }
    for (final ResourceHelper.StateListState state : myStateList.getStates()) {
      final StateComponent stateComponent = createStateComponent(state);
      add(stateComponent);
    }
    revalidate();
    repaint();
  }

  @NotNull
  private StateComponent createStateComponent(@NotNull ResourceHelper.StateListState state) {
    final StateComponent stateComponent = new StateComponent(myModule.getProject());
    myStateComponents.add(stateComponent);

    String stateValue = state.getValue();
    String alphaValue = state.getAlpha();

    stateComponent.addValueActionListener(new ValueActionListener(state, stateComponent));
    stateComponent.addAlphaActionListener(new AlphaActionListener(state, stateComponent));

    stateComponent.setValueText(stateValue);
    stateComponent.setAlphaValue(alphaValue);

    stateComponent.setAlphaVisible(!StringUtil.isEmpty(alphaValue));

    stateComponent.setNameText(state.getDescription());
    stateComponent.setComponentPopupMenu(createAlphaPopupMenu(state, stateComponent));

    return stateComponent;
  }

  @NotNull
  private JBPopupMenu createAlphaPopupMenu(@NotNull final ResourceHelper.StateListState state,
                                           @NotNull final StateComponent stateComponent) {
    JBPopupMenu popupMenu = new JBPopupMenu();
    final JMenuItem deleteAlpha = new JMenuItem("Delete alpha");
    popupMenu.add(deleteAlpha);
    deleteAlpha.setVisible(!StringUtil.isEmpty(state.getAlpha()));

    final JMenuItem createAlpha = new JMenuItem("Create alpha");
    popupMenu.add(createAlpha);
    createAlpha.setVisible(StringUtil.isEmpty(state.getAlpha()));

    deleteAlpha.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stateComponent.getAlphaComponent().setVisible(false);
        stateComponent.setAlphaValue(null);
        state.setAlpha(null);
        updateIcon(stateComponent);
        deleteAlpha.setVisible(false);
        createAlpha.setVisible(true);
      }
    });

    createAlpha.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AlphaActionListener listener = stateComponent.getAlphaActionListener();
        if (listener == null) {
          return;
        }
        listener.actionPerformed(new ActionEvent(stateComponent.getAlphaComponent(), ActionEvent.ACTION_PERFORMED, null));
        if (!StringUtil.isEmpty(state.getAlpha())) {
          stateComponent.getAlphaComponent().setVisible(true);
          createAlpha.setVisible(false);
          deleteAlpha.setVisible(true);
        }
      }
    });

    return popupMenu;
  }

  /**
   * Returns a {@Link ValidationInfo} in the case one of the state list state has a value that does not resolve to a valid resource,
   * or a value that is a private framework value.
   */
  @Nullable("if there is no error")
  public ValidationInfo getFrameworkResourceError() {
    IAndroidTarget target = myConfiguration.getTarget();
    assert target != null;
    final AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myModule);
    assert androidTargetData != null;
    ResourceRepository frameworkResources = myConfiguration.getFrameworkResources();
    assert frameworkResources != null;

    ValidationInfo error = null;
    for (StateComponent component : myStateComponents) {
      String resourceValue = component.getResourceValue();
      String errorText = null;
      if (component.getResourceComponent().hasWarningIcon()) {
        errorText = NON_EXISTENT_ERROR_PATTERN;
      }
      else if (isResourcePrivate(resourceValue, androidTargetData)) {
        errorText = PRIVATE_ERROR_PATTERN;
      }
      if (errorText != null) {
        error = component.getResourceComponent().createSwatchValidationInfo(String.format(errorText, resourceValue));
        break;
      }

      resourceValue = component.getAlphaValue();
      if (resourceValue == null) {
        continue;
      }
      if (component.getAlphaComponent().hasWarningIcon()) {
        errorText = NON_EXISTENT_ERROR_PATTERN;
      }
      else if (isResourcePrivate(resourceValue, androidTargetData)) {
        errorText = PRIVATE_ERROR_PATTERN;
      }
      if (errorText != null) {
        error = new ValidationInfo(String.format(errorText, resourceValue), component.getAlphaComponent());
        break;
      }
    }
    return error;
  }

  /**
   * Returns a {@Link ValidationInfo} specifying which of the state list component requires an API level higher than minApi.
   */
  @Nullable("if there is no such component")
  public ValidationInfo getApiError(int minApi) {
    for (StateComponent component : myStateComponents) {
      int resourceApi = ResolutionUtils.getOriginalApiLevel(component.getResourceValue(), myModule.getProject());
      if (resourceApi > minApi) {
        return component.getResourceComponent().createSwatchValidationInfo(String.format(API_ERROR_TEXT, resourceApi));
      }
      int alphaApi = ResolutionUtils.getOriginalApiLevel(component.getAlphaValue(), myModule.getProject());
      if (alphaApi > minApi) {
        return new ValidationInfo(String.format(API_ERROR_TEXT, alphaApi), component.getAlphaComponent());
      }
    }
    return null;
  }

  private static boolean isResourcePrivate(@NotNull String resourceValue, @NotNull AndroidTargetData targetData) {
    ResourceUrl url = ResourceUrl.parse(resourceValue);
    return url != null && url.framework && !targetData.isResourcePublic(url.type.getName(), url.name);
  }

  public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription, boolean isBackgroundStateList) {
    myContrastColorsWithDescription = contrastColorsWithDescription;
    myIsBackgroundStateList = isBackgroundStateList;
  }

  @Nullable
  public ResourceHelper.StateList getStateList() {
    return myStateList;
  }

  class ValueActionListener implements ActionListener, DocumentListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public ValueActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      assert facet != null;
      assert myStateList != null;
      List<String> completionStrings = ResourceHelper.getCompletionFromTypes(facet, myStateList.getFolderType() == ResourceFolderType.COLOR
                                                                                    ? GraphicalResourceRendererEditor.COLORS_ONLY
                                                                                    : GraphicalResourceRendererEditor.DRAWABLES_ONLY);
      myComponent.getResourceComponent().setCompletionStrings(completionStrings);
    }

    /**
     * @see AlphaActionListener#documentChanged(DocumentEvent)
     */
    @Override
    public void documentChanged(DocumentEvent e) {
      myState.setValue(myComponent.getResourceValue());
      // This is run inside a WriteAction and updateIcon may need an APP_RESOURCES_LOCK from AndroidFacet.
      // To prevent a potential deadlock, we call updateIcon in another thread.
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          updateIcon(myComponent);
          myComponent.repaint();
        }
      });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ResourceComponent resourceComponent = myComponent.getResourceComponent();

      final String attributeValue = resourceComponent.getValueText();
      ResourceUrl attributeValueUrl = ResourceUrl.parse(attributeValue);
      boolean isFrameworkValue = attributeValueUrl != null && attributeValueUrl.framework;
      String nameSuggestion = attributeValueUrl != null ? attributeValueUrl.name : attributeValue;

      ResourceType[] allowedTypes;
      assert myStateList != null;
      if (myStateList.getFolderType() == ResourceFolderType.COLOR) {
        allowedTypes = GraphicalResourceRendererEditor.COLORS_ONLY;
      }
      else {
        allowedTypes = GraphicalResourceRendererEditor.DRAWABLES_ONLY;
      }

      ChooseResourceDialog.ResourceNameVisibility resourceNameVisibility = ChooseResourceDialog.ResourceNameVisibility.FORCE;
      if (nameSuggestion.startsWith("#")) {
        nameSuggestion = null;
        resourceNameVisibility = ChooseResourceDialog.ResourceNameVisibility.SHOW;
      }

      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myModule, allowedTypes, attributeValue, isFrameworkValue, resourceNameVisibility, nameSuggestion);

      if (!myContrastColorsWithDescription.isEmpty()) {
        dialog
          .setContrastParameters(myContrastColorsWithDescription, myIsBackgroundStateList, !myStateList.getDisabledStates().contains(myState));
      }

      dialog.show();

      if (dialog.isOK()) {
        String resourceName = dialog.getResourceName();
        myState.setValue(resourceName);
        myComponent.setValueText(resourceName);

        // If a resource was overridden, it may affect several states of the state list.
        // Thus we need to repaint all components.
        repaintAllComponents();
      }
    }
  }

  private class AlphaActionListener implements ActionListener, DocumentListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public AlphaActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      assert facet != null;
      myComponent.getAlphaComponent().setCompletionStrings(ResourceHelper.getCompletionFromTypes(facet, DIMENSIONS_ONLY));
    }

    /**
     * @see ValueActionListener#documentChanged(DocumentEvent)
     */
    @Override
    public void documentChanged(DocumentEvent e) {
      myState.setAlpha(myComponent.getAlphaValue());
      // This is run inside a WriteAction and updateIcon may need an APP_RESOURCES_LOCK from AndroidFacet.
      // To prevent a potential deadlock, we call updateIcon in another thread.
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          updateIcon(myComponent);
          myComponent.repaint();
        }
      });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      SwatchComponent source = myComponent.getAlphaComponent();
      String itemValue = source.getText();

      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;

      ResourceValue resValue = resourceResolver.findResValue(itemValue, false);
      String resolvedResource = resValue != null ? resourceResolver.resolveResValue(resValue).getName() : itemValue;

      final ChooseResourceDialog dialog = new ChooseResourceDialog(myModule, DIMENSIONS_ONLY, resolvedResource, null);

      dialog.show();

      if (dialog.isOK()) {
        String resourceName = dialog.getResourceName();
        myState.setAlpha(resourceName);
        myComponent.setAlphaValue(resourceName);

        // If a resource was overridden, it may affect several states of the state list.
        // Thus we need to repaint all components.
        repaintAllComponents();
      }
    }
  }

  private void updateIcon(@NotNull StateComponent component) {
    component.showAlphaError(false);

    ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
    assert resourceResolver != null;

    String resourceName = component.getResourceValue();

    ResourceValue resValue = resourceResolver.findResValue(resourceName, false);
    resValue = resourceResolver.resolveResValue(resValue);

    if (resValue != null && resValue.getResourceType() != ResourceType.COLOR) {
      List<BufferedImage> images = myRenderTask.renderDrawableAllStates(resValue);
      if (images.isEmpty()) {
        component.setValueIcon(SwatchComponent.WARNING_ICON);
      }
      else {
        component.setValueIcon(new SwatchComponent.SquareImageIcon(Iterables.getLast(images)));
      }
      component.showStack(images.size() > 1);
    }
    else {
      final List<Color> colors = ResourceHelper.resolveMultipleColors(resourceResolver, resValue, myModule.getProject());
      if (colors.isEmpty()) {
        Color colorValue = ResourceHelper.parseColor(resourceName);
        if (colorValue != null) {
          component.setValueIcon(new SwatchComponent.ColorIcon(colorValue));
        }
        else {
          component.setValueIcon(SwatchComponent.WARNING_ICON);
        }
      }
      else {
        component.setValueIcon(new SwatchComponent.ColorIcon(Iterables.getLast(colors)));
      }
      component.showStack(colors.size() > 1);

      String alphaValue = component.getAlphaValue();

      if (!StringUtil.isEmpty(alphaValue)) {
        try {
          float alpha = Float.parseFloat(ResourceHelper.resolveStringValue(resourceResolver, alphaValue));
          Font iconFont = JBUI.Fonts.smallFont().asBold();
          component.getAlphaComponent().setSwatchIcon(new SwatchComponent.TextIcon(String.format("%.2f", alpha), iconFont));
        }
        catch (NumberFormatException e) {
          component.showAlphaError(true);
          component.getAlphaComponent().setSwatchIcon(SwatchComponent.WARNING_ICON);
        }
      }
      else {
        Font iconFont = JBUI.Fonts.smallFont().asBold();
        component.getAlphaComponent().setSwatchIcon(new SwatchComponent.TextIcon("1.00", iconFont));
      }
    }
  }

  private void repaintAllComponents() {
    for (StateComponent component : myStateComponents) {
      updateIcon(component);
      component.repaint();
    }
  }

  private class StateComponent extends Box {
    private final ResourceComponent myResourceComponent;
    private final SwatchComponent myAlphaComponent;
    private final JBLabel myAlphaErrorLabel;
    private AlphaActionListener myAlphaActionListener;

    public StateComponent(@NotNull Project project) {
      super(BoxLayout.PAGE_AXIS);

      myResourceComponent = new ResourceComponent(project, true);
      add(myResourceComponent);
      myResourceComponent
        .setMaximumSize(new Dimension(myResourceComponent.getMaximumSize().width, myResourceComponent.getPreferredSize().height));
      myResourceComponent.setVariantComboVisible(false);

      myAlphaComponent = new SwatchComponent(project, true);
      add(myAlphaComponent);

      Font font = StateListPicker.this.getFont();
      setFont(ThemeEditorUtils.scaleFontForAttribute(font));
      myAlphaComponent.setMaximumSize(new Dimension(myAlphaComponent.getMaximumSize().width, myAlphaComponent.getPreferredSize().height));

      Box alphaErrorComponent = new Box(BoxLayout.LINE_AXIS);
      myAlphaErrorLabel =
        new JBLabel("This value does not resolve to a floating-point number.", AllIcons.General.BalloonWarning, SwingConstants.LEADING);
      myAlphaErrorLabel.setVisible(false);
      alphaErrorComponent.add(myAlphaErrorLabel);
      alphaErrorComponent.add(Box.createHorizontalGlue());
      add(alphaErrorComponent);
    }

    @NotNull
    public ResourceComponent getResourceComponent() {
      return myResourceComponent;
    }

    @NotNull
    public SwatchComponent getAlphaComponent() {
      return myAlphaComponent;
    }

    public void showAlphaError(boolean hasError) {
      myAlphaErrorLabel.setVisible(hasError);
      myAlphaComponent.setWarningBorder(hasError);
    }

    public void setNameText(@NotNull String name) {
      myResourceComponent.setNameText(name);
    }

    public void setValueText(@NotNull String value) {
      myResourceComponent.setValueText(value);
    }

    public void setAlphaValue(@Nullable String alphaValue) {
      myAlphaComponent.setText(Strings.nullToEmpty(alphaValue));
    }

    public void setAlphaVisible(boolean isVisible) {
      myAlphaComponent.setVisible(isVisible);
    }

    public void setValueIcon(@NotNull SwatchComponent.SwatchIcon icon) {
      myResourceComponent.setSwatchIcon(icon);
    }

    public void showStack(boolean show) {
      myResourceComponent.showStack(show);
    }

    @NotNull
    public String getResourceValue() {
      return myResourceComponent.getValueText();
    }

    @Nullable
    public String getAlphaValue() {
      return myAlphaComponent.getText();
    }

    public void addValueActionListener(@NotNull ValueActionListener listener) {
      myResourceComponent.addSwatchListener(listener);
      myResourceComponent.addTextDocumentListener(listener);
    }

    public void addAlphaActionListener(@NotNull AlphaActionListener listener) {
      myAlphaComponent.addSwatchListener(listener);
      myAlphaComponent.addTextDocumentListener(listener);
      myAlphaActionListener = listener;
    }

    @Nullable
    public AlphaActionListener getAlphaActionListener() {
      return myAlphaActionListener;
    }

    @Override
    public void setComponentPopupMenu(JPopupMenu popup) {
      super.setComponentPopupMenu(popup);
      myResourceComponent.setComponentPopupMenu(popup);
      myAlphaComponent.setComponentPopupMenu(popup);
    }

    @Override
    public void setFont(Font font) {
      super.setFont(font);
      if (myResourceComponent != null) {
        myResourceComponent.setFont(font);
      }
      if (myAlphaComponent != null) {
        myAlphaComponent.setFont(font);
      }
    }
  }
}
