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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import com.google.common.base.Joiner;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StateListPicker extends JPanel {
  static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b>";

  private Module myModule;
  private Configuration myConfiguration;

  public StateListPicker(@NotNull List<StateListState> colorStates, @NotNull Module module, @NotNull Configuration configuration) {
    myModule = module;
    myConfiguration = configuration;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    for (StateListState state: colorStates) {
      ResourceComponent stateComponent  = createStateComponent(state);
      stateComponent.addActionListener(new StateActionListener(state));
      add(stateComponent);
    }
  }

  @NotNull
  private ResourceComponent createStateComponent(StateListState state) {
    ResourceComponent component = new ResourceComponent();
    component.setVariantComboVisible(false);

    String colorValue = state.getColor();
    component.setValueText(colorValue);
    ResourceUrl url = ResourceUrl.parse(colorValue);
    if (url != null) {
      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;
      ResourceValue resValue = resourceResolver.findResValue(colorValue, url.framework);
      ResourceValue color = resourceResolver.resolveResValue(resValue);
      colorValue = color.getValue();
    }
    List<Color> colorList = Collections.singletonList(ResourceHelper.parseColor(colorValue));
    component.setSwatchIcons(SwatchComponent.colorListOf(colorList));

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

  class StateActionListener implements ActionListener {
    private StateListState myState;

    public StateActionListener(StateListState state) {
      myState = state;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Component source = (Component)e.getSource();
      ResourceComponent component = (ResourceComponent)SwingUtilities.getAncestorOfClass(ResourceComponent.class, source);
      String itemValue = component.getValueText();
      final String colorName;
      // If it points to an existing resource.
      if (!RenderResources.REFERENCE_EMPTY.equals(itemValue) &&
          !RenderResources.REFERENCE_NULL.equals(itemValue) &&
          itemValue.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        // Use the name of that resource.
        colorName = itemValue.substring(itemValue.indexOf('/') + 1);
      }
      else {
        // Otherwise use the name of the attribute.
        colorName = itemValue;
      }

      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;
      String resolvedColor;
      ResourceUrl url = ResourceUrl.parse(itemValue);
      if (url != null) {
        ResourceValue resValue = resourceResolver.findResValue(itemValue, url.framework);
        resolvedColor = ResourceHelper.colorToString(ResourceHelper.resolveColor(resourceResolver, resValue));
      }
      else {
        resolvedColor = itemValue;
      }

      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myModule, ChooseResourceDialog.COLOR_TYPES, resolvedColor, null,
                                 ChooseResourceDialog.ResourceNameVisibility.FORCE, colorName);

      dialog.show();

      if (dialog.isOK()) {
        myState.setColor(dialog.getResourceName());
        updateComponent(component, dialog.getResourceName());
      }
    }

    private void updateComponent(@NotNull ResourceComponent component, @NotNull String resourceName) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null) {
        facet.refreshResources();
      }
      component.setValueText(resourceName);
      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;
      ResourceUrl url = ResourceUrl.parse(resourceName);
      if (url != null) {
        ResourceValue resValue = resourceResolver.findResValue(resourceName, url.framework);
        if (resValue != null) {
          List<Color> colorList = Collections.singletonList(ResourceHelper.parseColor(resourceResolver.resolveResValue(resValue).getValue()));
          component.setSwatchIcons(SwatchComponent.colorListOf(colorList));
        }
      }
      component.repaint();
    }
  }


  public static class StateListState {
    private String myColor;
    private Map<String, Boolean> myAttributes;

    public StateListState() {
      myAttributes = new HashMap<String, Boolean>();
    }

    public void setColor(String color) {
      myColor = color;
    }

    public void addAttribute(String name, boolean value) {
      myAttributes.put(name, value);
    }

    public String getColor() {
      return myColor;
    }

    public Map<String, Boolean> getAttributes() {
      return myAttributes;
    }
  }
}
