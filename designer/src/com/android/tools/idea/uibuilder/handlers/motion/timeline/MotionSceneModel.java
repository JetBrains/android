/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NamedNodeMap;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.*;

/**
 * The model of the Motion scene file.
 * This parses the file and provide hooks to write the file.
 */
public class MotionSceneModel {
  public static final boolean BROKEN = true;
  public static final boolean DEBUG = true;
  HashMap<String, MotionSceneView> mySceneViews = new HashMap<>();
  ArrayList<ConstraintSet> myConstraintSets;
  ArrayList<TransitionTag> myTransition;
  OnSwipeTag myOnSwipeTag;
  private VirtualFile myVirtualFile;
  private Project myProject;
  private NlModel myNlModel;
  private String myName;

  public String getName() {
    return myName;
  }
  public TransitionTag getTransitionTag(int i) {
    return myTransition.get(i);
  }

  public OnSwipeTag getOnSwipeTag() {
    return myOnSwipeTag;
  }

  private XmlFile motionSceneFile() {
    return (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, myVirtualFile);
  }

  public ConstraintSet getStartConstraintSet() {
    TransitionTag tag = myTransition.get(0);
    return tag.getConstraintSetStart();
  }

  public ConstraintSet getEndConstraintSet() {
    TransitionTag tag = myTransition.get(0);
    return tag.getConstraintSetEnd();
  }
  public ArrayList<TransitionTag> getTransitions() {
    return myTransition;
  }
  public String[] getConstraintSetNames() {
    String[]names = new String[myConstraintSets.size()];
    for (int i = 0; i < names.length; i++) {
      names[i] = myConstraintSets.get(i).mId;

    }
    return  names;
  }

  // Represents a single view in the motion scene
  public static class MotionSceneView {
    String mid;
    Icon myIcon;
    MotionSceneModel myModel;
    public ArrayList<KeyPos> myKeyPositions = new ArrayList<>();
    public ArrayList<KeyAttributes> myKeyAttributes = new ArrayList<>();
    public ArrayList<KeyCycle> myKeyCycles = new ArrayList<>();

    @NotNull
    public Icon getIcon() {
      if (myIcon == null) {
        myIcon = findIcon();
      }
      return myIcon;
    }

    @NotNull
    private Icon findIcon() {
      NlComponent component = myModel.myNlModel.find(mid);
      if (component == null) {
        return StudioIcons.LayoutEditor.Palette.VIEW;
      }
      ViewHandlerManager manager = ViewHandlerManager.get(myModel.myProject);
      ViewHandler handler = manager.getHandler(component);
      if (handler == null) {
        return StudioIcons.LayoutEditor.Palette.VIEW;
      }
      return handler.getIcon(component);
    }
  }

  public MotionSceneView getMotionSceneView(@NotNull String viewId) {
    return mySceneViews.get(viewId);
  }

  /**
   * Create a new Keyframe tag
   *
   * @param nlModel
   * @param type
   * @param framePosition
   * @param id
   */
  public void createKeyFrame(String type, int framePosition, String id) {
    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, myVirtualFile);
    switch (type) {
      case KeyTypePosition:
        for (KeyPos keys : mySceneViews.get(id).myKeyPositions) {
          if (keys.framePosition == framePosition) {
            return;
          }
        }
        break;
      case KeyTypeAttribute:
        for (KeyAttributes keys : mySceneViews.get(id).myKeyAttributes) {
          if (keys.framePosition == framePosition) {
            return;
          }
        }
        break;
      case KeyTypeCycle:
        for (KeyCycle keys : mySceneViews.get(id).myKeyCycles) {
          if (keys.framePosition == framePosition) {
            return;
          }
        }
    }
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        XmlTag keyFrame = null;
        XmlTag[] tags = xmlFile.getRootTag().getSubTags();
        for (int i = 0; i < tags.length; i++) {
          XmlTag tag = tags[i];
          String keyNodeName = tag.getName();
          if (keyNodeName.equals(MotionSceneKeyFrameSet)) {
            keyFrame = tag;
            break;
          }
        }
        if (keyFrame == null) { // no keyframes need to create
          keyFrame =
            xmlFile.getRootTag().createChildTag(MotionSceneKeyFrameSet, null, null, false);
          keyFrame = xmlFile.getRootTag().addSubTag(keyFrame, false);
        }

