/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.services;

import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.parse.SaxUtils;
import com.android.tools.idea.templates.recipe.Recipe;
import com.android.tools.idea.templates.recipe.RecipeContext;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.IntProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.ui.properties.expressions.integer.IntExpression;
import com.android.tools.idea.ui.properties.expressions.string.StringExpression;
import com.android.tools.idea.ui.properties.swing.*;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.containers.Stack;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.SAXParser;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * Class which handles the parsing of a service.xml file. Note that a service.xml file is
 * additionally associated with a {@link Module}, and the same service.xml file may parse
 * differently depending on the settings of the module it is linked to.
 * <p/>
 * This class inherits from SAX's {@link DefaultHandler} and is meant to be passed into a
 * {@link SAXParser}.
 * <p/>
 * See {@link Schema} for the full XML schema for a service file to get a better overview of its
 * capabilities.
 */
/* package */ final class ServiceXmlParser extends DefaultHandler {
  private static final Logger LOG = Logger.getInstance(ServiceXmlParser.class);

  /**
   * Highest supported format; templates with a higher number will be skipped
   */
  private static final int CURRENT_FORMAT = 1;

  /**
   * Searches for values that look like "${var_name}". The variable name is captured.
   */
  private static final Pattern VAR_PATTERN = Pattern.compile("^\\$\\{(.+)\\}$");

  /**
   * Searches for values that look like "${method_name()}". The method name is captured.
   */
  private static final Pattern ACTION_PATTERN = Pattern.compile("^\\$\\{(.+)\\(\\)\\}$");

  @NotNull private final Module myModule;
  @NotNull private final File myRootPath;
  @NotNull private final ServiceContext myContext;
  @NotNull private final Stack<String> myTagStack = new Stack<String>();

  @NotNull private ServicePanelBuilder myPanelBuilder;
  @NotNull private ServiceCategory myServiceCategory;
  @NotNull private DeveloperServiceMetadata myDeveloperServiceMetadata;
  @NotNull private File myRecipeFile;

  public ServiceXmlParser(@NotNull Module module, @NotNull File rootPath, @NotNull ServiceContext serviceContext) {
    myModule = module;
    myRootPath = rootPath;
    myContext = serviceContext;
    myPanelBuilder = new ServicePanelBuilder();
  }

  @NotNull
  private static URI toUri(@NotNull String urlString) {
    try {
      return new URI(urlString);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(String.format("Malformed link argument: %1$s", urlString), e);
    }
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public ServiceContext getContext() {
    return myContext;
  }

  public DeveloperServiceMetadata getDeveloperServiceMetadata() {
    return myDeveloperServiceMetadata;
  }

  public ServiceCategory getServiceCategory() {
    return myServiceCategory;
  }

  public JPanel getServicePanel() {
    return myPanelBuilder.getPanel();
  }

  @Override
  public void startElement(String uri, String localName, @NotNull String tagName, @NotNull Attributes attributes) throws SAXException {
    myTagStack.push(tagName);
    if (tagName.equals(Schema.Service.TAG)) {
      parseServiceTag(attributes);
    }
    else if (tagName.equals(Schema.UiGrid.TAG)) {
      parseUiGridTag(attributes);
    }
    else if (tagName.equals(Schema.UiItem.TAG)) {
      parseUiItemTag(attributes);
    }
    else {
      LOG.warn("WARNING: Unknown service directive " + tagName);
    }
  }

  @Override
  public void endElement(String uri, String localName, @NotNull String tagName) throws SAXException {
    if (tagName.equals(Schema.Service.TAG)) {
      closeServiceTag();
    }
    else if (tagName.equals(Schema.UiGrid.TAG)) {
      closeUiGridTag();
    }

    myTagStack.pop();
  }

  @NotNull
  public Recipe createRecipe(boolean executeRecipe) {
    Configuration freemarker = new FreemarkerConfiguration();
    PrefixTemplateLoader loader = new PrefixTemplateLoader(myRootPath.getPath());
    Map<String, Object> paramMap = FreemarkerUtils.createParameterMap(myContext.toValueMap());

    try {
      freemarker.setTemplateLoader(loader);
      String xml = FreemarkerUtils.processFreemarkerTemplate(freemarker, paramMap, myRecipeFile);
      Recipe recipe = Recipe.parse(new StringReader(xml));

      if (executeRecipe) {
        RecipeContext recipeContext = new RecipeContext(myModule, loader, freemarker, paramMap, myRootPath, false);
        recipe.execute(recipeContext);

        // Convert relative paths to absolute paths, so TemplateUtils.openEditors can find them
        List<File> relFilesToOpen = recipe.getFilesToOpen();
        List<File> absFilesToOpen = Lists.newArrayListWithCapacity(relFilesToOpen.size());
        for (File file : relFilesToOpen) {
          absFilesToOpen.add(recipeContext.getTargetFile(file));
        }
        TemplateUtils.openEditors(myModule.getProject(), absFilesToOpen, true);
      }

      return recipe;
    }
    catch (TemplateException e) {
      throw new RuntimeException(e);
    }
    catch (JAXBException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void parseServiceTag(@NotNull Attributes attributes) {
    String format = requireAttr(attributes, Schema.Service.ATTR_FORMAT);
    try {
      if (Integer.parseInt(format) > CURRENT_FORMAT) {
        throw new RuntimeException(
          String.format("Definition found with format %1$s newer than newest known format %2$s", format, CURRENT_FORMAT));
      }
    }
    catch (NumberFormatException e) {
      throw new RuntimeException(String.format("Non-numeric value passed to format attribute: %1$s", format));
    }

    String name = requireAttr(attributes, Schema.Service.ATTR_NAME);
    String description = requireAttr(attributes, Schema.Service.ATTR_DESCRIPTION);
    String category = requireAttr(attributes, Schema.Service.ATTR_CATEGORY);
    File iconFile = new File(myRootPath, requireAttr(attributes, Schema.Service.ATTR_ICON));

    String learnLink = attributes.getValue(Schema.Service.ATTR_LEARN_MORE);
    String apiLink = attributes.getValue(Schema.Service.ATTR_API_DOCS);

    myRecipeFile = new File(myRootPath, requireAttr(attributes, Schema.Service.ATTR_EXECUTE));

    try {
      myServiceCategory = ServiceCategory.valueOf(UPPER_CAMEL.to(UPPER_UNDERSCORE, category));
    }
    catch (IllegalArgumentException e) {
      // We got a bad category value - show the developer an error so they can fix their service.xml
      List<String> validCategories = Lists.transform(Arrays.asList(ServiceCategory.values()), new Function<ServiceCategory, String>() {
        @Nullable
        @Override
        public String apply(ServiceCategory c) {
          return c.getDisplayName();
        }
      });

      throw new RuntimeException(
        String.format("Invalid category \"%1$s\", should be one of [%2$s]", category, Joiner.on(',').join(validCategories)));
    }

    Icon icon = new ImageIcon(iconFile.getPath());
    myDeveloperServiceMetadata = new DeveloperServiceMetadata(name, description, icon);
    if (learnLink != null) {
      myDeveloperServiceMetadata.setLearnMoreLink(toUri(learnLink));
    }
    if (apiLink != null) {
      myDeveloperServiceMetadata.setApiLink(toUri(apiLink));
    }
  }

  private void closeServiceTag() {
    Recipe recipe = createRecipe(false);

    for (String d : recipe.getDependencies()) {
      myDeveloperServiceMetadata.addDependency(d);
    }
    for (File f : recipe.getFilesToModify()) {
      myDeveloperServiceMetadata.addModifiedFile(f);
    }

    // Consider ourselves installed if this service's dependencies are already found in the current
    // module.
    // TODO: Flesh this simplistic approach out more. We would like to have a way to say a service
    // isn't installed even if its dependency happens to be added to the project. For example,
    // multiple services might share a dependency but have additional settings that indicate some
    // are installed and others aren't.
    List<String> moduleDependencyNames = Lists.newArrayList();
    GradleBuildFile gradleBuildFile = GradleBuildFile.get(myModule);
    if (gradleBuildFile != null) {
      for (BuildFileStatement dependency : gradleBuildFile.getDependencies()) {
        if (dependency instanceof Dependency) {
          Object data = ((Dependency)dependency).data;
          if (data instanceof String) {
            String dependencyString = (String)data;
            List<String> dependencyParts = Lists.newArrayList(Splitter.on(':').split(dependencyString));
            if (dependencyParts.size() == 3) {
              // From the dependency URL "group:name:version" string - we only care about "name"
              // We ignore the version, as a service may be installed using an older version
              // TODO: Handle "group: 'com.android.support', name: 'support-v4', version: '21.0.+'" format also
              // See also GradleDetector#getNamedDependency
              moduleDependencyNames.add(dependencyParts.get(1));
            }
          }
        }
      }
    }
    boolean allDependenciesFound = true;
    for (String serviceDependency : myDeveloperServiceMetadata.getDependencies()) {
      boolean thisDependencyFound = false;
      for (String moduleDependencyName : moduleDependencyNames) {
        if (serviceDependency.contains(moduleDependencyName)) {
          thisDependencyFound = true;
          break;
        }
      }

      if (!thisDependencyFound) {
        allDependenciesFound = false;
        break;
      }
    }

    myContext.installed().set(allDependenciesFound);
    myContext.snapshot();
  }

  private void parseUiGridTag(@NotNull Attributes attributes) {
    parseGridCoords(attributes);

    String weights = requireAttr(attributes, Schema.UiGrid.ATTR_COL_DEFINITIONS);
    JPanel grid = myPanelBuilder.startGrid(weights);
    bindComponentProperties(grid, attributes);
  }

  private void closeUiGridTag() {
    myPanelBuilder.endGrid();
  }

  private void parseUiItemTag(@NotNull Attributes attributes) {
    parseGridCoords(attributes);

    String type = requireAttr(attributes, Schema.UiItem.ATTR_TYPE);
    if (type.equals(Schema.UiItem.Type.VALUE_BUTTON)) {
      JButton button = myPanelBuilder.addButton();
      bindButtonProperties(button, attributes);
      bindComponentProperties(button, attributes);
    }
    else if (type.equals(Schema.UiItem.Type.VALUE_CHECKBOX)) {
      JCheckBox checkbox = myPanelBuilder.addCheckbox();
      bindComponentProperties(checkbox, attributes);
      bindCheckboxProperties(checkbox, attributes);
    }
    else if (type.equals(Schema.UiItem.Type.VALUE_INPUT)) {
      JTextField field = myPanelBuilder.addField();
      bindComponentProperties(field, attributes);
      bindFieldProperties(field, attributes);
    }
    else if (type.equals(Schema.UiItem.Type.VALUE_LABEL)) {
      JLabel label = myPanelBuilder.addLabel();
      bindComponentProperties(label, attributes);
      bindLabelProperties(label, attributes);
    }
    else if (type.equals(Schema.UiItem.Type.VALUE_LINK)) {
      HyperlinkLabel link = myPanelBuilder.addLink(requireAttr(attributes, Schema.UiItem.Type.Text.ATTR_TEXT),
                                                   toUri(requireAttr(attributes, Schema.UiItem.Type.Link.ATTR_URL)));
      bindComponentProperties(link, attributes);
    }
    else if (type.equals(Schema.UiItem.Type.VALUE_PULLDOWN)) {
      String listKey = requireAttr(attributes, Schema.UiItem.Type.Pulldown.ATTR_LIST);
      ObservableList<String> backingList = getList(listKey);
      JComboBox comboBox = myPanelBuilder.addComboBox(backingList);
      bindComponentProperties(comboBox, attributes);
      bindComboBoxProperties(comboBox, attributes);
    }
  }

  private void parseGridCoords(@NotNull Attributes attributes) {
    String row = attributes.getValue(Schema.UiTag.ATTR_ROW);
    if (row != null) {
      myPanelBuilder.setRow(Integer.parseInt(row));
    }
    String col = attributes.getValue(Schema.UiTag.ATTR_COL);
    if (col != null) {
      myPanelBuilder.setCol(Integer.parseInt(col));
    }
  }

  private void bindButtonProperties(@NotNull JButton button, @NotNull Attributes attributes) {
    String textKey = attributes.getValue(Schema.UiItem.Type.Text.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(button);
      myPanelBuilder.getBindings().bind(textProperty, parseString(textKey));
    }

    String actionKey = attributes.getValue(Schema.UiItem.Type.Button.ATTR_ACTION);
    if (actionKey != null) {
      final Runnable action = parseAction(actionKey);
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          action.run();
        }
      });
    }
  }

  private void bindComponentProperties(@NotNull JComponent component, @NotNull Attributes attributes) {
    String visibleKey = attributes.getValue(Schema.UiItem.Type.Component.ATTR_VISIBLE);
    if (visibleKey != null) {
      VisibleProperty visibleProperty = new VisibleProperty(component);
      myPanelBuilder.getBindings().bind(visibleProperty, parseBool(visibleKey));
    }

    String enabledKey = attributes.getValue(Schema.UiItem.Type.Component.ATTR_ENABLED);
    if (enabledKey != null) {
      EnabledProperty enabledProperty = new EnabledProperty(component);
      myPanelBuilder.getBindings().bind(enabledProperty, parseBool(enabledKey));
    }
  }

  private void bindCheckboxProperties(@NotNull JCheckBox checkbox, @NotNull Attributes attributes) {
    String textKey = attributes.getValue(Schema.UiItem.Type.Text.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(checkbox);
      myPanelBuilder.getBindings().bind(textProperty, parseString(textKey));
    }

    String checkedKey = attributes.getValue(Schema.UiItem.Type.CheckBox.ATTR_CHECKED);
    if (checkedKey != null) {
      SelectedProperty selectedProperty = new SelectedProperty(checkbox);
      BoolProperty checkedValue = (BoolProperty)parseBool(checkedKey);
      myPanelBuilder.getBindings().bindTwoWay(selectedProperty, checkedValue);
    }
  }

  private void bindComboBoxProperties(@NotNull JComboBox comboBox, @NotNull Attributes attributes) {
    String indexKey = attributes.getValue(Schema.UiItem.Type.Pulldown.ATTR_INDEX);
    if (indexKey != null) {
      SelectedIndexProperty indexProperty = new SelectedIndexProperty(comboBox);
      IntProperty indexValue = (IntProperty)parseInt(indexKey);
      myPanelBuilder.getBindings().bindTwoWay(indexProperty, indexValue);
    }
  }

  private void bindFieldProperties(@NotNull JTextField field, @NotNull Attributes attributes) {
    String textKey = attributes.getValue(Schema.UiItem.Type.Text.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(field);
      StringProperty textValue = (StringProperty)parseString(textKey);
      myPanelBuilder.getBindings().bindTwoWay(textProperty, textValue);
    }
  }

  private void bindLabelProperties(@NotNull JLabel label, @NotNull Attributes attributes) {
    String textKey = attributes.getValue(Schema.UiItem.Type.Text.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(label);
      myPanelBuilder.getBindings().bind(textProperty, parseString(textKey));
    }
  }

  @NotNull
  private Runnable parseAction(String value) {
    Matcher matcher = ACTION_PATTERN.matcher(value);
    if (matcher.find()) {
      String varName = matcher.group(1);
      return myContext.getAction(varName);
    }
    else {
      throw new RuntimeException("Invalid action value (did you forget ${...()}): " + value);
    }
  }

  @NotNull
  private ObservableValue<Boolean> parseBool(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      String varName = matcher.group(1);
      return (ObservableValue<Boolean>)myContext.getValue(varName);
    }
    else {
      final Boolean boolValue = (Boolean)TypedVariable.parse(TypedVariable.Type.BOOLEAN, value);
      if (boolValue == null) {
        throw new RuntimeException("Invalid bool value (did you forget ${...}): " + value);
      }
      return new BooleanExpression() {
        @NotNull
        @Override
        public Boolean get() {
          return boolValue;
        }
      };
    }
  }

  @NotNull
  private ObservableValue<String> parseString(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      String varName = matcher.group(1);
      return (ObservableValue<String>)myContext.getValue(varName);
    }
    else {
      return new StringExpression() {
        @NotNull
        @Override
        public String get() {
          return value;
        }
      };
    }
  }

  @NotNull
  private ObservableValue<Integer> parseInt(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      String varName = matcher.group(1);
      return (ObservableValue<Integer>)myContext.getValue(varName);
    }
    else {
      final Integer intValue = (Integer)TypedVariable.parse(TypedVariable.Type.INTEGER, value);
      if (intValue == null) {
        throw new RuntimeException("Invalid integer value (did you forget ${...}): " + value);
      }

      return new IntExpression() {
        @NotNull
        @Override
        public Integer get() {
          return intValue;
        }
      };
    }
  }

  @NotNull
  private <E> ObservableList<E> getList(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      String varName = matcher.group(1);
      return (ObservableList<E>)myContext.getValue(varName);
    }
    else {
      throw new RuntimeException("Invalid list value (did you forget ${...}): " + value);
    }
  }

  private String requireAttr(@NotNull Attributes attributes, @NotNull String attrFormat) {
    return SaxUtils.requireAttr(myTagStack.peek(), attributes, attrFormat);
  }

  private static final class Schema {
    public static final class Service {
      public static final String TAG = "service";
      public static final String ATTR_API_DOCS = "apiDocs";
      public static final String ATTR_CATEGORY = "category";
      public static final String ATTR_DESCRIPTION = "description";
      public static final String ATTR_EXECUTE = "execute";
      public static final String ATTR_FORMAT = "format";
      public static final String ATTR_ICON = "icon";
      public static final String ATTR_INITIALIZE = "initialize";
      public static final String ATTR_LEARN_MORE = "learnMore";
      public static final String ATTR_MIN_API = "minApi";
      public static final String ATTR_NAME = "name";
    }

    public static class UiTag {
      public static final String ATTR_COL = "col";
      public static final String ATTR_ROW = "row";
    }

    public static final class UiGrid extends UiTag {
      public static final String TAG = "uiGrid";
      public static final String ATTR_COL_DEFINITIONS = "colDefinitions";
    }

    public static final class UiItem extends UiTag {
      public static final String TAG = "uiItem";
      public static final String ATTR_TYPE = "type";

      public static final class Type {
        public static final String VALUE_BUTTON = "button";
        public static final String VALUE_CHECKBOX = "checkbox";
        public static final String VALUE_INPUT = "input";
        public static final String VALUE_LABEL = "label";
        public static final String VALUE_LINK = "link";
        public static final String VALUE_PULLDOWN = "pulldown";

        public static final class Component {
          public static final String ATTR_ENABLED = "enabled";
          public static final String ATTR_VISIBLE = "visible";
        }

        public static final class Button {
          public static final String ATTR_ACTION = "action";
        }

        public static final class CheckBox {
          public static final String ATTR_CHECKED = "checked";
        }

        public static final class Link {
          public static final String ATTR_URL = "url";
        }

        public static final class Text {
          public static final String ATTR_TEXT = "text";
        }

        public static final class Pulldown {
          public static final String ATTR_LIST = "list";
          public static final String ATTR_INDEX = "index";
        }
      }
    }
  }
}
