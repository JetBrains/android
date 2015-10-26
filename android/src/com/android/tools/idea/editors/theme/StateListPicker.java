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

import com.android.SdkConstants;
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
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.color.ColorSelector;
import org.jetbrains.android.dom.drawable.DrawableSelector;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

public class StateListPicker extends JPanel {
  private static final String API_ERROR_TEXT = "This resource requires at least an API level of %d";
  private static final ResourceType[] DIMENSIONS_ONLY = {ResourceType.DIMEN};

  public static final String PRIVATE_ERROR_PATTERN = "%s is a private framework resource";
  public static final String NON_EXISTENT_ERROR_PATTERN = "The resource %s does not exist";

  private final Module myModule;
  private final Configuration myConfiguration;
  private final ResourceHelper.StateList myStateList;
  private final List<StateComponent> myStateComponents;
  private @Nullable final RenderTask myRenderTask;

  private boolean myIsBackgroundStateList;
  /** If not null, it contains colors to compare with the state list items colors to find out any possible contrast problems,
   *  and descriptions to use in case there is a problem. */
  private @NotNull ImmutableMap<String, Color> myContrastColorsWithDescription = ImmutableMap.of();

  public StateListPicker(@NotNull ResourceHelper.StateList stateList,
                         @NotNull Module module,
                         @NotNull Configuration configuration) {
    myStateList = stateList;
    myModule = module;
    myConfiguration = configuration;
    myStateComponents = Lists.newArrayListWithCapacity(stateList.getStates().size());
    myRenderTask = DrawableRendererEditor.configureRenderTask(module, configuration);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    for (final ResourceHelper.StateListState state : myStateList.getStates()) {
      final StateComponent stateComponent = createStateComponent(state);
      stateComponent.addValueActionListener(new ValueActionListener(state, stateComponent));
      stateComponent.addAlphaActionListener(new AlphaActionListener(state, stateComponent));
      add(stateComponent);
    }
  }

  @NotNull
  private StateComponent createStateComponent(@NotNull ResourceHelper.StateListState state) {
    final StateComponent stateComponent = new StateComponent();
    myStateComponents.add(stateComponent);

    String stateValue = state.getValue();
    String alphaValue = state.getAlpha();
    updateComponent(stateComponent, stateValue, alphaValue);

    stateComponent.setAlphaVisible(!StringUtil.isEmpty(alphaValue));

    String stateDescription = Joiner.on(", ").join(state.getAttributesNames(true));
    stateComponent.setNameText(stateDescription);
    stateComponent.setComponentPopupMenu(createAlphaPopupMenu(state, stateComponent));

    return stateComponent;
  }

