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

import com.android.SdkConstants;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.io.PsiFileUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * The Writer form of the WrapMotionScene used when you are modifying the tag.
 */
public class MotionSceneTagWriter extends MotionSceneTag implements MTag.TagWriter {
  private MotionSceneTag mTag;
  private String mType;
  private ArrayList<CommitListener> myListeners = new ArrayList<>();
  private HashMap<String, Attribute> mNewAttrList = new LinkedHashMap<>();
  private DeleteTag deleteRun;

  interface DeleteTag {
    void delete(String command);
  }

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
    mTag = tag;
    mType = tag.getTagName();
  }

  @Override
  public String getTagName() {
    return mType;
  }

  @Override
  public void setAttribute(String type, String attribute, String value) {
    Attribute a = new Attribute();
    a.mAttribute = type + ":" + attribute;
    a.mAttribute = attribute;
    a.mNamespace = type;
    a.mValue = value;
    Track track = getRoot(this).myTrack;
    Track.motionEditorEdit(track);
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
    boolean tagCreated = false;
    if (deleteRun != null) {
      deleteRun.delete(commandName);
      deleteRun = null;
      return null;
    }
    MotionSceneTag.Root root = getRoot(this);
    if (root == null) {
      return null;
    }
    if (myXmlTag != null) { // this already exist
      update(root, commandName);
    }
    else {
      myXmlTag = createTag(mType, this, root, commandName);
      tagCreated = true;
    }
    MotionSceneTag result = new MotionSceneTag(myXmlTag, mParent);
    for (MTag child : myChildren) {
      if (child instanceof MotionSceneTagWriter) {
        MotionSceneTagWriter tagWriter = (MotionSceneTagWriter)child;
        result.myChildren.add(tagWriter.commit(commandName));
      }
    }
    if (tagCreated && !(mParent instanceof MotionSceneTagWriter)) {
      mParent.myChildren.add(result);
    }
    notifyListeners(result);
    return result;
  }

  private void update(@NotNull MotionSceneTag.Root root, @Nullable String commandName) {
    WriteCommandAction.runWriteCommandAction(root.mProject, commandName, null, () -> {
      boolean hasVerticalConstraint = false;
      boolean hasHorizontalConstraint = false;
      CommandProcessor.getInstance().addAffectedFiles(root.mProject, root.mModel.getFile().getVirtualFile());
      CommandProcessor.getInstance().addAffectedFiles(root.mProject, root.mXmlFile.getVirtualFile());
      for (String key : mNewAttrList.keySet()) {
        Attribute attr = mNewAttrList.get(key);
        hasVerticalConstraint |= MotionSceneAttrs.VERTICAL_CONSTRAINT_ATTRS.contains(attr.mAttribute);
        hasHorizontalConstraint |= MotionSceneAttrs.HORIZONTAL_CONSTRAINT_ATTRS.contains(attr.mAttribute);
        String namespace = MotionSceneAttrs.lookupName(attr);
        myXmlTag.setAttribute(attr.mAttribute, namespace, attr.mValue);
        mTag.getAttrList().put(attr.mAttribute, attr);
      }
      if (hasVerticalConstraint) {
        myXmlTag.setAttribute(MotionSceneAttrs.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, SdkConstants.AUTO_URI, null);
        Attribute a = new Attribute();
        a.mAttribute = MotionSceneAttrs.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
        a.mNamespace = SdkConstants.AUTO_URI;
        mTag.getAttrList().put(MotionSceneAttrs.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, a);
      }
      if (hasHorizontalConstraint) {
        myXmlTag.setAttribute(MotionSceneAttrs.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, SdkConstants.AUTO_URI, null);
        Attribute a = new Attribute();
        a.mAttribute = MotionSceneAttrs.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
        a.mNamespace = SdkConstants.AUTO_URI;
        mTag.getAttrList().put(MotionSceneAttrs.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, a);
      }
    }, root.mXmlFile, root.mModel.getFile());

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

  /**
   * Utility method to pass multiple files to a WriteCommandAction
   * @param root
   * @param commandName
   * @param computable
   * @param files
   * @return
   */
  private static XmlTag writeAction(MotionSceneTag.Root root, String commandName,
                                    final Computable<XmlTag> computable,
                                    PsiFile... files) {
    return WriteCommandAction.writeCommandAction(root.mProject, files)
      .withName(commandName).withGroupId(null).compute(() -> computable.compute());
  }

  public static XmlTag createTag(String type, MotionSceneTagWriter tag, MotionSceneTag.Root root, String commandName) {

    XmlTag createdTag = writeAction(root, commandName, () -> {
      CommandProcessor.getInstance().addAffectedFiles(root.mProject, root.mModel.getFile().getVirtualFile());
      CommandProcessor.getInstance().addAffectedFiles(root.mProject, root.mXmlFile.getVirtualFile());
      XmlTag transitionTag = ((MotionSceneTag)tag.getParent()).myXmlTag;
      XmlTag child = transitionTag.createChildTag(type, null, null, false);
      child = transitionTag.addSubTag(child, false);
      for (String key : tag.mNewAttrList.keySet()) {
        Attribute attr = tag.mNewAttrList.get(key);
        String namespace = MotionSceneAttrs.lookupName(attr);
        child.setAttribute(attr.mAttribute, namespace, attr.mValue);
      }
      return child;
    }, root.mXmlFile, root.mModel.getFile());

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(root.mProject, root.mVirtualFile);
    saveAndNotify(xmlFile, root.mModel);

    return createdTag;
  }

  @Override
  public TagWriter deleteTag() {
    if (deleteRun != null) {
      return this;
    }
    MotionSceneTag.Root root = getRoot(this);
    if (root == null) {
      throw new RuntimeException("no root tag");
    }
    if (myXmlTag == null) {
      throw new RuntimeException("myXmlTag is null");
    }

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(root.mProject, root.mVirtualFile);
    deleteRun = new DeleteTag() {
      @Override
      public void delete(String cmd) {
        WriteCommandAction.<XmlTag>runWriteCommandAction(root.mProject, cmd, null, () -> {
          CommandProcessor.getInstance().addAffectedFiles(root.mProject, root.mModel.getFile().getVirtualFile());
          CommandProcessor.getInstance().addAffectedFiles(root.mProject, root.mXmlFile.getVirtualFile());
          myXmlTag.delete();
        }, xmlFile, root.mModel.getFile());
      }
    };
    return this;
  }

  /**
   * Save file if necessary
   *
   * @param xmlFile
   * @param nlModel
   */
  public static void saveAndNotify(PsiFile xmlFile, NlModel nlModel) {
    PsiFileUtil.saveFileIfNecessary(xmlFile);
    // Some tests need to read during notifyModified. The invokeLater avoids deadlocks.
    ApplicationManager.getApplication().invokeLater(() -> nlModel.notifyModified(NlModel.ChangeType.EDIT));
  }

  @Override
  public void addCommitListener(CommitListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeCommitListener(CommitListener listener) {
    myListeners.remove(listener);
  }

  private void notifyListeners(MotionSceneTag tag) {
    for (CommitListener listener : myListeners) {
      listener.commit(tag);
    }
  }
}
