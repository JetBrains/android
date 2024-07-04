/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * The main interface to tags
 */
public interface MTag {

  @Override
  public String toString();

  public String getTagName();

  void setClientData(String type, Object motionAttributes);

  public Object getClientData(String type);

  public static class Attribute {
    public String mNamespace;
    public String mAttribute;
    public String mValue;
  }

  public ArrayList<MTag> getChildren();

  public HashMap<String, Attribute> getAttrList();

  public MTag[] getChildTags();

  public MTag getParent();

  public MTag[] getChildTags(String type);

  /**
   * Get children who attribute == value
   */
  public MTag[] getChildTags(String attribute, String value);

  /**
   * Get children who attribute == value
   */
  public MTag[] getChildTags(String type, String attribute, String value);

  public String getAttributeValue(String attribute);

  public MTag getChildTagWithTreeId(String type, String treeId);

  public String getTreeId();

  default boolean isSameTreeIdHierarchy(MTag other) {
    if (!Objects.equals(getTagName(), other.getTagName()) ||
        !Objects.equals(getTreeId(), other.getTreeId())) {
      return false;
    }
    MTag parent = getParent();
    MTag otherParent = other.getParent();
    if (parent == null || otherParent == null) {
      return parent == null && otherParent == null;
    }
    return parent.isSameTreeIdHierarchy(otherParent);
  }

  public void print(String space);

  public String toXmlString();

  public String toFormalXmlString(String space);

  public void printFormal(String space, PrintStream out);

  /**
   * Create a tag writer for a child of this tag
   * @param name
   * @return
   */
  public TagWriter getChildTagWriter(String name);

  /**
   * Provide the tag write version of this tag
   *
   * @return
   */
  public TagWriter getTagWriter();

  interface TagWriter extends MTag {
    void setAttribute(String type, String attribute, String value);
    TagWriter deleteTag();

    /**
     * Commit is responsible for saving the tag writer
     * and returning a tag version of its self.
     * @param commandName
     * @return
     */
    MTag commit(@Nullable String commandName);

    void addCommitListener(CommitListener listener);

    void removeCommitListener(CommitListener listener);

  }

  interface CommitListener {
    void commit(MTag tag);
  }

  public static String serializeTag(MTag tag) {
    ByteArrayOutputStream buff = new ByteArrayOutputStream();
    PrintStream pout = new PrintStream(buff);
    tag.printFormal("",pout);
    return  buff.toString();
  }
}
