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
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
import com.intellij.ui.ColorUtil;
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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StateListPicker extends JPanel {
  private static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b>";
  private static final String API_ERROR_TEXT = "This resource requires at least an API level of %d";
  private static final ResourceType[] DIMENSIONS_ONLY = {ResourceType.DIMEN};
  private static final Icon QUESTION_ICON = AllIcons.Actions.Help;
  private static final SwatchComponent.SwatchIcon WARNING_ICON = new SwatchComponent.SwatchIcon() {
    @Override
    public void paint(@Nullable Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      int horizontalMargin = (w + JBUI.scale(1) - QUESTION_ICON.getIconWidth()) / 2;
      int verticalMargin = (h + JBUI.scale(3) - QUESTION_ICON.getIconHeight()) / 2;
      QUESTION_ICON.paintIcon(c, g, x + horizontalMargin, y + verticalMargin);
    }
  };
  private static final ImmutableList<SwatchComponent.SwatchIcon> WARNING_ICON_LIST = ImmutableList.of(WARNING_ICON);

  private final Module myModule;
  private final Configuration myConfiguration;
  private final ResourceHelper.StateList myStateList;
  private final List<StateComponent> myStateComponents;
  private @Nullable final RenderTask myRenderTask;

  public StateListPicker(@NotNull ResourceHelper.StateList stateList, @NotNull Module module, @NotNull Configuration configuration) {
    myStateList = stateList;
    myModule = module;
    myConfiguration = configuration;
    myStateComponents = Lists.newArrayListWithCapacity(stateList.getStates().size());
    myRenderTask = DrawableRendererEditor.configureRenderTask(module, configuration);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    for (ResourceHelper.StateListState state : myStateList.getStates()) {
      StateComponent stateComponent = createStateComponent(state);
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
    updateComponent(stateComponent, stateValue, state.getAlpha());

    Map<String, Boolean> attributes = state.getAttributes();
    List<String> attributeDescriptions = new ArrayList<String>();

    for (Map.Entry<String, Boolean> attribute : attributes.entrySet()) {
      String description = attribute.getKey().substring(ResourceHelper.STATE_NAME_PREFIX.length());
      if (!attribute.getValue()) {
        description = "Not " + description;
      }
      attributeDescriptions.add(StringUtil.capitalize(description));
    }

    String stateDescription = attributeDescriptions.size() == 0 ? "Default" : Joiner.on(", ").join(attributeDescriptions);
    stateComponent.setNameText(String.format(LABEL_TEMPLATE, ColorUtil.toHex(ThemeEditorConstants.RESOURCE_ITEM_COLOR), stateDescription));

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

            if (state.getAlpha() != null) {
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
   * Returns a {@Link ValidationInfo} specifying which of the state list component has a value which is a private resource.
   * If there is no such component, returns null.
   */
  @Nullable
  public ValidationInfo getPrivateResourceError() {
    IAndroidTarget target = myConfiguration.getTarget();
    assert target != null;
    final AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myModule);
    assert androidTargetData != null;

    ValidationInfo error = null;
    String errorText = "%s is a private Android resource";
    String resourceValue;

    for (StateComponent component : myStateComponents) {
      resourceValue = component.getResourceValue();
      if (isResourcePrivate(resourceValue, androidTargetData)) {
        error = component.getResourceComponent().createSwatchValidationInfo(String.format(errorText, resourceValue));
        break;
      }
      else {
        resourceValue = component.getAlphaValue();
        if (isResourcePrivate(resourceValue, androidTargetData)) {
          error = new ValidationInfo(String.format(errorText, resourceValue), component.getAlphaComponent());
          break;
        }
      }
    }

    return error;
  }

  /**
   * Returns a {@Link ValidationInfo} specifying which of the state list component requires an API level higher than minApi.
   * If there is no such component, returns null.
   */
  @Nullable
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

  class ValueActionListener implements ActionListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public ValueActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ResourceComponent resourceComponent = myComponent.getResourceComponent();

      String itemValue = resourceComponent.getValueText();
      final String resourceName;
      // If it points to an existing resource.
      if (!RenderResources.REFERENCE_EMPTY.equals(itemValue) &&
          !RenderResources.REFERENCE_NULL.equals(itemValue) &&
          itemValue.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        // Use the name of that resource.
        resourceName = itemValue.substring(itemValue.indexOf('/') + 1);
      }
      else {
        // Otherwise use the name of the attribute.
        resourceName = itemValue;
      }

      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;
      String resolvedResource = itemValue;

      ResourceValue resValue = resourceResolver.findResValue(itemValue, false);
      if (resValue != null) {
        if (resValue.getResourceType() == ResourceType.COLOR) {
          resolvedResource = ResourceHelper.colorToString(ResourceHelper.resolveColor(resourceResolver, resValue, myModule.getProject()));
        }
        else {
          resolvedResource = resourceResolver.resolveResValue(resValue).getName();
        }
      }

      ResourceType[] allowedTypes;
      if (myStateList.getType() == ResourceFolderType.COLOR) {
        allowedTypes = GraphicalResourceRendererEditor.COLORS_ONLY;
      }
      else {
        allowedTypes = GraphicalResourceRendererEditor.DRAWABLES_ONLY;
      }

      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myModule, allowedTypes, resolvedResource, null, ChooseResourceDialog.ResourceNameVisibility.FORCE,
                                 resourceName);

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

  private class AlphaActionListener implements ActionListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public AlphaActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
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
    component.setValueText(resourceName);
    component.setAlphaValue(alphaValue);
    component.setAlphaVisible(!StringUtil.isEmpty(alphaValue));
    component.showAlphaError(false);

    ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
    assert resourceResolver != null;

    ResourceValue resValue = resourceResolver.findResValue(resourceName, false);
    String value = resValue != null ? resourceResolver.resolveResValue(resValue).getValue() : resourceName;

    if (resValue != null && resValue.getResourceType() != ResourceType.COLOR && myRenderTask != null) {
      component.setValueIcons(SwatchComponent.imageListOf(myRenderTask.renderDrawableAllStates(resValue)));
    }
    else {
      Color color = ResourceHelper.parseColor(value);
      assert color != null;
      List<Color> colorList = ImmutableList.of(color);
      component.setValueIcons(SwatchComponent.colorListOf(colorList));

      if (!StringUtil.isEmpty(alphaValue)) {
        try {
          float alpha = Float.parseFloat(ResourceHelper.resolveStringValue(resourceResolver, alphaValue));
          Font iconFont = JBUI.Fonts.smallFont().asBold();
          List<SwatchComponent.TextIcon> list = ImmutableList.of(new SwatchComponent.TextIcon(String.format("%.2f", alpha), iconFont));
          component.getAlphaComponent().setSwatchIcons(list);
        }
        catch (NumberFormatException e) {
          component.showAlphaError(true);
          component.getAlphaComponent().setSwatchIcons(WARNING_ICON_LIST);
        }
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
      setFont(StateListPicker.this.getFont());

      myResourceComponent = new ResourceComponent();
      add(myResourceComponent);
      myResourceComponent
        .setMaximumSize(new Dimension(myResourceComponent.getMaximumSize().width, myResourceComponent.getPreferredSize().height));
      myResourceComponent.setVariantComboVisible(false);

      myAlphaComponent = new SwatchComponent((short)1);
      myAlphaComponent.setBackground(JBColor.WHITE);
      myAlphaComponent.setForeground(null);
      add(myAlphaComponent);
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

    public void setValueIcons(List<SwatchComponent.SwatchIcon> icons) {
      myResourceComponent.setSwatchIcons(icons);
    }

    @NotNull
    public String getResourceValue() {
      return myResourceComponent.getValueText();
    }

    @NotNull
    public String getAlphaValue() {
      return myAlphaComponent.getText();
    }

    public void addValueActionListener(@NotNull ValueActionListener listener) {
      myResourceComponent.addActionListener(listener);
    }

    public void addAlphaActionListener(@NotNull AlphaActionListener listener) {
      myAlphaComponent.addActionListener(listener);
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
  }
}
