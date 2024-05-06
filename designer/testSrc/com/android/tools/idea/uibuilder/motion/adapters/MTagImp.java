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
package com.android.tools.idea.uibuilder.motion.adapters;

import static com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.ATTR_ANDROID_ID;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * MTag implementation based on parsing a XML file.
 */
public class MTagImp implements MTag, MTag.TagWriter {

  private static final boolean DEBUG = false;
  String name;
  MTagImp parent;
  Object clientData;
  HashMap<String, Attribute> mAttrList = new HashMap<>();

  @Override
  public String toString() {
    return ("MTag (" + name + " )");
  }

  @Override
  public String getTagName() {
    return name;
  }

  @Override
  public void setClientData(String type, Object clientData) {
    this.clientData = clientData;
  }

  @Override
  public Object getClientData(String type) {
    return this.clientData;
  }

  ArrayList<MTag> mChildren = new ArrayList<>();

  @Override
  public ArrayList<MTag> getChildren() {
    return mChildren;
  }

  @Override
  public HashMap<String, Attribute> getAttrList() {
    return mAttrList;
  }

  @Override
  public MTagImp[] getChildTags() {
    return (MTagImp[])mChildren.toArray(new MTagImp[0]);
  }

  @Override
  public MTag getParent() {
    return parent;
  }

