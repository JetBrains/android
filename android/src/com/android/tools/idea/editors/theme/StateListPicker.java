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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.attributes.editors.ColorRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.color.ColorSelector;
import org.jetbrains.android.dom.drawable.DrawableSelector;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StateListPicker extends JPanel {
  private static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b>";

  private final Module myModule;
  private final Configuration myConfiguration;
  private final StateList myStateList;
  private @Nullable final RenderTask myRenderTask;

  public StateListPicker(@NotNull StateList stateList, @NotNull Module module, @NotNull Configuration configuration) {
    myStateList = stateList;
    myModule = module;
    myConfiguration = configuration;
    myRenderTask = DrawableRendererEditor.configureRenderTask(module, configuration);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    for (StateListState state: myStateList.getStates()) {
      ResourceComponent stateComponent  = createStateComponent(state);
      stateComponent.addActionListener(new StateActionListener(state));
      add(stateComponent);
    }
  }

  @NotNull
  private ResourceComponent createStateComponent(@NotNull StateListState state) {
    ResourceComponent component = new ResourceComponent();
    component.setMaximumSize(new Dimension(component.getMaximumSize().width, component.getPreferredSize().height));
    component.setVariantComboVisible(false);

    String stateValue = state.getValue();
    updateComponent(component, stateValue);

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
    component.setNameText(String.format(LABEL_TEMPLATE, ThemeEditorConstants.RESOURCE_ITEM_COLOR.toString(), stateDescription));
    return component;
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

  class StateActionListener implements ActionListener {
    private final StateListState myState;

    public StateActionListener(StateListState state) {
      myState = state;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Component source = (Component)e.getSource();
      ResourceComponent component = (ResourceComponent)SwingUtilities.getAncestorOfClass(ResourceComponent.class, source);
      String itemValue = component.getValueText();
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
        new ChooseResourceDialog(myModule, allowedTypes, resolvedResource, null,
                                 ChooseResourceDialog.ResourceNameVisibility.FORCE, resourceName);

      dialog.show();

      if (dialog.isOK()) {
        myState.setValue(dialog.getResourceName());
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        if (facet != null) {
          facet.refreshResources();
        }
        updateComponent(component, dialog.getResourceName());
        component.repaint();
      }
    }
  }

  private void updateComponent(@NotNull ResourceComponent component, @NotNull String resourceName) {
    component.setValueText(resourceName);

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
      component.setSwatchIcons(SwatchComponent.imageListOf(myRenderTask.renderDrawableAllStates(resValue)));
    }
    else {
      List<Color> colorList = Collections.singletonList(ResourceHelper.parseColor(value));
      component.setSwatchIcons(SwatchComponent.colorListOf(colorList));
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
    private final Map<String, Boolean> myAttributes;

    public StateListState(@NotNull String value, @NotNull Map<String, Boolean> attributes) {
      myValue = value;
      myAttributes = attributes;
    }

    public void setValue(@NotNull String value) {
      myValue = value;
    }

    @NotNull
    public String getValue() {
      return myValue;
    }

    @NotNull
    public Map<String, Boolean> getAttributes() {
      return myAttributes;
    }
  }
}
