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
package com.android.tools.idea.assistant;

import com.android.tools.idea.assistant.datamodel.*;
import com.android.tools.idea.templates.recipe.Recipe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * JAXB enabled POJO representing "assistant" content organized into features and tutorials.
 * This is the default xml mapping to {@code TutorialBundleData} and it may be extended or used
 * as a pattern if developers would like to augment the bundle with data structures specific to their
 * assistant.
 */
@XmlRootElement(name = "tutorialBundle")
public class DefaultTutorialBundle implements TutorialBundleData {

  @XmlAttribute(name = "name")
  private String myName;

  @XmlAttribute(name = "icon")
  @Nullable
  private String myIconName;

  @XmlAttribute(name = "logo")
  @Nullable
  private String myLogoName;

  @XmlAttribute(name = "id")
  private String myId;

  @XmlAttribute(name = "delete")
  private int myDeleteme;

  @XmlAttribute(name = "contentRoot")
  private String myContentRoot;

  @XmlAttribute(name = "resourceRoot")
  private String myResourceRoot;

  @XmlElement(name = "welcome")
  private String myWelcome;

  @XmlElement(name = "learnMoreLink")
  private String myLearnMoreLink;

  @XmlAttribute(name = "stepByStep")
  private boolean myStepByStep;

  private Icon myIcon;

  private Icon myLogo;

  private Class myResourceClass;

  @XmlElements({
    @XmlElement(name = "feature", type = Feature.class)
  })
  private List<Feature> myFeatures = Lists.newArrayList();


  public DefaultTutorialBundle() {
  }

  /**
   * Parse a bundle XML.
   */
  public static TutorialBundleData parse(@NotNull InputStream stream) throws JAXBException {
    return parse(stream, DefaultTutorialBundle.class);
  }

  /**
   * Parse a bundle XML with a custom class. Used when extending this class.
   */
  public static <T> T parse(@NotNull InputStream stream, Class<T> clazz) throws JAXBException {
    Unmarshaller unmarshaller = JAXBContext.newInstance(clazz).createUnmarshaller();
    unmarshaller.setEventHandler(event -> {
      getLog().info("Found unhandled xml", event.getLinkedException());
      return true;
    });

    return unmarshaller.unmarshal(new StreamSource(stream), clazz).getValue();
  }

  private static Logger getLog() {
    return Logger.getInstance(TutorialBundleData.class);
  }

  /**
   * As this is only called after the bundle has been instantiated, all tutorial instances
   * should already be created and may be manipulated safely.
   */
  @Override
  public void setResourceClass(@NotNull Class clazz) {
    myResourceClass = clazz;
    myFeatures.forEach((feature) -> feature.getTutorials().forEach((tutorial) -> {
      tutorial.setResourceClass(clazz);
    }));
  }

