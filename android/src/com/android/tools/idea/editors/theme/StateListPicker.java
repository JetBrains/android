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
import com.android.tools.idea.editors.theme.attributes.editors.ColorRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
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

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StateListPicker extends JPanel {
  private static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b>";
  private static final ResourceType[] DIMENSIONS_ONLY = {ResourceType.DIMEN};

  private final Module myModule;
  private final Configuration myConfiguration;
  private final StateList myStateList;
  private final List<StateComponent> myStateComponents;
  private @Nullable final RenderTask myRenderTask;

  public StateListPicker(@NotNull StateList stateList, @NotNull Module module, @NotNull Configuration configuration) {
    myStateList = stateList;
    myModule = module;
    myConfiguration = configuration;
    myStateComponents = Lists.newArrayListWithCapacity(stateList.getStates().size());
    myRenderTask = DrawableRendererEditor.configureRenderTask(module, configuration);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    for (StateListState state : myStateList.getStates()) {
      StateComponent stateComponent = createStateComponent(state);
      stateComponent.addValueActionListener(new ValueActionListener(state, stateComponent));
      stateComponent.addAlphaActionListener(new AlphaActionListener(state, stateComponent));
      add(stateComponent);
    }
  }

  @NotNull
  private StateComponent createStateComponent(@NotNull final StateListState state) {
    final StateComponent stateComponent = new StateComponent();
    myStateComponents.add(stateComponent);

    String stateValue = state.getValue();
    updateComponent(stateComponent, stateValue, state.getAlpha());

    Map<String, Boolean> attributes = state.getAttributes();
    List<String> attributeDescriptions = new ArrayList<String>();
    for(Map.Entry<String, Boolean> attribute : attributes.entrySet()) {
      String description = attribute.getKey().substring(ResourceHelper.STATE_NAME_PREFIX.length());
      if (!attribute.getValue()) {
        description = "Not " + description;
      }
      attributeDescriptions.add(StringUtil.capitalize(description));
    }
    String stateDescription = attributeDescriptions.size() == 0 ? "Default" : Joiner.on(", ").join(attributeDescriptions);
    stateComponent.setNameText(String.format(LABEL_TEMPLATE, ThemeEditorConstants.RESOURCE_ITEM_COLOR.toString(), stateDescription));

    stateComponent.setComponentPopupMenu(createAlphaPopupMenu(state, stateComponent));

    return stateComponent;
  }

  @NotNull
  private JBPopupMenu createAlphaPopupMenu(@NotNull final StateListState state, @NotNull final StateComponent stateComponent) {
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

  @NotNull
  private String resolveResource(@NotNull String resourceValue, boolean isFrameworkValue) {
    ResourceUrl resourceUrl = ResourceUrl.parse(resourceValue);
    if (resourceUrl == null) {
      return resourceValue;
    }
    ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
    assert resourceResolver != null;
    ResourceValue resValue = resourceResolver.findResValue(resourceValue, isFrameworkValue || resourceUrl.framework);
    if (resValue == null) {
      return resourceValue;
    }
    ResourceValue finalValue = resourceResolver.resolveResValue(resValue);
    if (finalValue == null || finalValue.getValue() == null) {
      return resourceValue;
    }
    return finalValue.getValue();
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
          for (StateListState state : myStateList.getStates()) {
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

  private static boolean isResourcePrivate(@NotNull String resourceValue, @NotNull AndroidTargetData targetData) {
    ResourceUrl url = ResourceUrl.parse(resourceValue);
    return url != null && url.framework && !targetData.isResourcePublic(url.type.getName(), url.name);
  }

  class ValueActionListener implements ActionListener {
    private final StateListState myState;
    private final StateComponent myComponent;

    public ValueActionListener(StateListState state, StateComponent stateComponent) {
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
      String resolvedResource;
      ResourceUrl url = ResourceUrl.parse(itemValue);
      if (url != null) {
        ResourceValue resValue = resourceResolver.findResValue(itemValue, url.framework);
        if (resValue.getResourceType() == ResourceType.COLOR) {
          resolvedResource = ResourceHelper.colorToString(ResourceHelper.resolveColor(resourceResolver, resValue, myModule.getProject()));
        }
        else {
          resolvedResource = resourceResolver.resolveResValue(resValue).getName();
        }
      }
      else {
        resolvedResource = itemValue;
      }

      ResourceType[] allowedTypes;
      if (myStateList.getType() == ResourceFolderType.COLOR) {
        allowedTypes = ColorRendererEditor.COLORS_ONLY;
      }
      else {
        allowedTypes = ColorRendererEditor.DRAWABLES_ONLY;
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
        myComponent.repaint();
      }
    }
  }

  private class AlphaActionListener implements ActionListener {
    private final StateListState myState;
    private final StateComponent myComponent;

    public AlphaActionListener(StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      SwatchComponent source = myComponent.getAlphaComponent();
      String itemValue = source.getText();

      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;
      String resolvedResource;
      ResourceUrl url = ResourceUrl.parse(itemValue);
      if (url != null) {
        ResourceValue resValue = resourceResolver.findResValue(itemValue, url.framework);
        resolvedResource = resourceResolver.resolveResValue(resValue).getName();
      }
      else {
        resolvedResource = itemValue;
      }

      final ChooseResourceDialog dialog = new ChooseResourceDialog(myModule, DIMENSIONS_ONLY, resolvedResource, null);

      dialog.show();

      if (dialog.isOK()) {
        myState.setAlpha(dialog.getResourceName());
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        assert facet != null;
        facet.refreshResources();
        updateComponent(myComponent, myState.getValue(), myState.getAlpha());
        myComponent.repaint();
      }
    }
  }

  private void updateComponent(@NotNull StateComponent component, @NotNull String resourceName, @Nullable String alphaValue) {
    component.setValueText(resourceName);
    component.setAlphaVisible(alphaValue != null);

    ResourceValue resValue = null;
    String value = resourceName;
    ResourceUrl url = ResourceUrl.parse(resourceName);
    if (url != null) {
      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;
      resValue = resourceResolver.findResValue(resourceName, url.framework);
      value = resourceResolver.resolveResValue(resValue).getValue();
    }
    if (resValue != null && resValue.getResourceType() != ResourceType.COLOR && myRenderTask != null) {
      component.setValueIcons(SwatchComponent.imageListOf(myRenderTask.renderDrawableAllStates(resValue)));
    }
    else {
      component.setSwatchColorWithAlpha(value, alphaValue);
    }
  }

  public static class StateList {
    private final ResourceFolderType myType;
    private final List<StateListState> myStates;

    public StateList(@NotNull ResourceFolderType type) {
      myType = type;
      myStates = new ArrayList<StateListState>();
    }

    @NotNull
    public ResourceFolderType getType() {
      return myType;
    }

    @NotNull
    public List<StateListState> getStates() {
      return myStates;
    }

    public void addState(@NotNull StateListState state) {
      myStates.add(state);
    }
  }

  public static class StateListState {
    private String myValue;
    private String myAlpha;
    private final Map<String, Boolean> myAttributes;

    public StateListState(@NotNull String value, @NotNull Map<String, Boolean> attributes, @Nullable String alpha) {
      myValue = value;
      myAttributes = attributes;
      myAlpha = alpha;
    }

    public void setValue(@NotNull String value) {
      myValue = value;
    }

    public void setAlpha(String alpha) {
      myAlpha = alpha;
    }

    @NotNull
    public String getValue() {
      return myValue;
    }

    @Nullable
    public String getAlpha() {
      return myAlpha;
    }

    @NotNull
    public Map<String, Boolean> getAttributes() {
      return myAttributes;
    }
  }

  private class StateComponent extends Box {
    private final ResourceComponent myResourceComponent;
    private final SwatchComponent myAlphaComponent;
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
    }

    @NotNull
    public ResourceComponent getResourceComponent() {
      return myResourceComponent;
    }

    @NotNull
    public SwatchComponent getAlphaComponent() {
      return myAlphaComponent;
    }

    public void setNameText(@NotNull String name) {
      myResourceComponent.setNameText(name);
    }

    public void setValueText(@NotNull String value) {
      myResourceComponent.setValueText(value);
    }

    public void setAlphaVisible(boolean isVisible) {
      myAlphaComponent.setVisible(isVisible);
    }

    public void setValueIcons(List<SwatchComponent.SwatchIcon> icons) {
      myResourceComponent.setSwatchIcons(icons);
    }

    public void setSwatchColorWithAlpha(@NotNull String colorValue, @Nullable String alphaValue) {
      float alpha = 1.0f;
      if (alphaValue != null) {
        myAlphaComponent.setText(alphaValue);
        try {
          alpha = Float.parseFloat(resolveResource(alphaValue, false));
        }
        catch (NumberFormatException e) {
          AndroidUtils.reportError(myModule.getProject(), "The value for alpha needs to be a floating point number");
        }
      }

      Color color = ResourceHelper.parseColor(colorValue);
      assert color != null;
      int combinedAlpha = (int)(color.getAlpha() * alpha);
      if (combinedAlpha < 0) {
        combinedAlpha = 0;
      }
      if (combinedAlpha > 255) {
        combinedAlpha = 255;
      }
      Color colorWithAlpha = ColorUtil.toAlpha(color, combinedAlpha);
      List<Color> colorList = ImmutableList.of(colorWithAlpha);
      myResourceComponent.setSwatchIcons(SwatchComponent.colorListOf(colorList));
      List<NumericalIcon> list = Collections.singletonList(new NumericalIcon(alpha, getFont()));
      myAlphaComponent.setSwatchIcons(list);
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

  private static class NumericalIcon implements SwatchComponent.SwatchIcon {
    private final Font myAlphaFont;
    private final String myString;
    private final Font myStateListFont;

    public NumericalIcon(float f, @NotNull Font stateListFont) {
      myString = String.format("%.2f", f);
      myStateListFont = stateListFont;
      myAlphaFont = new Font(myStateListFont.getName(), Font.BOLD, myStateListFont.getSize() - 2);
    }

    @Override
    public void paint(@Nullable Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      g.setColor(JBColor.LIGHT_GRAY);
      g.fillRect(x, y, w, h);

      g.setColor(JBColor.DARK_GRAY);
      g.setFont(myAlphaFont);

      FontMetrics fm = g.getFontMetrics();
      int horizontalMargin = (w + 1 - fm.stringWidth(myString)) / 2;
      int verticalMargin = (h + 3 - fm.getAscent()) / 2;
      g.drawString(myString, x + horizontalMargin, y + h - verticalMargin);

      g.setFont(myStateListFont);
    }
  }
}
