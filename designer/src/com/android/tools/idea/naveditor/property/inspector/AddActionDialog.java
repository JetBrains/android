/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class AddActionDialog extends DialogWrapper {
  private JComboBox<NlComponent> myFromComboBox;
  private JComboBox<NlComponent> myDestinationComboBox;
  private JComboBox<Transition> myEnterComboBox;
  private JComboBox<Transition> myExitComboBox;
  private JComboBox<NlComponent> myPopToComboBox;
  private JCheckBox myInclusiveCheckBox;
  private JCheckBox mySingleTopCheckBox;
  private JCheckBox myDocumentCheckBox;
  private JCheckBox myClearTaskCheckBox;
  private JPanel myContentPanel;

  public enum Transition {
    FADE {
      @Override
      public String toString() {
        return "Fade";
      }
    }, SLIDE_IN_LEFT {
      @Override
      public String toString() {
        return "Slide in Left";
      }
    }, SLIDE_OUT_RIGHT {
      @Override
      public String toString() {
        return "Slide out Right";
      }
    }
  }

  protected AddActionDialog(@NotNull List<NlComponent> components, @Nullable ResourceResolver resourceResolver) {
    super(false);
    assert components.size() == 1;
    NlComponent component = components.get(0);
    NlModel model = component.getModel();
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(component.getModel().getFacet());

    ListCellRendererWrapper<NlComponent> componentRenderer = new ListCellRendererWrapper<NlComponent>() {
      @Override
      public void customize(JList list, NlComponent value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("None");
        }
        else {
          setText(NavComponentHelperKt.getUiName(value, resourceResolver));
        }
      }
    };

    myFromComboBox.setRenderer(componentRenderer);
    myFromComboBox.addItem(component);
    myFromComboBox.setEnabled(false);

    myDestinationComboBox.setRenderer(componentRenderer);
    NavComponentHelperKt.getVisibleDestinations(component).forEach(c -> myDestinationComboBox.addItem(c));
    myDestinationComboBox.setSelectedItem(null);

    myEnterComboBox.addItem(Transition.FADE);
    myEnterComboBox.addItem(Transition.SLIDE_IN_LEFT);

    myExitComboBox.addItem(Transition.FADE);
    myExitComboBox.addItem(Transition.SLIDE_OUT_RIGHT);

    myPopToComboBox.setRenderer(componentRenderer);
    myPopToComboBox.addItem(null);
    model.flattenComponents().filter(c -> schema.getDestinationType(c.getTagName()) != null).forEach(c -> myPopToComboBox.addItem(c));

    init();

    myOKAction.putValue(Action.NAME, "Add");
    setTitle("Add Action");
  }

  @NotNull
  public NlComponent getSource() {
    //noinspection ConstantConditions
    return (NlComponent)myFromComboBox.getSelectedItem();
  }

  @NotNull
  public NlComponent getDestination() {
    //noinspection ConstantConditions
    return (NlComponent)myDestinationComboBox.getSelectedItem();
  }

  @NotNull
  public Transition getEnterTransition() {
    //noinspection ConstantConditions
    return (Transition)myEnterComboBox.getSelectedItem();
  }

  @NotNull
  public Transition getExitTransition() {
    //noinspection ConstantConditions
    return (Transition)myExitComboBox.getSelectedItem();
  }

  @Nullable
  public NlComponent getPopTo() {
    return (NlComponent)myPopToComboBox.getSelectedItem();
  }

  public boolean isInclusive() {
    return myInclusiveCheckBox.isSelected();
  }

  public boolean isSingleTop() {
    return mySingleTopCheckBox.isSelected();
  }

  public boolean isDocument() {
    return myDocumentCheckBox.isSelected();
  }

  public boolean isClearTask() {
    return myClearTaskCheckBox.isSelected();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myDestinationComboBox.getSelectedItem() == null) {
      return new ValidationInfo("Destination must be set!", myDestinationComboBox);
    }
    return super.doValidate();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