  @NotNull
  private JBPopupMenu createAlphaPopupMenu(@NotNull final ResourceHelper.StateListState state,
                                           @NotNull final StateComponent stateComponent) {
    JBPopupMenu popupMenu = new JBPopupMenu();
    final JMenuItem deleteAlpha = new JMenuItem("Delete alpha");
    popupMenu.add(deleteAlpha);
    deleteAlpha.setVisible(state.getAlpha() != null);

    final JMenuItem createAlpha = new JMenuItem("Create alpha");
    popupMenu.add(createAlpha);
    createAlpha.setVisible(state.getAlpha() == null);

    deleteAlpha.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stateComponent.getAlphaComponent().setVisible(false);
        state.setAlpha(null);
        updateComponent(stateComponent, state.getValue(), state.getAlpha());
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
        if (state.getAlpha() != null) {
          stateComponent.getAlphaComponent().setVisible(true);
          createAlpha.setVisible(false);
          deleteAlpha.setVisible(true);
        }
      }
    });

    return popupMenu;
  }

  public void updateStateList(@NotNull List<VirtualFile> files) {
    Project project = myModule.getProject();
    if (!AndroidResourceUtil.ensureFilesWritable(project, files)) {
      return;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithCapacity(files.size());
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : files) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }

    final List<AndroidDomElement> selectors = Lists.newArrayListWithCapacity(files.size());

    Class<? extends AndroidDomElement> selectorClass;
    if (myStateList.getType() == ResourceFolderType.COLOR) {
      selectorClass = ColorSelector.class;
    }
    else {
      selectorClass = DrawableSelector.class;
    }
    for (VirtualFile file : files) {
      final AndroidDomElement selector = AndroidUtils.loadDomElement(myModule, file, selectorClass);
      if (selector == null) {
        AndroidUtils.reportError(project, file.getName() + " is not a statelist file");
        return;
      }
      selectors.add(selector);
    }

    new WriteCommandAction.Simple(project, "Change State List", psiFiles.toArray(new PsiFile[psiFiles.size()])) {
      @Override
      protected void run() {
        for (AndroidDomElement selector : selectors) {
          XmlTag tag = selector.getXmlTag();
          for (XmlTag subtag : tag.getSubTags()) {
            subtag.delete();
          }
          for (ResourceHelper.StateListState state : myStateList.getStates()) {
            XmlTag child = tag.createChildTag(SdkConstants.TAG_ITEM, tag.getNamespace(), null, false);
            child = tag.addSubTag(child, false);

            Map<String, Boolean> attributes = state.getAttributes();
            for (String attributeName : attributes.keySet()) {
              child.setAttribute(attributeName, SdkConstants.ANDROID_URI, attributes.get(attributeName).toString());
            }

            if (!StringUtil.isEmpty(state.getAlpha())) {
              child.setAttribute("alpha", SdkConstants.ANDROID_URI, state.getAlpha());
            }

            if (selector instanceof ColorSelector) {
              child.setAttribute(SdkConstants.ATTR_COLOR, SdkConstants.ANDROID_URI, state.getValue());
            }
            else if (selector instanceof DrawableSelector) {
              child.setAttribute(SdkConstants.ATTR_DRAWABLE, SdkConstants.ANDROID_URI, state.getValue());
            }
          }
        }

        // The following is necessary since layoutlib will look on disk for the color state list file.
        // So as soon as a color state list is modified, the change needs to be saved on disk
        // for the correct values to be used in the theme editor preview.
        // TODO: Remove this once layoutlib can get color state lists from PSI instead of disk
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    }.execute();
  }

  /**
   * Returns a {@Link ValidationInfo} specifying which of the state list component has a value which is a framework value,
   * but either does not exist, or is private;
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

  class ValueActionListener extends DocumentAdapter implements ActionListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public ValueActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    @Override
    protected void textChanged(DocumentEvent e) {
      myState.setValue(myComponent.getResourceValue());
      updateComponent(myComponent, myComponent.getResourceValue(), myComponent.getAlphaValue());
      myComponent.repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ResourceComponent resourceComponent = myComponent.getResourceComponent();

      final String attributeValue = resourceComponent.getValueText();
      ResourceUrl attributeValueUrl = ResourceUrl.parse(attributeValue);
      boolean isFrameworkValue = attributeValueUrl != null && attributeValueUrl.framework;
      String nameSuggestion = attributeValueUrl != null ? attributeValueUrl.name : attributeValue;

      ResourceType[] allowedTypes;
      if (myStateList.getType() == ResourceFolderType.COLOR) {
        allowedTypes = GraphicalResourceRendererEditor.COLORS_ONLY;
      }
      else {
        allowedTypes = GraphicalResourceRendererEditor.DRAWABLES_ONLY;
      }

      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myModule, myConfiguration, allowedTypes, attributeValue, isFrameworkValue,
                                 ChooseResourceDialog.ResourceNameVisibility.FORCE, nameSuggestion);

      if (!myContrastColorsWithDescription.isEmpty()) {
        dialog
          .setContrastParameters(myContrastColorsWithDescription, myIsBackgroundStateList, !myStateList.getDisabledStates().contains(myState));
      }

      dialog.show();

      if (dialog.isOK()) {
        myState.setValue(dialog.getResourceName());
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        if (facet != null) {
          facet.refreshResources();
        }
        updateComponent(myComponent, myState.getValue(), myState.getAlpha());

        // If a resource was overridden, it may affect several states of the state list.
        // Thus we need to repaint all components.
        repaintAllComponents();
      }
    }
  }

  private class AlphaActionListener extends DocumentAdapter implements ActionListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public AlphaActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    @Override
    protected void textChanged(DocumentEvent e) {
      myState.setAlpha(myComponent.getAlphaValue());
      updateComponent(myComponent, myComponent.getResourceValue(), myComponent.getAlphaValue());
      myComponent.repaint();
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
        myState.setAlpha(dialog.getResourceName());
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        assert facet != null;
        facet.refreshResources();
        updateComponent(myComponent, myState.getValue(), myState.getAlpha());

        // If a resource was overridden, it may affect several states of the state list.
        // Thus we need to repaint all components.
        repaintAllComponents();
      }
    }
  }

  private void updateComponent(@NotNull StateComponent component, @NotNull String resourceName, @Nullable String alphaValue) {
    if (!Objects.equal(resourceName, component.getResourceValue())) {
      component.setValueText(resourceName);
    }
    if (!Objects.equal(alphaValue, component.getAlphaValue())) {
      component.setAlphaValue(alphaValue);
    }
    component.showAlphaError(false);

    ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
    assert resourceResolver != null;

    ResourceValue resValue = resourceResolver.findResValue(resourceName, false);
    resValue = resourceResolver.resolveResValue(resValue);

    if (resValue != null && resValue.getResourceType() != ResourceType.COLOR && myRenderTask != null) {
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
      updateComponent(component, component.getResourceValue(), component.getAlphaValue());
      component.repaint();
    }
  }

  private class StateComponent extends Box {
    private final ResourceComponent myResourceComponent;
    private final SwatchComponent myAlphaComponent;
    private final JBLabel myAlphaErrorLabel;
    private AlphaActionListener myAlphaActionListener;

    public StateComponent() {
      super(BoxLayout.PAGE_AXIS);

      myResourceComponent = new ResourceComponent();
      add(myResourceComponent);
      myResourceComponent
        .setMaximumSize(new Dimension(myResourceComponent.getMaximumSize().width, myResourceComponent.getPreferredSize().height));
      myResourceComponent.setVariantComboVisible(false);

      myAlphaComponent = new SwatchComponent();
      myAlphaComponent.setBackground(JBColor.WHITE);
      myAlphaComponent.setForeground(null);
      add(myAlphaComponent);

      Font font = StateListPicker.this.getFont();
      setFont(font.deriveFont(font.getSize() * ThemeEditorConstants.ATTRIBUTES_FONT_SCALE));
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
