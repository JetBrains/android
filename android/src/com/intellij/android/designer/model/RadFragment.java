/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.model;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.LayoutMetadata;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.propertyTable.FragmentProperty;
import com.intellij.android.designer.propertyTable.IdProperty;
import com.intellij.android.designer.propertyTable.JavadocParser;
import com.intellij.android.designer.propertyTable.TextEditorWithAutoCommit;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.rendering.LayoutMetadata.KEY_FRAGMENT_LAYOUT;

/**
 * @author Alexander Lobas
 */
public class RadFragment extends RadViewComponent implements IConfigurableComponent {
  private static final Property NAME_PROPERTY =
    new FragmentProperty("name",
                         SdkConstants.ANDROID_URI,
                         new MyResourceEditor(),
                         JavadocParser.build("name", "Supply the name of the fragment class to instantiate."));

  private static final Property CLASS_PROPERTY = new FragmentProperty("class",
                                                                      null,
                                                                      new MyResourceEditor(),
                                                                      JavadocParser.build("class",
                                                                                          "Supply the name of the fragment class to instantiate."));

  private static final Property TAG_PROPERTY = new FragmentProperty("tag", SdkConstants.ANDROID_URI, new TextEditorWithAutoCommit(),
                                                                    JavadocParser.build(
                                                                      "tag",
                                                                      "Use <code>device-admin</code> as the root tag of the XML resource that\n" +
                                                                      "describes a\n" +
                                                                      "         {@link android.app.admin.DeviceAdminReceiver}, which is\n" +
                                                                      "         referenced from its\n" +
                                                                      "         {@link android.app.admin.DeviceAdminReceiver#DEVICE_ADMIN_META_DATA}\n" +
                                                                      "         meta-data entry.  Described here are the attributes that can be\n" +
                                                                      "         included in that tag."));

  private static final String NAME_KEY = "fragment.name";

  @Override
  public String getCreationXml() {
    return "<fragment android:layout_width=\"wrap_content\"\n" +
           "android:layout_height=\"wrap_content\"\n" +
           "android:name=\"" +
           getClientProperty(NAME_KEY) +
           "\"/>";
  }

  @Override
  public void configure(RadComponent rootComponent) throws Exception {
    String fragment = chooseFragment(rootComponent);
    if (fragment != null) {
      setClientProperty(NAME_KEY, fragment);
    }
    else {
      throw new Exception();
    }
  }

  @Nullable
  private static String chooseFragment(RadComponent rootComponent) {
    Module module = RadModelBuilder.getModule(rootComponent);
    if (module == null) {
      return null;
    }
    return ChooseClassDialog.openDialog(module, "Fragments", true, "android.app.Fragment", "android.support.v4.app.Fragment");
  }

  @Override
  public void setProperties(List<Property> properties) {
    if (!properties.contains(CLASS_PROPERTY)) {
      properties = new ArrayList<Property>(properties);
      properties.add(NAME_PROPERTY);
      properties.add(CLASS_PROPERTY);
      properties.add(IdProperty.INSTANCE);
      properties.add(TAG_PROPERTY);
    }
    super.setProperties(properties);
  }

  private static final class MyResourceEditor extends ResourceEditor {
    public MyResourceEditor() {
      super(null, Collections.<AttributeFormat>emptySet(), null);
    }

    @Override
    protected void showDialog() {
      String fragment = chooseFragment(myRootComponent);

      if (fragment != null) {
        setValue(fragment);
      }
    }
  }

  @Override
  public boolean addPopupActions(@NotNull AndroidDesignerEditorPanel designer,
                                 @NotNull DefaultActionGroup beforeGroup,
                                 @NotNull DefaultActionGroup afterGroup,
                                 @Nullable JComponent shortcuts,
                                 @NotNull List<RadComponent> selection) {
    super.addPopupActions(designer, beforeGroup, afterGroup, shortcuts, selection);
    beforeGroup.add(new AssignFragmentLayoutAction(designer));
    beforeGroup.addSeparator();
    return true;
  }

  private class AssignFragmentLayoutAction extends AnAction {
    private final AndroidDesignerEditorPanel myDesigner;

    private AssignFragmentLayoutAction(AndroidDesignerEditorPanel designer) {
      super("Choose Preview Layout...");
      myDesigner = designer;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ChooseResourceDialog dialog = new ChooseResourceDialog(myDesigner.getModule(), new ResourceType[]{ResourceType.LAYOUT}, null, null);
      dialog.setAllowCreateResource(false);
      if (dialog.showAndGet()) {
        String layout = dialog.getResourceName();
        Project project = myDesigner.getProject();
        XmlFile xmlFile = myDesigner.getXmlFile();
        XmlTag tag = getTag();
        LayoutMetadata.setProperty(project, "Set List Type", xmlFile, tag, KEY_FRAGMENT_LAYOUT, TOOLS_URI, layout);
        myDesigner.requestRender();
      }
    }
  }
}