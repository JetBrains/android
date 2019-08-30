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

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.HashMap;

/**
 * The Writer form of the WrapMotionScene used when you are modifying the tag.
 */
public class MotionSceneTagWriter extends MotionSceneTag implements MTag.TagWriter {
  String mType;

  HashMap<String, Attribute> mNewAttrList = new HashMap<>();

  public MotionSceneTagWriter(MotionSceneTag parent, String type) {
    super(null, parent);
    mType = type;
  }

  /**
   * Create tag writer version of tag
   *
   * @param tag
   */
  public MotionSceneTagWriter(MotionSceneTag tag) {
    super(tag.myXmlTag, tag.mParent);
    mType = tag.getTagName();
  }

  @Override
  public void setAttribute(String type, String attribute, String value) {
    Attribute a = new Attribute();
    a.mAttribute = type + ":" + attribute;
    a.mAttribute = attribute;
    a.mNamespace = type;
    a.mValue = value;
    // Debug.log(a.mNamespace+"  : "+ a.mAttribute+"  = "+ a.mValue);

    mAttrList.put(a.mAttribute, a);
    mNewAttrList.put(a.mAttribute, a);
  }

  @Override
  public TagWriter getChildTagWriter(String name) {
    MotionSceneTagWriter ret = new MotionSceneTagWriter(this, name);
    myChildren.add(ret);
    return ret;
  }

  @Override
  public MTag commit(@Nullable String commandName) {
    MotionSceneTag.Root root = getRoot(this);
    if (root == null) {
      return null;
    }
    if (myXmlTag != null) { // this already exist
      update(root, commandName);
    }
    else {
      myXmlTag = createConstraint(mType, this, root);
    }
    MotionSceneTag result = new MotionSceneTag(myXmlTag, mParent);
    for (MTag child : myChildren) {
      if (child instanceof MotionSceneTagWriter) {
        MotionSceneTagWriter tagWriter = (MotionSceneTagWriter)child;
        result.myChildren.add(tagWriter.commit(commandName));
      }
    }
    if (!(mParent instanceof MotionSceneTagWriter)) {
      mParent.myChildren.add(result);
    }
    return result;
  }

  private void update(@NotNull MotionSceneTag.Root root, @Nullable String commandName) {
    WriteCommandAction.runWriteCommandAction(root.mProject, commandName, null, () -> {
      for (String key : mNewAttrList.keySet()) {
        Attribute attr = mNewAttrList.get(key);
        String namespace = MotionSceneAttrs.lookupName(attr);
        myXmlTag.setAttribute(attr.mAttribute, namespace, attr.mValue);
      }
    }, root.mXmlFile);

    saveAndNotify(root.mXmlFile, root.mModel);
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

  interface Match {
    boolean fits(XmlTag tag);
  }

  private static XmlTag getMatching(XmlFile xmlFile, Match match) {
    XmlTag constraintSet = null;
    XmlTag[] tags = xmlFile.getRootTag().getSubTags();
    for (int i = 0; i < tags.length; i++) {
      XmlTag tag = tags[i];
      if (match.fits(tag)) {
        return tag;
      }
    }
    return null;
  }

  public static XmlTag createConstraint(String type, MotionSceneTagWriter tag, MotionSceneTag.Root root) {

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(root.mProject, root.mVirtualFile);

    XmlTag createdTag = WriteCommandAction.<XmlTag>runWriteCommandAction(root.mProject, () -> {
      XmlTag transitionTag = ((MotionSceneTag)tag.getParent()).myXmlTag;
      XmlTag child = transitionTag.createChildTag(type, null, null, false);
      child = transitionTag.addSubTag(child, false);
      for (String key : tag.mNewAttrList.keySet()) {
        Attribute attr = tag.mNewAttrList.get(key);
        String namespace = MotionSceneAttrs.lookupName(attr);
        child.setAttribute(attr.mAttribute, namespace, attr.mValue);
      }
      return child;
    });

    saveAndNotify(xmlFile, root.mModel);
    return createdTag;
  }

  public void deleteTag(@NotNull String command) {
    MotionSceneTag.Root root = getRoot(this);

    Runnable operation = () -> {
      myXmlTag.delete();
    };
    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(root.mProject, root.mVirtualFile);

    WriteCommandAction.runWriteCommandAction(root.mProject, command, null, operation, xmlFile);

    mParent.myChildren.remove(this);
  }

  /**
   * Save file if necessary
   *
   * @param xmlFile
   * @param nlModel
   */
  public static void saveAndNotify(PsiFile xmlFile, NlModel nlModel) {
    LayoutPullParsers.saveFileIfNecessary(xmlFile);
    nlModel.notifyModified(NlModel.ChangeType.EDIT);
  }


  private static String trimId(String name) { return name.substring(name.indexOf('/') + 1); }
}