  @Override
  public MTag[] getChildTags(String type) {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : mChildren) {
      if (child.getTagName().equals(type)) {
        filter.add(child);
      }
    }
    return filter.toArray(new MTag[0]);
  }

  /**
   * Get children who attribute == value
   */
  @Override
  public MTag[] getChildTags(String attribute, String value) {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : mChildren) {
      String childValue = child.getAttributeValue(attribute);
      if (childValue != null && childValue.endsWith(value)) {
        filter.add(child);
      }
    }
    return filter.toArray(new MTag[0]);
  }

  /**
   * Get children who attribute == value
   */
  @Override
  public MTag[] getChildTags(String type, String attribute, String value) {
    ArrayList<MTag> filter = new ArrayList<>();
    for (MTag child : mChildren) {
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
    for (MTag child : mChildren) {
      if (treeId.equals(child.getTreeId())) {
        return child;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getTreeId() {
    if (name.startsWith("Key")) {
      return getAttributeValue("framePosition") + "|" + name;
    }
    if (name.equals("Transition")) {
      return getAttributeValue("constraintSetStart") + "|" + getAttributeValue("constraintSetEnd");
    }
    return getAttributeValue(ATTR_ANDROID_ID);
  }

  @Override
  public String getAttributeValue(String attribute) {
    if (DEBUG) {
      System.out.println(Debug.getLocation() + " test  ---- attribute =  " + attribute);
    }

    for (Attribute value : mAttrList.values()) {
      if (DEBUG) {
        System.out.println(Debug.getLocation() + " test  ----  " + value.mAttribute);
      }

      if (value.mAttribute.equals(attribute)) {
        return value.mValue;
      }
    }
    return null;
  }

  @Override
  public void print(String space) {
    System.out.println("\n" + space + "<" + name + ">");
    for (Attribute value : mAttrList.values()) {
      System.out.println(space + "   " + value.mAttribute + "=\"" + value.mValue + "\"");
    }
    for (MTag child : mChildren) {
      child.print(space + "   ");
    }
    System.out.println(space + "</" + name + ">");
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
    ret += "\n" + space + "<" + name;
    Attribute[] attr = mAttrList.values().toArray(new Attribute[0]);
    Arrays.sort(attr, new Comparator<Attribute>() {
      @Override
      public int compare(Attribute o1, Attribute o2) {
        return o1.mAttribute.compareTo(o2.mAttribute);
      }
    });
    for (Attribute value : attr) {
      String nameSpace = value.mNamespace;
      if (nameSpace.startsWith("http")) {
        if (nameSpace.endsWith("res-auto")) {
          nameSpace = "motion";
        }
        if (nameSpace.endsWith("android")) {
          nameSpace = "android";
        }
      }
      ret += "\n" + space + "   " + nameSpace + ":" + value.mAttribute + "=\"" + value.mValue
             + "\"";
    }
    if (mChildren.size() == 0) {
      ret += (" />\n");
    }
    else {
      ret += (" >\n");
    }
    for (MTag child : mChildren) {
      ret += child.toFormalXmlString(space + "  ");
    }
    if (mChildren.size() > 0) {
      ret += space + "</" + name + ">\n";
    }
    return ret;
  }

  @Override
  public void printFormal(String space, PrintStream out) {
    if (space == null) {
      out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
      space = "";
    }
    out.print("\n" + space + "<" + name);
    for (Attribute value : mAttrList.values()) {
      out.print(
        "\n" + space + "   " + value.mNamespace + ":" + value.mAttribute + "=\"" + value.mValue
        + "\"");
    }
    out.println(" >");

    for (MTag child : mChildren) {
      child.printFormal(space + "  ", out);
    }
    out.println(space + "</" + name + ">");
  }

  @Override
  public void setAttribute(String type, String attribute, String value) {
    Attribute attr = mAttrList.get(attribute);
    if (attr == null) {
      attr = new Attribute();
      attr.mNamespace = type;
      attr.mAttribute = attribute;
      mAttrList.put(attribute, attr);
    }
    attr.mValue = value;
  }

  @Override
  public TagWriter deleteTag() {
    parent.mChildren.remove(this);
    return this;
  }

  @Override
  public MTag commit(String commandName) {
    return this;
  }

  @Override
  public void addCommitListener(CommitListener listener) {

  }

  @Override
  public void removeCommitListener(CommitListener listener) {

  }

  public static void main(String[] str) {
    String dir = "/media/hoford/hofordssd/dtools/design-tools/examples/Dev/app/src/main/res/xml";
    String file = "motion_testscene_08_scene.xml";
    MTagImp mTag = parse(new File(dir + File.separator + file));
    mTag.printFormal(null, System.out);
  }

  public static MTagImp parse(String str) {
    if (DEBUG) {
      System.out.println(" parse " + str);
    }
    HashSet<MTagImp> props = new HashSet<>();
    try {
      InputStream inputStream = new ByteArrayInputStream(str.getBytes(Charset.forName("UTF-8")));
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(inputStream, new DefaultHandler() {
        MTagImp currentTag = null;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
          throws SAXException {
          MTagImp newTag = (currentTag == null) ? new RootTag(null) : new MTagImp();
          newTag.parent = currentTag;
          newTag.name = qName;
          if (currentTag != null) {
            currentTag.mChildren.add(newTag);
          }
          else {
            props.add(newTag);
          }
          currentTag = newTag;
          if (DEBUG) {
            System.out.println("START " + currentTag.name);
          }

          for (int i = 0; i < attributes.getLength(); i++) {
            String aName = attributes.getQName(i);
            Attribute attribute = new Attribute();
            String[] sp = aName.split(":");
            if (sp.length != 2) {
              continue;
            }
            attribute.mAttribute = sp[1];
            attribute.mNamespace = sp[0];
            attribute.mValue = attributes.getValue(i);
            currentTag.mAttrList.put(aName, attribute);
            if (DEBUG) {

              System.out.println("     " + attribute.mAttribute);
            }
          }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
          if (currentTag != null) {
            if (DEBUG) {
              System.out.println("END " + currentTag.name + "  qName=" + qName);
            }
            currentTag = currentTag.parent;
          }
        }
      });
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return props.iterator().next();
  }

  static class RootTag extends MTagImp {
    File mSourceFile;

    RootTag(File file) {
      mSourceFile = file;
    }
  }

  public static MTagImp parse(File file) {
    if (DEBUG) {
      System.out.println(" parse " + file.getName() + (file.exists() ? "Exist" : "not found"));
    }
    HashSet<MTagImp> props = new HashSet<>();
    try {
      FileInputStream inputStream = new FileInputStream(file);
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(inputStream, new DefaultHandler() {
        MTagImp currentTag = null;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
          throws SAXException {
          MTagImp newTag = (currentTag == null) ? new RootTag(file) : new MTagImp();
          newTag.parent = currentTag;
          newTag.name = qName;
          if (currentTag != null) {
            currentTag.mChildren.add(newTag);
          }
          else {
            props.add(newTag);
          }
          currentTag = newTag;
          if (DEBUG) {
            System.out.println("START " + currentTag.name);
          }

          for (int i = 0; i < attributes.getLength(); i++) {
            String aName = attributes.getQName(i);
            Attribute attribute = new Attribute();
            String[] sp = aName.split(":");
            if (sp.length != 2) {
              continue;
            }
            attribute.mAttribute = sp[1];
            attribute.mNamespace = sp[0];
            attribute.mValue = attributes.getValue(i);
            currentTag.mAttrList.put(aName, attribute);
            if (DEBUG) {

              System.out.println("     " + attribute.mAttribute);
            }
          }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
          if (currentTag != null) {
            if (DEBUG) {
              System.out.println("END " + currentTag.name + "  qName=" + qName);
            }
            currentTag = currentTag.parent;
          }
        }
      });
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return props.iterator().next();
  }

  class TagWriterImp extends MTagImp implements TagWriter {
    HashMap<String, Attribute> newTags = new HashMap<>();

    TagWriterImp(String name, MTagImp parent) {
      this.name = name;
      super.parent = parent;
      parent.mChildren.add(this);
    }

    @Override
    public void setAttribute(String nameSpace, String attributeName, String value) {
      Attribute attribute = new Attribute();
      attribute.mAttribute = attributeName;
      attribute.mNamespace = nameSpace;
      attribute.mValue = value;
      mAttrList.put(nameSpace + ":" + attributeName, attribute);
      newTags.put(nameSpace + ":" + attributeName, attribute);
    }

    @Override
    public TagWriter deleteTag() {
      return null;
    }

    @Override
    public MTag commit(@Nullable String commandName) {
      if (DEBUG) {
        printFormal(" > ", System.out);
      }
      parent.mChildren.remove(this);
      MTagImp ret = new MTagImp();
      ret.name = name;
      ret.parent = parent;
      ret.mAttrList = mAttrList;
      TagWriter[] childArray = (TagWriter[])mChildren.toArray(new TagWriter[0]);
      for (MTag child : childArray) {
        ret.mChildren.add(((TagWriter)child).commit(commandName));
      }
      parent.mChildren.add(ret);
      return ret;
    }

    @Override
    public void addCommitListener(CommitListener listener) {
      throw new RuntimeException(" not  implemented ");
    }

    @Override
    public void removeCommitListener(CommitListener listener) {

    }
  }

  @Override
  public TagWriter getChildTagWriter(String name) {
    MTagImp imp = new MTagImp();
    imp.name = name;
    mChildren.add(imp);
    return imp;
  }

  @Override
  public TagWriter getTagWriter() {
    return this;
  }
}
