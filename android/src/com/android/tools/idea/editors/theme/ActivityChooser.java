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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.editor.SpecificActivityConfigurable;
import com.android.tools.idea.run.editor.SpecificActivityLaunch;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

/**
 * Lets you pick an Activity from the project.
 */
public class ActivityChooser extends DialogWrapper {

  private static final int DROPDOWN_LIMIT = 10;

  private final Module myModule;
  private final ComboBox<Element> myActivitySelector;
  private final JComponent myChooserComponent;
  private final SpecificActivityConfigurable myChooser;
  private final JPanel myPanel;

  protected ActivityChooser(@NotNull Module module) {
    super(module.getProject());
    myModule = module;
    myChooser = new SpecificActivityConfigurable(module.getProject(), () -> module);
    setTitle("Select Activity");

    // we can NOT use AndroidFacet.getManifest() as it does not take manifestPlaceholders into account, so we MUST use MergedManifest
    List<Element> activities = MergedManifest.get(module).getActivities();

    // show list of activities from merged manifest
    Element[] first10 = activities.stream().limit(DROPDOWN_LIMIT).toArray(Element[]::new);
    myActivitySelector = new ComboBox<Element>(new DefaultComboBoxModel<>(first10)) {
      @Override
      protected void selectedItemChanged() {
        super.selectedItemChanged();
        if (dataModel.getSelectedItem() == null) {
          // normally JComboBox does not fire events when null item is selected, but we still want an event.
          fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, null, ItemEvent.SELECTED));
        }
      }
    };
    // over 10, add a "Specify activity name..." link to specify name
    if (activities.size() > DROPDOWN_LIMIT) {
      ((DefaultComboBoxModel<Element>)myActivitySelector.getModel()).addElement(null);
    }
    myActivitySelector.setRenderer(new ColoredListCellRenderer<Element>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends Element> list, Element value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          append("Specify activity name...");
        }
        else {
          append(value.getAttributeNS(SdkConstants.ANDROID_URI, "name"));
          String label = value.getAttributeNS(SdkConstants.ANDROID_URI, "label");
          if (!StringUtil.isEmpty(label)) {
            append(" (" + label + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    });

    myChooserComponent = myChooser.createComponent();
    assert myChooserComponent != null;
    myChooserComponent.setVisible(false);

    myActivitySelector.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        Element item = (Element)e.getItem();
        myChooserComponent.setVisible(item == null);
      }
    });

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myActivitySelector, BorderLayout.NORTH);
    myPanel.add(myChooserComponent);

    init();
  }

  @NotNull
  public String getActivity() {
    if (myChooserComponent.isVisible()) {
      SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
      myChooser.applyTo(state);
      return state.ACTIVITY_CLASS;
    }
    Element item = (Element)myActivitySelector.getSelectedItem();
    return item.getAttributeNS(SdkConstants.ANDROID_URI, "name");
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String activity = getActivity();
    Project project = myModule.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
    if (activityClass == null) {
      return new ValidationInfo(AndroidBundle.message("cant.find.activity.class.error"));
    }

    // allScope gives us whole project and anything coming from libs
    PsiClass c = JavaExecutionUtil.findMainClass(project, activity, GlobalSearchScope.allScope(project));
    if (c == null || !c.isInheritor(activityClass, true)) {
      return new ValidationInfo(AndroidBundle.message("not.activity.subclass.error", activity));
    }
    // check whether activity is declared in the manifest
    Element element = MergedManifest.get(myModule).findActivity(ActivityLocatorUtils.getQualifiedActivityName(c));
    if (element == null) {
      return new ValidationInfo(AndroidBundle.message("activity.not.declared.in.manifest", c.getName()));
    }
    return null;
  }
}