        XmlTag createdTag = keyFrame.createChildTag(type, null, null, false);
        createdTag = keyFrame.addSubTag(createdTag, false);
        createdTag.setAttribute(KeyAttributes_framePosition, AUTO_URI, Integer.toString(framePosition));
        createdTag.setAttribute(KeyAttributes_target, AUTO_URI, "@id/" + id);
      //  createdTag.setAttribute(KeyAttributes_target, AUTO_URI, "@id/" + id);
        if (type.equals(KeyTypePosition)) {
          createdTag.setAttribute(KeyPosition_type, AUTO_URI, KeyPosition_type_cartesian);
        }
      }
    });
    if (myNlModel != null) {
      // TODO: we may want to do live edits instead, but LayoutLib needs
      // anyway to save the file to disk, so...
      LayoutPullParsers.saveFileIfNecessary(xmlFile);
      myNlModel.notifyModified(NlModel.ChangeType.EDIT);
    }
  }

  /* ===========================BaseTag===================================*/

  public static abstract class BaseTag {
    protected final MotionSceneModel myMotionSceneModel;
    protected final String myTitle;

    public BaseTag(@NotNull MotionSceneModel model, @NotNull String title) {
      myMotionSceneModel = model;
      myTitle = title;
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    @NotNull
    public MotionSceneModel getModel() {
      return myMotionSceneModel;
    }

    public abstract XmlTag findMyTag();

    public boolean isAndroidAttribute(@NotNull String attributeName) {
      // TODO: This will ot work with namespaces
      // This method should be overridden if any of the possible attributes are framework attributes.
      return false;
    }

    protected void completeSceneModelUpdate() {
      // Temporary for LayoutLib:
      myMotionSceneModel.myNlModel.notifyModified(NlModel.ChangeType.EDIT);
      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      LayoutPullParsers.saveFileIfNecessary(xmlFile);
    }

    public boolean deleteTag(@NotNull String command) {
      XmlTag tag = findMyTag();
      if (tag == null) {
        return false;
      }
      Runnable operation = () -> {
        tag.delete();
      };
      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      completeSceneModelUpdate();
      return true;
    }

    public boolean setValues(@NotNull HashMap<String, String> values) {
      XmlTag tag = findMyTag();
      if (tag == null) {
        return false;
      }
      String command = "Set attributes";
      Runnable operation = () -> {
        for (String key : values.keySet()) {
          String value = values.get(key);
          String namespace = isAndroidAttribute(key) ? ANDROID_URI : AUTO_URI;
          tag.setAttribute(key, namespace, value);
        }
      };

      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      completeSceneModelUpdate();
      return true;
    }

    protected boolean setValue(@NotNull XmlTag tag, @NotNull String key, @NotNull String value) {
      String command = "Set " + key + " attribute";
      String namespace = isAndroidAttribute(key) ? ANDROID_URI : AUTO_URI;
      Runnable operation = () -> {
        tag.setAttribute(key, namespace, value);
      };

      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      completeSceneModelUpdate();
      return true;
    }

    public boolean setValue(@NotNull String key, @NotNull String value) {
      XmlTag tag = findMyTag();
      if (tag == null) {
        return false;
      }
      return setValue(tag, key, value);
    }

    @NotNull
    public abstract Set<String> getAttributeNames();

    @NotNull
    public List<CustomAttributes> getCustomAttributes() {
      return Collections.emptyList();
    }

    @Nullable
    public abstract String getValue(@NotNull String key);

    public boolean deleteAttribute(@NotNull String attributeName) {
      XmlTag tag = findMyTag();
      if (tag == null) {
        return false;
      }
      String command = "Delete " + attributeName + " attribute";
      String namespace = findAttributeNamespace(tag, attributeName);
      if (namespace == null) {
        return false;
      }
      Runnable operation = () -> {
        tag.setAttribute(attributeName, namespace, null);
      };

      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      completeSceneModelUpdate();
      return true;
    }

    @Nullable
    private static String findAttributeNamespace(@NotNull XmlTag tag, @NotNull String attributeName) {
      for (XmlAttribute attribute : tag.getAttributes()) {
        if (attributeName.equals(attribute.getLocalName())) {
          return attribute.getNamespace();
        }
      }
      return null;
    }
  }

  /* ===========================KeyFrame===================================*/

  public static abstract class KeyFrame extends BaseTag {
    protected String mType;

    int framePosition;
    String target;
    HashMap<String, Object> myAttributes = new HashMap<>();
    protected String[] myPossibleAttr;

    public KeyFrame(@NotNull MotionSceneModel motionSceneModel, @NotNull String title) {
      super(motionSceneModel, title);
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      switch (key) {
        case Key_framePosition:
          return String.valueOf(framePosition);
        case KeyCycle_target:
          return target;
        default:
          Object value = myAttributes.get(key);
          return value != null ? value.toString() : null;
      }
    }

    @Override
    @NotNull
    public Set<String> getAttributeNames() {
      return myAttributes.keySet();
    }

    public abstract String[] getDefault(String key);

    public CustomAttributes createCustomAttribute(@NotNull String key, @NotNull CustomAttributes.Type type, @NotNull String value) {
      // TODO: Do we need to support this for other tags than KeyAttributes ?
      throw new UnsupportedOperationException();
    }

    public String getString(String type) {
      if ("target".equals(type)) {
        return target;
      }
      return null;
    }

    public String[] getPossibleAttr() {
      return myPossibleAttr;
    }

    public int getFramePosition() { return framePosition; }

    public float getFloat(String type) {
      String val = myAttributes.get(type).toString();
      if (val.endsWith("dp")) { // TODO check for px etc.
        val = val.substring(0, val.length() - 2);
      }
      return Float.parseFloat(val);
    }

    public void fill(HashMap<String, Object> attributes) {
      attributes.put(Key_framePosition, framePosition);
      attributes.put(KeyCycle_target, target);
      attributes.putAll(myAttributes);
    }

    void parse(NamedNodeMap att) {
      int attCount = att.getLength();
      for (int i = 0; i < attCount; i++) {
        parse(att.item(i).getNodeName(), att.item(i).getNodeValue());
      }
    }

    float fparse(String v) {
      if (v.endsWith("dp")) {
        return Float.parseFloat(v.substring(0, v.length() - 2));
      }
      return Float.parseFloat(v);
    }

    void parse(String node, String value) {
      if (value == null) {
        myAttributes.remove(node);
        return;
      }
      if (node.endsWith(Key_framePosition)) {
        framePosition = Integer.parseInt(value);
      }
      else if (node.endsWith(KeyAttributes_target)) {
        target = value.substring(value.indexOf('/') + 1);
      }
      else {
        myAttributes.put(node, value);
      }
    }

    private String trim(String node) {
      return node.substring(node.indexOf(':') + 1);
    }

    public void parse(XmlAttribute[] attributes) {
      for (int i = 0; i < attributes.length; i++) {
        XmlAttribute attribute = attributes[i];
        parse(trim(attribute.getName()), attribute.getValue());
      }
    }

    /**
     * Find the {@link XmlTag} corresponding to this {@link KeyFrame} type.
     */
    @Nullable
    @Override
    public XmlTag findMyTag() {
      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      XmlTag root = xmlFile != null ? xmlFile.getRootTag() : null;
      if (root == null) {
        return null;
      }
      XmlTag[] keyFrames = root.findSubTags(MotionSceneKeyFrameSet);
      if (keyFrames.length == 0) {
        return null;
      }
      XmlTag[] keyFrame = keyFrames[0].getSubTags();
      for (XmlTag tag : keyFrame) {
        if (match(tag)) {
          return tag;
        }
      }
      return null;
    }

    /**
     * Delete an attribute from a KeyFrame.
     */
    @Override
    public boolean deleteAttribute(@NotNull String attributeName) {
      // Never delete these required attributes:
      if (attributeName.equals(KeyAttributes_target) ||
          attributeName.equals(KeyAttributes_framePosition)) {
        // TODO: Find out why these are called in the first place...
        return false;
      }
      if (!super.deleteAttribute(attributeName)) {
        return false;
      }
      myAttributes.remove(attributeName);
      return true;
    }

    public boolean deleteTag() {
      String command = "Delete key attributes for: " + target;
      return super.deleteTag(command);
    }

    /**
     * Set the value of a KeyFrame attribute.
     */
    @Override
    public boolean setValue(@NotNull String key, @NotNull String value) {
      if (!super.setValue(key, value)) {
        return false;
      }
      parse(key, value);
      return true;
    }

    /**
     * Set multiple values of a Keyframe in one go
     */
    @Override
    public boolean setValues(@NotNull HashMap<String, String> values) {
      if (!super.setValues(values)) {
        return false;
      }
      for (String key : values.keySet()) {
        parse(key, values.get(key));
      }
      return true;
    }

    /**
     * @param xmlTag
     * @return
     */
    boolean match(XmlTag xmlTag) {
      String keyNodeName = xmlTag.getName();

      if (!keyNodeName.equals(mType)) return false;
      XmlAttribute[] attr = xmlTag.getAttributes();
      for (int k = 0; k < attr.length; k++) {
        XmlAttribute attribute = attr[k];
        if (attribute.getName().endsWith("framePosition")) {
          if (Integer.parseInt(attribute.getValue()) != framePosition) {
            return false;
          }
        }
        if (attribute.getName().endsWith("target")) {
          if (!attribute.getValue().endsWith(target)) {
            return false;
          }
        }
      }

      return true;
    }

    public String getName() {
      return mType;
    }

    public String getEasingCurve() {
      return (String)myAttributes.get(KeyPosition_transitionEasing);
    }
  }

  /* ============================KeyPos==================================*/

  public static abstract class KeyPos extends KeyFrame {
    String transitionEasing = null;

    public KeyPos(MotionSceneModel motionSceneModel) { super(motionSceneModel, KeyPositionTitle); }

    @Override
    public float getFloat(String type) {
      return super.getFloat(type);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(KeyPosition_transitionEasing)) {
        transitionEasing = value;
      }
      super.parse(node, value);
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      if (transitionEasing != null) {
        attributes.put(Key_framePosition, (Integer)framePosition);
      }
      super.fill(attributes);
    }
  }

  /* ==========================KeyPosition====================================*/
  public static class KeyPosition extends KeyPos {
    static  final String TYPE = KeyTypePosition;
    float percentX = Float.NaN;
    float percentY = Float.NaN;
    public static String[] ourPossibleAttr = {
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "drawPath",
      "sizePercent",
      "sizePercent",
      "percentY",
      "percentX",
      "type"
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"cubic(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"true", "false"},
      {"0.5"},
      {"0.0"},
      {"0.5"}
    };


    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    public KeyPosition(MotionSceneModel motionSceneModel) {
      super(motionSceneModel);
      myPossibleAttr = ourPossibleAttr;
      mType = KeyTypePosition;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      if (!Float.isNaN(percentX)) {
        attributes.put(KeyPositionPath_path_percent, (Float)percentX);
      }
      if (!Float.isNaN(percentY)) {
        attributes.put(KeyPositionPath_perpendicularPath_percent, (Float)percentY);
      }
      super.fill(attributes);
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      switch (key) {
        case KeyPositionPath_path_percent:
          return String.valueOf(percentX);
        case KeyPositionPath_perpendicularPath_percent:
          return String.valueOf(percentY);
        default:
          return super.getValue(key);
      }
    }

    @Override
    public float getFloat(String type) {
      if ("perpendicularPath_percent".equals(type)) {
        return percentY;
      }
      if ("path_percent".equals(type)) {
        return percentY;
      }
      return super.getFloat(type);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(KeyPositionPath_path_percent)) {
        percentX = Float.parseFloat(value);
      }
      else if (node.endsWith(KeyPositionPath_perpendicularPath_percent)) {
        percentY = Float.parseFloat(value);
      }
      else if (node.endsWith(KeyPosition_framePosition) || node.endsWith("")) {
        super.parse(node, value);
      }
    }
  }


  /* ===========================KeyAttributes===================================*/
  public static class KeyAttributes extends KeyFrame {
    static  final String TYPE = KeyTypeAttribute;
    String curveFit = null;
    ArrayList<CustomAttributes> myCustomAttributes = new ArrayList<>();
    public static String[] ourPossibleAttr = {
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "sizePercent",
      "progress",
      "orientation",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "transitionPathRotate",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ",
      CustomLabel
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"curve=(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"0.5"},
      {"0.5"},
      {"90"},
      {"0.5"},
      {"5dp"},
      {"45"},
      {"10"},
      {"10"},
      {"90"},
      {"1.5"},
      {"1.5"},
      {"10dp"},
      {"10dp"},
      {"10dp"},
    };

    public static String[] ourPossibleStandardAttr = {
      "orientation",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ"
    };
    HashSet<String> myAndroidAttributes = null;

    @Override
    @NotNull
    public List<CustomAttributes> getCustomAttributes() {
      return myCustomAttributes;
    }

    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    @Override
    public boolean isAndroidAttribute(@NotNull String attributeName) {
      if (myAndroidAttributes == null) {
        myAndroidAttributes = new HashSet<>(Arrays.asList(ourPossibleStandardAttr));
      }
      return myAndroidAttributes.contains(attributeName);
    }

    public KeyAttributes(MotionSceneModel motionSceneModel) {
      super(motionSceneModel, KeyAttributesTitle);
      mType = KeyTypeAttribute;
      myPossibleAttr = ourPossibleAttr;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      attributes.putAll(myAttributes);
      if (curveFit != null) {
        attributes.put(KeyAttributes_curveFit, curveFit);
      }
      super.fill(attributes);
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      switch (key) {
        case KeyAttributes_curveFit:
          return curveFit;
        default:
          return super.getValue(key);
      }
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(KeyAttributes_curveFit)) {
        curveFit = value;
      }
      else if (ourStandardSet.contains(node)) {
        myAttributes.put(node, value);
      }
      else if (node.endsWith(KeyAttributes_framePosition) || node.endsWith("")) {
        super.parse(node, value);
      }
      else {
        myAttributes.put(node, new Float(value));
      }
    }

    @Override
    @Nullable
    public CustomAttributes createCustomAttribute(@NotNull String key, @NotNull CustomAttributes.Type type, @NotNull String value) {
      XmlTag keyFrame = findMyTag();
      if (keyFrame == null) {
        return null;
      }
      List<CustomAttributes> existing =
        myCustomAttributes.stream().filter(attr -> key.equals(attr.getAttributeName())).collect(Collectors.toList());
      for (CustomAttributes attr : existing) {
        attr.deleteTag();
      }
      Computable<CustomAttributes> operation = () -> {
        XmlTag createdTag = keyFrame.createChildTag(KeyAttributes_customAttribute, null, null, false);
        createdTag = keyFrame.addSubTag(createdTag, false);
        createdTag.setAttribute(CustomAttributes_attributeName, AUTO_URI, key);
        createdTag.setAttribute(type.getTagName(), AUTO_URI, StringUtil.isNotEmpty(value) ? value : type.getDefaultValue());
        CustomAttributes custom = new CustomAttributes(this);
        Arrays.stream(createdTag.getAttributes()).forEach(attr -> custom.parse(attr.getLocalName(), attr.getValue()));
        myCustomAttributes.add(custom);
        return custom;
      };
      CustomAttributes newAttribute = WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, operation);
      completeSceneModelUpdate();
      return newAttribute;
    }
  }

  /* ===========================KeyCycle===================================*/
  public static class KeyCycle extends KeyFrame {
    static  final String TYPE = KeyTypeCycle;
    float waveOffset = Float.NaN;
    float wavePeriod = Float.NaN;
    ArrayList<CustomCycleAttributes> myCustomAttributes = new ArrayList<>();
    String waveShape;
    public static String[] ourPossibleAttr = {
      "target",
      "framePosition",
      "transitionEasing",
      "curveFit",
      "progress",
      "waveShape",
      "wavePeriod",
      "waveOffset",
      //TODO "waveVariesBy",
      "transitionPathRotate",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ"
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"curve=(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"0.5"},
      {"sin", "square", "triangle", "sawtooth", "reverseSawtooth", "cos", "bounce"},
      {"1"},
      {"0"},
      {"90"},
      {"0.5"},
      {"20dp"},
      {"45"},
      {"10"},
      {"10"},
      {"1.5"},
      {"1.5"},
      {"20dp"},
      {"20dp"},
      {"20dp"},
    };

    public static String[] ourPossibleStandardAttr = {
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ"
    };

    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    HashSet<String> myAndroidAttributes = null;

    @Override
    public String toString() {
      return getName() + Arrays.toString(myAttributes.keySet().toArray());
    }

    @Override
    public boolean isAndroidAttribute(@NotNull String attributeName) {
      if (myAndroidAttributes == null) {
        myAndroidAttributes = new HashSet<>(Arrays.asList(ourPossibleStandardAttr));
      }
      return myAndroidAttributes.contains(attributeName);
    }

    public KeyCycle(MotionSceneModel motionSceneModel) {
      super(motionSceneModel, KeyCycleTitle);
      super.mType = KeyTypeCycle;
      myPossibleAttr = ourPossibleAttr;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      attributes.putAll(myAttributes);
      if (!Float.isNaN(waveOffset)) {
        attributes.put(KeyCycle_waveOffset, waveOffset);
      }
      if (!Float.isNaN(wavePeriod)) {
        attributes.put(KeyCycle_wavePeriod, wavePeriod);
      }
      if (waveShape != null) {
        attributes.put(KeyCycle_waveOffset, waveShape);
      }
      super.fill(attributes);
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      switch (key) {
        case KeyCycle_waveOffset:
          return String.valueOf(waveOffset);
        case KeyCycle_wavePeriod:
          return String.valueOf(wavePeriod);
        case KeyCycle_waveShape:
          return String.valueOf(waveShape);
        default:
          return super.getValue(key);
      }
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(KeyCycle_waveOffset)) {
        waveOffset = fparse(value);
      }
      else if (node.endsWith(KeyCycle_wavePeriod)) {
        wavePeriod = Float.parseFloat(value);
      }
      else if (node.endsWith(KeyCycle_waveShape)) {
        waveShape = value;
      }
      super.parse(node, value);
    }
  }

 static class KeyTimeCycle extends KeyCycle {
   static  final String TYPE = KeyTypeTimeCycle;
   public KeyTimeCycle(MotionSceneModel motionSceneModel) {
     super(motionSceneModel);
   }
 }

  /* ===========================CustomCycleAttributes===================================*/

  public static class CustomCycleAttributes implements AttributeParse {
    KeyCycle parentKeyCycle;
    HashMap<String, Object> myAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "attributeName",
      "customIntegerValue",
      "customFloatValue",
      "customDimension",
    };
    public static String[][] ourDefaults = {
      {},
      {"1"},
      {"1.0"},
      {"20dp"}
    };

    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    public CustomCycleAttributes(KeyCycle frame) {
      parentKeyCycle = frame;
    }

    @Override
    public void parse(String name, String value) {
      myAttributes.put(name, value);
    }
  }

  /* ===========================CustomAttributes===================================*/

  public static class CustomAttributes extends BaseTag implements AttributeParse {
    private final KeyAttributes parentKeyAttributes;
    private final HashMap<String, Object> myAttributes = new HashMap<>();

    @Nullable
    public String getAttributeName() {
      return (String)myAttributes.get(CustomAttributes_attributeName);
    }

    @Nullable
    public String getValueTagName() {
      return Arrays.stream(Type.values()).map(key -> key.getTagName())
                   .filter(tag -> myAttributes.containsKey(tag))
                   .findFirst()
                   .orElse(null);
    }

    @Override
    public String toString() {
      return Arrays.toString(myAttributes.keySet().toArray());
    }

    public boolean deleteTag() {
      if (!super.deleteTag("Remove custom attribute")) {
        return false;
      }
      parentKeyAttributes.getCustomAttributes().remove(this);
      return true;
    }

    @Override
    @NotNull
    public Set<String> getAttributeNames() {
      return Collections.singleton(getAttributeName());
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      Object value = myAttributes.get(key);
      return value != null ? value.toString() : null;
    }

    @Override
    public boolean setValue(@NotNull String key, @NotNull String value) {
      if (!super.setValue(key, value)) {
        return false;
      }
      parse(key, value);
      return true;
    }

    @Override
    public boolean deleteAttribute(@NotNull String attributeName) {
      throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public XmlTag findMyTag() {
      String attributeName = (String)myAttributes.get(CustomAttributes_attributeName);
      if (StringUtil.isEmpty(attributeName)) {
        return null;
      }
      XmlTag parent = parentKeyAttributes.findMyTag();
      if (parent == null) {
        return null;
      }
      XmlTag[] customAttrs = parent.findSubTags(KeyAttributes_customAttribute);
      for (XmlTag tag : customAttrs) {
        if (attributeName.equals(tag.getAttributeValue(CustomAttributes_attributeName, AUTO_URI))) {
          return tag;
        }
      }
      return null;
    }

    public enum Type {
      CUSTOM_COLOR("Color", CustomAttributes_customColorValue, "#FFF"),
      CUSTOM_INTEGER("Integer", CustomAttributes_customIntegerValue, "2"),
      CUSTOM_FLOAT("Float", CustomAttributes_customFloatValue, "1.0"),
      CUSTOM_STRING("String", CustomAttributes_customStringValue, "Example"),
      CUSTOM_DIMENSION("Dimension", CustomAttributes_customDimensionValue, "20dp"),
      CUSTOM_BOOLEAN("Boolean", CustomAttributes_customBooleanValue, "true");

      private final String myStringValue;
      private final String myTagName;
      private final String myDefaultValue;

      @NotNull
      public String getTagName() {
        return myTagName;
      }

      @NotNull
      public String getDefaultValue() {
        return myDefaultValue;
      }

      @Override
      public String toString() {
        return myStringValue;
      }

      Type(@NotNull String stringValue, @NotNull String tagName, @NotNull String defaultValue) {
        myStringValue = stringValue;
        myTagName = tagName;
        myDefaultValue = defaultValue;
      }
    }

    public CustomAttributes(@NotNull KeyAttributes frame) {
      super(frame.getModel(), "");
      parentKeyAttributes = frame;
    }

    public HashMap<String, Object> getAttributes() {
      return myAttributes;
    }

    @Override
    public void parse(String name, String value) {
      myAttributes.put(name.substring(name.lastIndexOf(':') + 1), value);
    }
  }

  // =================================AttributeParse====================================== //

  interface AttributeParse {
    void parse(String name, String value);
  }

  // =================================ConstraintSet====================================== //

  public static class ConstraintSet implements AttributeParse {
    String mId;

    public String getId() {
      return mId;
    }

    void setId(String id) {
      mId = id.substring(id.indexOf('/') + 1);
    }

    HashMap<String, ConstraintView> myConstraintViews = new HashMap<>();

    @Override
    public void parse(String name, String value) {
      if ("android:id".equals(name)) {
        mId = value;
      }
    }
  }

  // =================================TransitionTag====================================== //
  public static class TransitionTag extends BaseTag implements AttributeParse {
    private final String[] myPossibleAttr;
    String myConstraintSetEnd;
    String myConstraintSetStart;
    OnSwipeTag myOnSwipeTag = null;
    int duration;
    public ArrayList<KeyPos> myKeyPositions = new ArrayList<>();
    public ArrayList<KeyAttributes> myKeyAttributes = new ArrayList<>();
    public ArrayList<KeyCycle> myKeyCycles = new ArrayList<>();

    HashMap<String, Object> myAllAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "constraintSetStart",
      "constraintSetEnd",
      "duration",
      "staggered"
    };

    void addKeyFrame(MotionSceneModel model, HashMap<String, MotionSceneView>  viewsMap, KeyFrame frame) {
      String id = frame.target;
      MotionSceneView motionView = viewsMap.get(id);
      if (motionView == null) {
        motionView = new MotionSceneView();
        motionView.myModel = model;
        motionView.mid = id;
        viewsMap.put(id, motionView);
      }
      if (frame instanceof KeyAttributes) {
        motionView.myKeyAttributes.add((KeyAttributes)frame);
      }
      else if (frame instanceof KeyPos) {
        motionView.myKeyPositions.add((KeyPos)frame);
      }
      else if (frame instanceof KeyCycle) {
        motionView.myKeyCycles.add((KeyCycle)frame);
      }
    }

    public String[] getPossibleAttr() {
      return myPossibleAttr;
    }

    public HashMap<String, Object> getAttributes() {
      return myAllAttributes;
    }

    TransitionTag(MotionSceneModel model) {
      super(model, TransitionTitle);
      myPossibleAttr = ourPossibleAttr;
    }

    public ConstraintSet getConstraintSetEnd() {
      if (myConstraintSetEnd == null) {
        return null;
      }
      for (ConstraintSet set : myMotionSceneModel.myConstraintSets) {
        if (myConstraintSetEnd.equals(set.mId)) {
          return set;
        }
      }
      return null;
    }

    public ConstraintSet getConstraintSetStart() {
      if (myConstraintSetStart == null) {
        return null;
      }
      for (ConstraintSet set : myMotionSceneModel.myConstraintSets) {
        if (myConstraintSetStart.equals(set.mId)) {
          return set;
        }
      }
      return null;
    }

    @Override
    public void parse(String name, String value) {
      name = name.substring(name.lastIndexOf(':') + 1);
      myAllAttributes.put(name, value);
      if (name.endsWith(TransitionConstraintSetEnd)) {
        myConstraintSetEnd = value;
      }
      else if (name.endsWith(TransitionConstraintSetStart)) {
        myConstraintSetStart = value;
      }
      else if (name.endsWith(TransitionDuration)) {
        duration = Integer.parseInt(value);
      }
    }

    @Override
    @Nullable
    public XmlTag findMyTag() {
      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      XmlTag root = xmlFile != null ? xmlFile.getRootTag() : null;
      if (root == null) {
        return null;
      }
      XmlTag[] onSwipes = root.findSubTags(MotionSceneTransition);
      if (onSwipes.length == 0) {
        return null;
      }
      return onSwipes[0];
    }

    @Override
    public boolean deleteTag(@NotNull String command) {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public Set<String> getAttributeNames() {
      return myAllAttributes.keySet();
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      Object value = myAllAttributes.get(key);
      return value != null ? value.toString() : null;
    }

    @Override
    public boolean setValue(@NotNull String key, @NotNull String value) {
      if (!super.setValue(key, value)) {
        return false;
      }
      parse(key, value);
      return true;
    }

    @Override
    public boolean deleteAttribute(@NotNull String attributeName) {
      if (!super.deleteAttribute(attributeName)) {
        return false;
      }
      myAllAttributes.remove(attributeName);
      return true;
    }
  }

  // =================================OnSwipe====================================== //

  public static class OnSwipeTag extends BaseTag implements AttributeParse {
    HashMap<String, Object> myAllAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "maxVelocity",
      "maxAcceleration",
      "touchSide",
      "touchAnchorId",
      "touchAnchorSide"
    };
    public String[] myPossibleAttr = ourPossibleAttr;

    public String[] getPossibleAttr() {
      return myPossibleAttr;
    }

    public HashMap<String, Object> getAttributes() {
      return myAllAttributes;
    }

    OnSwipeTag(@NotNull MotionSceneModel model) {
      super(model, OnSwipeTitle);
      myPossibleAttr = ourPossibleAttr;
    }

    @Nullable
    @Override
    public XmlTag findMyTag() {
      XmlFile xmlFile = myMotionSceneModel.motionSceneFile();
      XmlTag root = xmlFile != null ? xmlFile.getRootTag() : null;
      if (root == null) {
        return null;
      }
      XmlTag[] onSwipes = root.findSubTags(MotionSceneOnSwipe);
      if (onSwipes.length == 0) {
        return null;
      }
      return onSwipes[0];
    }

    @Override
    public void parse(String name, String value) {
      if (DEBUG) {
        System.out.println("====================================================================================");
        System.out.println("parse ("+name+"  ,  "+value+" )");
      }
      name = name.substring(name.lastIndexOf(':') + 1);
      myAllAttributes.put(name, value);
    }

    @Override
    @Nullable
    public String getValue(@NotNull String key) {
      Object value = myAllAttributes.get(key);
      return value != null ? value.toString() : null;
    }

    @Override
    @NotNull
    public Set<String> getAttributeNames() {
      return myAllAttributes.keySet();
    }

    @Override
    public boolean deleteAttribute(@NotNull String attributeName) {
      if (!super.deleteAttribute(attributeName)) {
        return false;
      }
      myAllAttributes.remove(attributeName);
      return true;
    }

    public boolean deleteTag() {
      return deleteTag("Delete OnSwing");
    }
  }

  // =================================ConstraintView====================================== //

  static class ConstraintView implements AttributeParse {
    String mId;
    HashMap<String, Object> myAllAttributes = new HashMap<>();

    void setId(String id) {
      mId = id.substring(id.indexOf('/') + 1);
    }

    HashMap<String, String> myConstraintViews = new HashMap<>();

    @Override
    public void parse(String name, String value) {
      myAllAttributes.put(name, value);
    }
  }

  private static void parse(AttributeParse a, XmlAttribute[] attributes) {
    if (DEBUG) {
      System.out.println("====================================================================================");
      System.out.println(" parse(AttributeParse a, XmlAttribute[] attributes)");
    }
    for (int i = 0; i < attributes.length; i++) {
      XmlAttribute attribute = attributes[i];
      parse(a, attribute.getName(), attribute.getValue());
    }
  }

  private static void parse(AttributeParse a, String name, String value) {
    a.parse(name, value);
  }

  /**
   * Entry point to build the model
   *
   * @param model
   * @param project
   * @param virtualFile
   * @param file
   * @return
   */
  public static MotionSceneModel parse(NlModel model,
                                       Project project,
                                       VirtualFile virtualFile,
                                       XmlFile file) {
    MotionSceneModel motionSceneModel = new MotionSceneModel();
    motionSceneModel.myName = virtualFile.getName();
    ArrayList<ConstraintSet> constraintSet = new ArrayList<>();
    if (DEBUG) {
      System.out.println("====================================================================================");
      System.out.println(" parse ... VirtualFile "+virtualFile.getCanonicalPath());
    }
    motionSceneModel.myNlModel = model;
    motionSceneModel.myVirtualFile = virtualFile;
    motionSceneModel.myProject = project;

    // Process all the constraint sets
    XmlTag[] tagKeyFrames = file.getRootTag().findSubTags(MotionSceneConstraintSet);
    for (int i = 0; i < tagKeyFrames.length; i++) {
      XmlTag frame = tagKeyFrames[i];
      ConstraintSet set = new ConstraintSet();
      set.setId(frame.getAttributeValue("android:id"));
      parse(set, frame.getAttributes());
      constraintSet.add(set);
      XmlTag[] subTags = frame.getSubTags();
      for (int j = 0; j < subTags.length; j++) {
        XmlTag subtag = subTags[j];
        if (ConstraintSetConstraint.equals(subtag.getName())) {
          ConstraintView view = new ConstraintView();
          view.setId(subtag.getAttributeValue("android:id"));
          motionSceneModel.addKeyFrame(view.mId);
          parse(view, subtag.getAttributes());
          set.myConstraintViews.put(view.mId, view);
        }
      }
    }
    motionSceneModel.myConstraintSets = constraintSet;

    // process the Transition

    XmlTag[] transitionTags = file.getRootTag().findSubTags(MotionSceneTransition);
    if (transitionTags.length > 0) {
      motionSceneModel.myTransition = new ArrayList<>();
    }
    for (XmlTag transitionTag : transitionTags) {
      TransitionTag transition = new TransitionTag(motionSceneModel);
      XmlTag tag = transitionTag;
      parse(transition, tag.getAttributes());
      motionSceneModel.myTransition.add(transition);

      XmlTag[] onSwipeTags = tag.findSubTags(MotionSceneOnSwipe);
      if (onSwipeTags.length > 1) {
        System.err.println("Should only have one tag");
      }
      // TODO add onClick
      for (XmlTag onSwipeTag1 : onSwipeTags) {
        OnSwipeTag onSwipeTag = new OnSwipeTag(motionSceneModel);
        transition.myOnSwipeTag = onSwipeTag;
        XmlTag swipeTag = onSwipeTag1;
        parse(onSwipeTag, swipeTag.getAttributes());
        motionSceneModel.myOnSwipeTag = onSwipeTag; // Todo should really be contained in the transition
      }

      // process all the key frames
      tagKeyFrames = tag.findSubTags(MotionSceneKeyFrameSet);

      for (XmlTag tagKeyFrame : tagKeyFrames) {
        XmlTag[] tagkey = tagKeyFrame.getSubTags();

        for (XmlTag xmlTag : tagkey) {
          XmlTag[] customTags = xmlTag.getSubTags();
          String keyNodeName = xmlTag.getName();

          KeyFrame frame = null;
          switch (keyNodeName) {
            case KeyPosition.TYPE:
              frame = new KeyPosition(motionSceneModel);
              break;
            case KeyAttributes.TYPE:
              frame = new KeyAttributes(motionSceneModel);
              for (XmlTag ctag : customTags) {
                CustomAttributes custom = new CustomAttributes((KeyAttributes)frame);
                parse(custom, ctag.getAttributes());
                ((KeyAttributes)frame).myCustomAttributes.add(custom);
              }
              break;
            case KeyCycle.TYPE:
              frame = new KeyCycle(motionSceneModel);
              for (XmlTag ctag : customTags) {
                CustomCycleAttributes custom = new CustomCycleAttributes((KeyCycle)frame);
                parse(custom, ctag.getAttributes());
                ((KeyCycle)frame).myCustomAttributes.add(custom);
              }
              break;
            case KeyTimeCycle.TYPE:
              frame = new KeyTimeCycle(motionSceneModel);
              for (XmlTag ctag : customTags) {
                CustomCycleAttributes custom = new CustomCycleAttributes((KeyTimeCycle)frame);
                parse(custom, ctag.getAttributes());
                ((KeyTimeCycle)frame).myCustomAttributes.add(custom);
              }
              break;

            default:
              System.err.println("Unknown name :" + keyNodeName);

          }
          if (frame != null) {
            frame.parse(xmlTag.getAttributes());
            motionSceneModel.addKeyFrame(frame);
          }
          transition.addKeyFrame(motionSceneModel,motionSceneModel.mySceneViews,frame);
        }
      }
    }
    // process the OnSwipe

    return motionSceneModel;
  }

  public static ArrayList<MotionSceneModel.KeyFrame> filterList(ArrayList<? extends MotionSceneModel.KeyFrame> keyList,
                                                                String name) {
    ArrayList<MotionSceneModel.KeyFrame> ret = new ArrayList<>();
    for (KeyFrame keyFrame : keyList) {
      if (keyFrame.myAttributes.containsKey(name)) {
        ret.add(keyFrame);
      }
    }
    return ret;
  }

  public static String[] getGraphAttributes(ArrayList<? extends MotionSceneModel.KeyFrame> keyList) {
    HashSet<String> set = new HashSet<>();
    for (KeyFrame frame : keyList) {
      if (frame instanceof KeyAttributes) {
        set.addAll(frame.myAttributes.keySet());
      }
      else if (frame instanceof KeyCycle) {
        set.addAll(frame.myAttributes.keySet());
      }
    }
    return set.toArray(new String[set.size()]);
  }

  void addKeyFrame(String id) {
    MotionSceneView motionView = mySceneViews.get(id);
    if (motionView == null) {
      motionView = new MotionSceneView();
      motionView.myModel = this;
      motionView.mid = id;
      mySceneViews.put(id, motionView);
    }
  }

  void addKeyFrame(KeyFrame frame) {
    String id = frame.target;
    MotionSceneView motionView = mySceneViews.get(id);
    if (motionView == null) {
      motionView = new MotionSceneView();
      motionView.myModel = this;
      motionView.mid = id;
      mySceneViews.put(id, motionView);
    }
    if (frame instanceof KeyAttributes) {
      motionView.myKeyAttributes.add((KeyAttributes)frame);
    }
    else if (frame instanceof KeyPos) {
      motionView.myKeyPositions.add((KeyPos)frame);
    }
    else if (frame instanceof KeyCycle) {
      motionView.myKeyCycles.add((KeyCycle)frame);
    }
  }
}
