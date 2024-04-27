/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class wraps a NL component and provides a uniform interface between it and other types of XML based objects
 */
public class NlComponentTag implements MTag {
  NlComponent mComponent;
  NlComponentTag mParent;
  private static final boolean DEBUG = false;

  public NlComponentTag(NlComponent component, NlComponentTag parent) {
    mComponent = component;
    mParent = parent;
  }

  public NlComponent getComponent() {
    return mComponent;
  }

  @Override
  public String getTagName() {
    return mComponent.getTagName();
  }

  @Override
  public MTag getParent() {
    return mParent;
  }

  @Override
  public void setClientData(String type, Object motionAttributes) {
    if (DEBUG) {
      Debug.log("setClientData MOTION_LAYOUT_PROPERTIES setting " + motionAttributes );
    }
    mComponent.putClientProperty(type, motionAttributes );
  }

  @Override
  public Object getClientData(String type) {
    return mComponent.getClientProperty(type);
  }

  @Override
  public ArrayList<MTag> getChildren() {
    ArrayList<MTag> ret = new ArrayList<>();
    List<NlComponent> ch = mComponent.getChildren();
    for (NlComponent component : ch) {
      ret.add(new NlComponentTag(component, this));
    }
    return ret;
  }

  @Override
  public HashMap<String, Attribute> getAttrList() {
    HashMap<String, Attribute> ret = new  HashMap<>();
    List<AttributeSnapshot> att = mComponent.getAttributes();
    for (AttributeSnapshot attribute : att) {
      Attribute a = new Attribute();
      a.mAttribute = attribute.name;
      a.mNamespace = attribute.namespace;
      a.mValue = attribute.value;
      ret.put(a.mAttribute,a);
    }
    return ret;
  }

  @Override
  public MTag[] getChildTags() {
    return   getChildren().toArray(new MTag[0]);
  }

  @Override
  public MTag[] getChildTags(String type) {
    ArrayList<MTag> ret = new ArrayList<>();
    for (MTag child : getChildren()) {
      if (child.getTagName().equals(type)) {
        ret.add(child);
      }
    }
    return ret.toArray(new MTag[0]);
  }

  @Override
  public MTag[] getChildTags(String attribute, String value) {
    ArrayList<MTag> ret = new ArrayList<>();
    for (MTag child : getChildren()) {
      if (value.equals(child.getAttributeValue(attribute))) {
        ret.add(child);
      }
    }
    return ret.toArray(new MTag[0]);
  }

  @Override
  public MTag[] getChildTags(String type, String attribute, String value) {
    ArrayList<MTag> ret = new ArrayList<>();
    for (MTag child : getChildren()) {
      if (child.getTagName().equals(type) && value.equals(child.getAttributeValue(attribute))) {
        ret.add(child);
      }
    }
    return ret.toArray(new MTag[0]);
  }

  @Override
  @Nullable
  public MTag getChildTagWithTreeId(String type, String treeId) {
    for (MTag child : getChildren()) {
      if (child.getTreeId().equals(treeId)) {
        return child;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getTreeId() {
    return mComponent.getId();
  }

  @Override
  public String getAttributeValue(String attribute) {
    for (AttributeSnapshot componentAttribute : mComponent.getAttributes()) {
      if (componentAttribute.name.equals(attribute)){
        return componentAttribute.value;
      }
    }
    return null;
  }

  @Override
  public void print(String space) {
    System.out.println("\n" + space + "<" + getTagName() + ">");
    for (AttributeSnapshot value : mComponent.getAttributes()) {
      System.out.println(space + "   " + value.name + "=\"" + value.value + "\"");
    }
    for (MTag child : getChildTags()) {
      child.print(space + "   ");
    }
    System.out.println(space + "</" + getTagName() + ">");
  }

  @Override
  public String toXmlString() {
    return toFormalXmlString("");
  }

  @Override
  public String toFormalXmlString(String space) {
    String ret = "";
    if (space == null) {
      ret = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
      space = "";
    }
    ret += "\n" + space + "<" + getTagName();
    for (AttributeSnapshot value : mComponent.getAttributes()) {
      ret += "\n" + space + "   " + value.namespace + ":" + value.name + "=\"" + value.value
             + "\"";
    }
    ret += (" >\n");

    for (MTag child : getChildTags()) {
      ret += child.toFormalXmlString(space + "  ");
    }
    ret += space + "</" + getTagName() + ">\n";
    return ret;
  }

  @Override
  public void printFormal(String space, PrintStream out) {
    if (space == null) {
      out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
      space = "";
    }
    out.print("\n" + space + "<" + getTagName());
    for (AttributeSnapshot value : mComponent.getAttributes()) {
      out.print(
        "\n" + space + "   " + value.namespace + ":" + value.name + "=\"" + value.value
        + "\"");
    }
    out.println(" >");

    for (MTag child : getChildTags()) {
      child.printFormal(space + "  ", out);
    }
    out.println(space + "</" + getTagName() + ">");
  }

  @Override
  public TagWriter getChildTagWriter(String name) {
    return null; // TODO WE NEED TagWriter. But currently we do not write NLComponents
  }

  @Override
  public TagWriter getTagWriter() {
    return null;
  }

}