  /**
   * Gets an icon for the given path, null when resource not found.
   */
  @Nullable
  private Icon getIconResource(@NotNull String path) {
    return IconLoader.findIcon(path, myResourceClass);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public boolean isStepByStep() {
    return myStepByStep;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    if (myIcon != null) {
      return myIcon;
    }
    if (myIconName == null || myResourceRoot == null) {
      return null;
    }
    myIcon = getIconResource(myResourceRoot + "/" + myIconName);
    return myIcon;
  }

  @Nullable
  @Override
  public Icon getLogo() {
    if (myLogo != null) {
      return myLogo;
    }
    if (myLogoName == null || myResourceRoot == null) {
      return null;
    }
    myLogo = getIconResource(myResourceRoot + "/" + myLogoName);
    return myLogo;
  }

  @NotNull
  @Override
  public List<Feature> getFeatures() {
    return ImmutableList.copyOf(myFeatures);
  }

  @NotNull
  @Override
  public String getWelcome() {
    return myWelcome;
  }

  @SuppressWarnings("unused")
  private void afterUnmarshal(Unmarshaller u, Object parent) {
    myFeatures.forEach((feature) -> feature.getTutorials().forEach((tutorial) -> {
      tutorial.myResourceRoot = myResourceRoot;
    }));
  }

  @Override
  public String toString() {
    return "TutorialBundle{" +
           "myName='" + myName + '\'' +
           ", myIcon='" + myIcon + '\'' +
           ", myLogo='" + myLogo + '\'' +
           ", myId='" + myId + '\'' +
           ", myContentRoot='" + myContentRoot + '\'' +
           ", myResourceRoot='" + myResourceRoot + '\'' +
           ", myWelcome='" + myWelcome + '\'' +
           ", myLearnMoreLink='" + myLearnMoreLink + '\'' +
           ", myFeatures=" + myFeatures +
           '}';
  }

  /**
   * Individual feature configuration declaration that represents a single
   * feature that will be created within a {@code TutorialBundle}.
   */
  @SuppressWarnings({"NullableProblems"})
  public static final class Feature implements FeatureData {

    @XmlAttribute(name = "name")
    @NotNull
    @SuppressWarnings("unused")
    private String myName;

    @XmlAttribute(name = "icon")
    @Nullable
    @SuppressWarnings("unused")
    private String myIcon;

    @Nullable
    private Icon myIconInstance;

    @XmlAttribute(name = "resourceRoot")
    @NotNull
    @SuppressWarnings("unused")
    private String myResourceRoot;

    @XmlElements({
      @XmlElement(name = "resource", type = String.class)
    })
    private List<String> myResources = Lists.newArrayList();

    @XmlElement(name = "description")
    private String myDescription;

    @XmlElements({
      @XmlElement(name = "tutorial", type = Tutorial.class)
    })
    private List<Tutorial> myTutorials = Lists.newArrayList();

    private Class myResourceClass;

    @Override
    public void setResourceClass(@NotNull Class clazz) {
      myResourceClass = clazz;
    }

    @Override
    @NotNull
    public String getName() {
      return myName;
    }

    @Nullable
    private Icon getIconResource(@NotNull String path) {
      return IconLoader.findIcon(path, myResourceClass);
    }

    @Override
    @Nullable
    public Icon getIcon() {
      if (myIconInstance != null) {
        return myIconInstance;
      }
      if (myIcon == null) {
        return null;
      }
      myIconInstance = getIconResource(myResourceRoot + "/" + myIcon);
      return myIconInstance;
    }

    @NotNull
    @Override
    public String getDescription() {
      return myDescription;
    }

    @NotNull
    @Override
    public List<Tutorial> getTutorials() {
      return ImmutableList.copyOf(myTutorials);
    }

    @Override
    public String toString() {
      return "Feature{" +
             "myName='" + myName + "'" +
             ", myResourceRoot='" + myResourceRoot + "'" +
             ", myResources=" + myResources +
             '}';
    }
  }

  public static class Tutorial implements TutorialData {

    // NOTE: Intended both as a title of the tutorial view as well as the label
    // to navigate to the view.
    @XmlAttribute(name = "label")
    private String myLabel;

    @XmlElement(name = "description")
    private String myDescription;

    @XmlAttribute(name = "remoteLink")
    private String myRemoteLink;

    @XmlAttribute(name = "remoteLinkLabel")
    private String myRemoteLinkLabel;

    // Used for retrieval of view programatically. Navigating from one tutorial
    // to another will need to leverage this value.
    @XmlAttribute(name = "key")
    private String myKey;

    @XmlAttribute(name = "icon")
    @Nullable
    private String myIconName;

    protected String myResourceRoot;

    @XmlElements({
      @XmlElement(name = "step", type = Step.class)
    })
    private List<Step> mySteps = Lists.newArrayList();

    private Icon myIcon;

    private Class myResourceClass;

    public void setResourceClass(@NotNull Class clazz) {
      myResourceClass = clazz;
    }

    @Override
    @NotNull
    public String getLabel() {
      return myLabel;
    }

    @Override
    @Nullable
    public String getDescription() {
      return myDescription;
    }

    @Override
    @NotNull
    public String getRemoteLink() {
      return myRemoteLink;
    }

    @Override
    @NotNull
    public String getRemoteLinkLabel() {
      return myRemoteLinkLabel;
    }

    @Override
    @NotNull
    public String getKey() {
      return myKey;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      if (myIcon != null) {
        return myIcon;
      }
      if (myIconName == null || myResourceRoot == null) {
        return null;
      }

      myIcon =  IconLoader.findIcon(myResourceRoot + "/" + myIconName, myResourceClass);
      return myIcon;
    }

    @Override
    @NotNull
    public List<Step> getSteps() {
      return ImmutableList.copyOf(mySteps);
    }

    @Override
    public String toString() {
      return "Tutorial{" +
             "myLabel='" + myLabel + '\'' +
             ", myDescription='" + myDescription + '\'' +
             ", myRemoteLink='" + myRemoteLink + '\'' +
             ", myRemoteLinkLabel='" + myRemoteLinkLabel + '\'' +
             ", myKey='" + myKey + '\'' +
             ", mySteps=" + mySteps +
             '}';
    }
  }

  public static final class Step implements StepData {
    @XmlElements({
      @XmlElement(name = "stepElement", type = StepElement.class)
    })
    private List<StepElement> myStepElements = Lists.newArrayList();

    @XmlAttribute(name = "label")
    private String myLabel;

    @Override
    @NotNull
    public List<StepElement> getStepElements() {
      return ImmutableList.copyOf(myStepElements);
    }

    @Override
    @NotNull
    public String getLabel() {
      return myLabel;
    }

    @Override
    public String toString() {
      return "Step{" +
             "myLabel='" + myLabel + "'" +
             '}';
    }
  }

  /**
   * Wrapper class for all ordered elements living inside of a step. This is
   * less than ideal,  an ideal solution would be some way of binding to
   * a heterogeneous ordered collection, but this doesn't appear to be possible
   * at this time.
   *
   * TODO: Determine if there's a way to enforce one and only one child
   * element.
   */
  public static final class StepElement implements StepElementData {
    private StepElementType myType;

    @XmlElement(name = "code", type = Code.class)
    private Code myCode;

    @XmlElement(name = "section")
    private String mySection;

    @XmlElement(name = "action", type = Action.class)
    private Action myAction;

    @XmlElement(name = "image", type = Image.class)
    private Image myImage;

    @Override
    @NotNull
    public StepElementType getType() {
      if (myType != null) {
        return myType;
      }

      // Infer type by what sub element is present.
      if (myAction != null) {
        myType = StepElementType.ACTION;
      }
      else if (mySection != null) {
        myType = StepElementType.SECTION;
      }
      else if (myCode != null) {
        myType = StepElementType.CODE;
      }
      else if (myImage != null) {
        myType = StepElementType.IMAGE;
      }
      if (myType == null) {
        throw new RuntimeException("Unsupported StepElement.");
      }
      return myType;
    }

    @Override
    public String getCode() {
      return myCode.getCode();
    }

    @Override
    public FileType getCodeType() {
      return myCode == null ? null : myCode.getCodeType();
    }

    @Override
    public String getSection() {
      return mySection;
    }

    @Override
    public Action getAction() {
      return myAction;
    }

    @Nullable
    @Override
    public Image getImage() {
      return myImage;
    }

    @Override
    public String toString() {
      return "StepElement{" +
             "myType='" + getType() + "'" +
             '}';
    }
  }

  public static final class Code {
    @XmlValue
    private String myCode;

    @XmlAttribute(name = "fileType")
    private String myCodeType;

    public String getCode() {
      return myCode;
    }

    public FileType getCodeType() {
      if (myCodeType == null) {
        return null;
      }
      String type = myCodeType.trim().toUpperCase(Locale.ENGLISH);

      // If the list grows much, consider using a static map.
      if (type.equals("JAVA")) {
        return StdFileTypes.JAVA;
      }
      if (type.equals("XML")) {
        return StdFileTypes.XML;
      }
      if (type.equals("GROOVY")) {
        return GroovyFileType.GROOVY_FILE_TYPE;
      }
      if (type.equals("PLAIN_TEXT")) {
        return FileTypes.PLAIN_TEXT;
      }
      return null;
    }
  }

  public static final class Image {
    @XmlAttribute(name = "src")
    private String mySource;

    @XmlAttribute(name = "height")
    private int myHeight;

    @XmlAttribute(name = "width")
    private int myWidth;

    @XmlAttribute(name = "description")
    private String myDescription;

    @Nullable
    public String getSource() {
      return mySource;
    }

    public int getHeight() {
      return myHeight;
    }

    public int getWidth() {
      return myWidth;
    }

    @Nullable
    public String getDescription() {
      return myDescription;
    }
  }

  // TODO: Verify necessary properties to support wiring.
  public static final class Action implements ActionData {
    @XmlAttribute(name = "label") private String myLabel;

    @XmlAttribute(name = "key") private String myKey;

    @XmlAttribute(name = "actionArgument") private String myActionArgument;

    @XmlAttribute(name = "successMessage") private String mySuccessMessage;

    @XmlAttribute(name = "editAction") private String myEditAction;

    @XmlElement(name = "recipe", type = Recipe.class)
    private Recipe myRecipe;

    @Override
    @NotNull
    public String getLabel() {
      return myLabel;
    }

    @NotNull
    @Override
    public String getKey() {
      return myKey;
    }

    @Override
    public String getActionArgument() {
      return myActionArgument;
    }

    @Override
    public String getSuccessMessage() {
      return mySuccessMessage;
    }

    @Override
    public Recipe getRecipe() {
      return myRecipe;
    }

    @Override
    public String toString() {
      return "Action{" +
             "myLabel='" + myLabel + "'" +
             ", myKey='" + myKey + "'" +
             ", myActionArgument='" + myActionArgument + "'" +
             ", mySuccessMessage='" + mySuccessMessage + "'" +
             ", myEditAction='" + myEditAction + "'" +
             ", myRecipe='" + myRecipe + "'" +
             '}';
    }

    @SuppressWarnings("unused")
    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (myRecipe != null) {
        // Only a subset of instructions will be applied if this instruction is not added at the end.
        myRecipe.addUpdateAndSyncGradleInstruction();
      }
    }

    @Override
    public boolean equals(@Nullable Object that) {
      if (this == that) {
        return true;
      } else if (!(that instanceof Action)) {
        return false;
      }
      Action otherAction = (Action) that;
      return toString().equals(otherAction.toString());
    }

  }
}

