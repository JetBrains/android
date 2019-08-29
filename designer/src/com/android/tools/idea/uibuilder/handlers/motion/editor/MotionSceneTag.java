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
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class wraps an XmlTag component and provides a uniform interface between it and other types of XML based objects
 * TODO switch to SmartPsiElementPointer<XmlTag>
 */
public class MotionSceneTag implements MTag {

  XmlTag myXmlTag;
  MotionSceneTag mParent;
  ArrayList<MTag> myChildren = new ArrayList<>();
  HashMap<String, Attribute> mAttrList = new HashMap<>();

  public MotionSceneTag(XmlTag tag, MotionSceneTag parent) {
    myXmlTag = tag;
    mParent = parent;
    if (tag == null) {
      return;
    }
    for (XmlTag subTag : tag.getSubTags()) {
      myChildren.add(new MotionSceneTag(subTag, this));
    }
    for (XmlAttribute attribute : tag.getAttributes()) {
      Attribute a =  new Attribute();
      a.mAttribute =  attribute.getName();
      a.mAttribute = a.mAttribute.substring(a.mAttribute.indexOf(":")+1);
      a.mNamespace =  attribute.getNamespacePrefix();
      a.mValue =  attribute.getValue();
     // Debug.log(a.mNamespace+"  : "+ a.mAttribute+"  = "+ a.mValue);

      mAttrList.put(a.mAttribute,a);
    }
  }

  @Override
  public String getTagName() {
    return myXmlTag.getName();
  }

  @Override
  public ArrayList<MTag> getChildren() {
    return myChildren;
  }

  @Override
  public HashMap<String, Attribute> getAttrList() {
    return mAttrList;
  }

  @Override
  public MTag[] getChildTags() {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : myChildren) {
        filter.add(child);
    }
    return filter.toArray(new MTag[0]);
  }

  @Override
  public MTag[] getChildTags(String type) {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : myChildren) {
      if (child.getTagName().equals(type)) {
        filter.add(child);
      }
    }
    return filter.toArray(new MTag[0]);
  }

  @Override
  public MTag[] getChildTags(String attribute, String value) {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : myChildren) {
      String childValue = child.getAttributeValue(attribute);
      if (childValue != null && childValue.endsWith(value)) {
        filter.add(child);
      }
    }
    return filter.toArray(new MTag[0]);
  }

  @Override
  public MTag[] getChildTags(String type, String attribute, String value) {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : myChildren) {
      if (child.getTagName().equals(type)) {
        String childValue = child.getAttributeValue(attribute);
        if (childValue != null && childValue.endsWith(value)) {
          filter.add(child);
        }
      }
    }
    return filter.toArray(new MTag[0]);  }

  @Override
  public String getAttributeValue(String attribute) {
    for (Attribute value : mAttrList.values()) {
   //   Debug.log(value.mNamespace+"  : "+ value.mAttribute+"  = "+ value.mValue);
      if (value.mAttribute.equals(attribute)) {
        return value.mValue;
      }
    }
    return null;  }

  @Override
  public void print(String space) {
    System.out.println("\n" + space + "<" + getTagName() + ">");
    for (XmlAttribute value : myXmlTag.getAttributes()) {
      System.out.println(space + "   " + value.getName() + "=\"" + value.getValue() + "\"");
    }
    for (MTag child : myChildren) {
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
    for (XmlAttribute value : myXmlTag.getAttributes()) {
      ret += "\n" + space + "   " + value.getNamespacePrefix() + ":" + value.getName() + "=\"" + value.getValue()
             + "\"";
      ret +=  " getValue() = "+value.getValue();
      ret +=  " getNamespacePrefix() = "+value.getNamespacePrefix();
      ret +=  " getNamespace() = "+value.getNamespace();
      ret +=  " getName() = "+value.getName();
      ret +=  " getValue() = "+value.getLocalName();
      ret +=  " getValue() = "+value.getDisplayValue();

    }
    ret += (" >\n");

    for (MTag child : myChildren) {
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
    for (XmlAttribute value : myXmlTag.getAttributes()) {
      out.print(
        "\n" + space + "   " + value.getNamespacePrefix() + ":" + value.getName() + "=\"" + value.getValue()
        + "\"");
      String ret = "  ";
      ret +=  " getValue() = "+value.getValue();
      ret +=  " getNamespacePrefix() = "+value.getNamespacePrefix();
      ret +=  " getNamespace() = "+value.getNamespace();
      ret +=  " getName() = "+value.getName();
      ret +=  " getValue() = "+value.getLocalName();
      ret +=  " getValue() = "+value.getDisplayValue();
      System.out.print(ret);
    }
    out.println(" >");

    for (MTag child : myChildren) {
      child.printFormal(space + "  ", out);
    }
    out.println(space + "</" + getTagName() + ">");

  }

  @Override
  public MTag getParent() {
    return mParent;
  }

  @Override
  public void deleteTag() {
    // TODO WE NEED THE ABILITY TO DELETE TAGS
  }

  @Override
  public TagWriter getChildTagWriter(String name) {
    return new MotionSceneTagWriter(this, name);
  }

  @Override
  public MotionSceneTagWriter getTagWriter() {
    return new MotionSceneTagWriter(this);
  }

  static class Root extends MotionSceneTag {
    Project mProject;
    VirtualFile mVirtualFile;
    XmlFile mXmlFile;
    NlModel mModel;
    public Root(XmlTag tag, Project project,
                VirtualFile virtualFile,
                XmlFile file,NlModel model) {
      super(tag, null);
      mProject = project;
      mVirtualFile = virtualFile;
      mXmlFile = file;
      mModel = model;
    }
  }

  private static final boolean DEBUG = true;

  public static MotionSceneTag parse(NlComponent motionLayout,
                                     Project project,
                                     VirtualFile virtualFile,
                                     XmlFile file) {
    NlModel model = motionLayout.getModel();

    MotionSceneTag motionSceneModel = new Root(file.getRootTag(), project, virtualFile, file, model);
    return motionSceneModel;
  }
}
