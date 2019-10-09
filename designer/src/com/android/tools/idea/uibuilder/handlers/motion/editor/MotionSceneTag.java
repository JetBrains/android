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

import static com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.ATTR_ANDROID_ID;
import static com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END;
import static com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.intellij.openapi.command.WriteCommandAction;
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
  private static final boolean DEBUG = false;
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
    if (myXmlTag == null) {
      return null;
    }
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
    if (value == null) {
      return  null;
    }
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
    return filter.toArray(new MTag[0]);
  }

  @Override
  @Nullable
  public MTag getChildTagWithTreeId(String type, String treeId) {
    if (treeId == null) {
      return null;
    }
    for (MTag child : myChildren) {
      if (child.getTagName().equals(type)) {
        String childValue = child.getTreeId();
        if (childValue != null && childValue.equals(treeId)) {
          return child;
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getTreeId() {
    switch (getTagName()) {
      case Tags.CONSTRAINTSET:
        return getAttributeValue(ATTR_ANDROID_ID);
      case Tags.TRANSITION:
        return String.format("%1$s|%2$s|%3$s",
                             getAttributeValue(ATTR_ANDROID_ID),
                             getAttributeValue(ATTR_CONSTRAINTSET_START),
                             getAttributeValue(ATTR_CONSTRAINTSET_END));
      case Tags.KEY_ATTRIBUTE:
      case Tags.KEY_POSITION:
      case Tags.KEY_CYCLE:
      case Tags.KEY_TIME_CYCLE:
      case Tags.KEY_TRIGGER:
        return computeKeyFrameTreeId();
      default:
        return null;
    }
  }

  /**
   * Compute the a treeId for a KeyFrame
   * The intent is it should look like
   * "KeyCycle,32|Id:button1|scaleX"
   */
  private String computeKeyFrameTreeId() {
    String tagName = getTagName();
    String target = getAttributeValue(MotionSceneAttrs.Key.MOTION_TARGET);
    String pos = getAttributeValue(MotionSceneAttrs.Key.FRAME_POSITION);

    StringBuilder key = new StringBuilder();
    key.append(tagName);
    if (pos != null) { // TAGS.KEY_TRIGGER may not have a framePosition
      key.append(",").append(pos);
    }
    if (target != null) {
      if (target.startsWith("@")) {
        key.append("|Id:").append(Utils.stripID(target));
      }
      else {
        key.append("|Tag:").append(target);
      }
    }
    for (String keyAttribute : MotionSceneAttrs.KeyAttributeOptions) {
      if (getAttributeValue(keyAttribute) != null) {
        key.append(",").append(keyAttribute);
      }
    }
    return key.toString();
  }

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

  @Nullable
  public XmlTag getXmlTag() {
    return myXmlTag != null && myXmlTag.isValid() ? myXmlTag : null;
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
        "\n" + space + "   " + value.getName() + "=\"" + value.getValue()
        + "\"");
      String ret = "  ";
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

  private static MotionSceneTag.Root getRoot(MotionSceneTag tag) {
    while (!(tag instanceof MotionSceneTag.Root)) {
      tag = tag.mParent;
      if (tag == null) {
        return null;
      }
    }
    return (MotionSceneTag.Root)tag;
  }

  @Override
  public void setClientData(String type, Object motionAttributes) {
    if (DEBUG) {
      Debug.log("setClientData NO SUPPORT FOR CLIENT DATA" );
    }
  }

  @Override
  public Object getClientData(String type) {
    return null;
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


  public static MotionSceneTag parse(NlComponent motionLayout,
                                     Project project,
                                     VirtualFile virtualFile,
                                     XmlFile file) {
    NlModel model = motionLayout.getModel();

    return new Root(file.getRootTag(), project, virtualFile, file, model);
  }
}
