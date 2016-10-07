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

import com.android.SdkConstants;
import com.android.tools.idea.templates.FreemarkerUtils;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.TypedVariable;
import com.android.tools.idea.templates.parse.SaxUtils;
import com.android.tools.idea.templates.recipe.Recipe;
import com.android.tools.idea.templates.recipe.RecipeExecutor;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.swing.*;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.structure.services.BuildSystemOperationsLookup.getBuildSystemOperations;
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
 *
 * TODO: Consider migrating much of this to JAXB to reduce manual parsing.
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

  // These fields are not initialized by the constructor but are by the parsing step and not null afterward.
  @NotNull
  @SuppressWarnings("NullableProblems")
  private ServiceCategory myServiceCategory;

  @NotNull
  @SuppressWarnings("NullableProblems")
  private DeveloperServiceMetadata myDeveloperServiceMetadata;

  @NotNull
  @SuppressWarnings("NullableProblems")
  private File myRecipeFile;

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

  @NotNull
  public DeveloperServiceMetadata getDeveloperServiceMetadata() {
    return myDeveloperServiceMetadata;
  }

  @NotNull
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
    else if (tagName.equals(Schema.UiButton.TAG)) {
      parseUiButton(attributes);
    }
    else if (tagName.equals(Schema.UiCheckbox.TAG)) {
      parseUiCheckbox(attributes);
    }
    else if (tagName.equals(Schema.UiInput.TAG)) {
      parseUiInput(attributes);
    }
    else if (tagName.equals(Schema.UiLabel.TAG)) {
      parseUiLabel(attributes);
    }
    else if (tagName.equals(Schema.UiLink.TAG)) {
      parseUiLink(attributes);
    }
    else if (tagName.equals(Schema.UiPulldown.TAG)) {
      parseUiPulldown(attributes);
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

  public void install() {
    List<File> filesToOpen = Lists.newArrayList();
    analyzeRecipe(false, filesToOpen, null, null, null, null, null);
    TemplateUtils.openEditors(myModule.getProject(), filesToOpen, true);
  }

  private void analyzeRecipe(boolean findOnlyReferences,
                             @Nullable Collection<File> openFiles,
                             @Nullable SetMultimap<String, String> dependencies,
                             @Nullable Collection<String> classpathEntries,
                             @Nullable Collection<String> plugins,
                             @Nullable Collection<File> sourceFiles,
                             @Nullable Collection<File> targetFiles) {
    try {
      File moduleRoot = new File(myModule.getModuleFilePath()).getParentFile();
      // @formatter:off
      RenderingContext context = RenderingContext.Builder
        .newContext(myRootPath, myModule.getProject())
        .withParams(myContext.toValueMap())
        .withOutputRoot(moduleRoot)
        .withModuleRoot(moduleRoot)
        .withFindOnlyReferences(findOnlyReferences)
        .withGradleSync(false)
        .intoOpenFiles(openFiles)
        .intoDependencies(dependencies)
        .intoClasspathEntries(classpathEntries)
        .intoPlugins(plugins)
        .intoSourceFiles(sourceFiles)
        .intoTargetFiles(targetFiles)
        .build();
      // @formatter:on
      String xml = FreemarkerUtils.processFreemarkerTemplate(context, myRecipeFile, null);
      Recipe recipe = Recipe.parse(new StringReader(xml));
      RecipeExecutor recipeExecutor = context.getRecipeExecutor();
      recipe.execute(recipeExecutor);
    }
    catch (TemplateProcessingException e) {
      throw new RuntimeException(e);
    }
    catch (JAXBException e) {
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
    String id = requireAttr(attributes, Schema.Service.ATTR_ID);
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
    myDeveloperServiceMetadata = new DeveloperServiceMetadata(id, name, description, icon);
    if (learnLink != null) {
      myDeveloperServiceMetadata.setLearnMoreLink(toUri(learnLink));
    }
    if (apiLink != null) {
      myDeveloperServiceMetadata.setApiLink(toUri(apiLink));
    }
  }

  private void closeServiceTag() {
    SetMultimap<String, String> dependencies = LinkedHashMultimap.create();
    Set<String> classpathEntries = Sets.newHashSet();
    Set<String> plugins = Sets.newHashSet();
    List<File> sourceFiles = Lists.newArrayList();
    List<File> targetFiles = Lists.newArrayList();
    analyzeRecipe(true, null, dependencies, classpathEntries, plugins, sourceFiles, targetFiles);

    // Ignore test configurations here.
    for (String d : dependencies.get(SdkConstants.GRADLE_COMPILE_CONFIGURATION)) {
      myDeveloperServiceMetadata.addDependency(d);
    }
    for (String c : classpathEntries) {
      myDeveloperServiceMetadata.addClasspathEntry(c);
    }
    for (String p : plugins) {
      myDeveloperServiceMetadata.addPlugin(p);
    }
    for (File f : sourceFiles) {
      if (f.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
        parseManifestForPermissions(f);
      }
    }
    for (File f : targetFiles) {
      myDeveloperServiceMetadata.addModifiedFile(f);
    }

    Project project = myModule.getProject();
    boolean allDependenciesFound = getBuildSystemOperations(project).containsAllDependencies(myModule, myDeveloperServiceMetadata);
    myContext.installed().set(allDependenciesFound);
    myContext.snapshot();
  }

  private void parseManifestForPermissions(@NotNull File f) {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(f, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String tagName, Attributes attributes) throws SAXException {
          if (tagName.equals(SdkConstants.TAG_USES_PERMISSION) ||
              tagName.equals(SdkConstants.TAG_USES_PERMISSION_SDK_23) ||
              tagName.equals(SdkConstants.TAG_USES_PERMISSION_SDK_M)) {
            String permission = attributes.getValue(SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_NAME);
            // Most permissions are "android.permission.XXX", so for readability, just remove the prefix if present
            permission = permission.replace(SdkConstants.ANDROID_PKG_PREFIX + SdkConstants.ATTR_PERMISSION + ".", "");
            myDeveloperServiceMetadata.addPermission(permission);
          }
        }
      });
    }
    catch (Exception e) {
      // This method shouldn't crash the user for any reason, as showing permissions is just
      // informational, but log a warning so developers can see if they make a mistake when
      // creating their service.
      LOG.warn("Failed to read permissions from AndroidManifest.xml", e);
    }
  }

  private void parseUiGridTag(@NotNull Attributes attributes) {
    parseRowCol(attributes);

    String weights = requireAttr(attributes, Schema.UiGrid.ATTR_COL_DEFINITIONS);
    JPanel grid = myPanelBuilder.startGrid(weights);
    bindTopLevelProperties(grid, attributes);
  }

  private void closeUiGridTag() {
    myPanelBuilder.endGrid();
  }

  private void parseUiButton(@NotNull Attributes attributes) {
    parseRowCol(attributes);
    JButton button = myPanelBuilder.addButton();
    bindTopLevelProperties(button, attributes);

    String textKey = attributes.getValue(Schema.UiButton.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(button);
      myPanelBuilder.getBindings().bind(textProperty, parseString(textKey));
    }

    String actionKey = attributes.getValue(Schema.UiButton.ATTR_ACTION);
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

  private void parseUiCheckbox(@NotNull Attributes attributes) {
    parseRowCol(attributes);
    JCheckBox checkbox = myPanelBuilder.addCheckbox();
    bindTopLevelProperties(checkbox, attributes);

    String textKey = attributes.getValue(Schema.UiCheckbox.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(checkbox);
      myPanelBuilder.getBindings().bind(textProperty, parseString(textKey));
    }

    String checkedKey = attributes.getValue(Schema.UiCheckbox.ATTR_CHECKED);
    if (checkedKey != null) {
      SelectedProperty selectedProperty = new SelectedProperty(checkbox);
      BoolProperty checkedValue = (BoolProperty)parseBool(checkedKey);
      myPanelBuilder.getBindings().bindTwoWay(selectedProperty, checkedValue);
    }
  }

  private void parseUiInput(@NotNull Attributes attributes) {
    parseRowCol(attributes);
    JTextField field = myPanelBuilder.addField();
    bindTopLevelProperties(field, attributes);

    String textKey = attributes.getValue(Schema.UiInput.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(field);
      StringProperty textValue = (StringProperty)parseString(textKey);
      myPanelBuilder.getBindings().bindTwoWay(textProperty, textValue);
    }
  }

  private void parseUiLabel(@NotNull Attributes attributes) {
    parseRowCol(attributes);
    JLabel label = myPanelBuilder.addLabel();
    bindTopLevelProperties(label, attributes);

    String textKey = attributes.getValue(Schema.UiLabel.ATTR_TEXT);
    if (textKey != null) {
      TextProperty textProperty = new TextProperty(label);
      myPanelBuilder.getBindings().bind(textProperty, parseString(textKey));
    }
  }

  private void parseUiLink(@NotNull Attributes attributes) {
    parseRowCol(attributes);
    HyperlinkLabel link =
      myPanelBuilder.addLink(requireAttr(attributes, Schema.UiLink.ATTR_TEXT), toUri(requireAttr(attributes, Schema.UiLink.ATTR_URL)));
    bindTopLevelProperties(link, attributes);
  }

  private void parseUiPulldown(@NotNull Attributes attributes) {
    parseRowCol(attributes);
    String listKey = requireAttr(attributes, Schema.UiPulldown.ATTR_LIST);
    ObservableList<String> backingList = getList(listKey);
    JComboBox comboBox = myPanelBuilder.addComboBox(backingList);
    bindTopLevelProperties(comboBox, attributes);

    String indexKey = attributes.getValue(Schema.UiPulldown.ATTR_INDEX);
    if (indexKey != null) {
      SelectedIndexProperty indexProperty = new SelectedIndexProperty(comboBox);
      IntProperty indexValue = (IntProperty)parseInt(indexKey);
      myPanelBuilder.getBindings().bindTwoWay(indexProperty, indexValue);
    }
  }

  private void parseRowCol(@NotNull Attributes attributes) {
    String row = attributes.getValue(Schema.UiTag.ATTR_ROW);
    if (row != null) {
      myPanelBuilder.setRow(Integer.parseInt(row));
    }
    String col = attributes.getValue(Schema.UiTag.ATTR_COL);
    if (col != null) {
      myPanelBuilder.setCol(Integer.parseInt(col));
    }
  }

  private void bindTopLevelProperties(@NotNull JComponent component, @NotNull Attributes attributes) {
    String visibleKey = attributes.getValue(Schema.UiTag.ATTR_VISIBLE);
    if (visibleKey != null) {
      VisibleProperty visibleProperty = new VisibleProperty(component);
      myPanelBuilder.getBindings().bind(visibleProperty, parseBool(visibleKey));
    }

    String enabledKey = attributes.getValue(Schema.UiTag.ATTR_ENABLED);
    if (enabledKey != null) {
      EnabledProperty enabledProperty = new EnabledProperty(component);
      myPanelBuilder.getBindings().bind(enabledProperty, parseBool(enabledKey));
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
      // noinspection unchecked
      return (ObservableValue<Boolean>)myContext.getValue(matcher.group(1));
    }
    else {
      final Boolean boolValue = (Boolean)TypedVariable.parse(TypedVariable.Type.BOOLEAN, value);
      if (boolValue == null) {
        throw new RuntimeException("Invalid bool value (did you forget ${...}): " + value);
      }
      return new BoolValueProperty(boolValue);
    }
  }

  @NotNull
  private ObservableValue<String> parseString(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      // noinspection unchecked
      return (ObservableValue<String>)myContext.getValue(matcher.group(1));
    }
    else {
      return new StringValueProperty(value);
    }
  }

  @NotNull
  private ObservableValue<Integer> parseInt(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      // noinspection unchecked
      return (ObservableValue<Integer>)myContext.getValue(matcher.group(1));
    }
    else {
      final Integer intValue = (Integer)TypedVariable.parse(TypedVariable.Type.INTEGER, value);
      if (intValue == null) {
        throw new RuntimeException("Invalid integer value (did you forget ${...}): " + value);
      }

      return new IntValueProperty(intValue);
    }
  }

  @NotNull
  private <E> ObservableList<E> getList(@NotNull final String value) {
    Matcher matcher = VAR_PATTERN.matcher(value);
    if (matcher.find()) {
      // noinspection unchecked
      return (ObservableList<E>)myContext.getValue(matcher.group(1));
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
      public static final String ATTR_LEARN_MORE = "learnMore";
      public static final String ATTR_MIN_API = "minApi";
      public static final String ATTR_NAME = "name";
      public static final String ATTR_ID = "id";
    }

    public static abstract class UiTag {
      public static final String ATTR_COL = "col";
      public static final String ATTR_ROW = "row";
      public static final String ATTR_ENABLED = "enabled";
      public static final String ATTR_VISIBLE = "visible";
    }

    public static final class UiGrid extends UiTag {
      public static final String TAG = "uiGrid";
      public static final String ATTR_COL_DEFINITIONS = "colDefinitions";
    }

    public static final class UiButton extends UiTag {
      public static final String TAG = "uiButton";
      public static final String ATTR_TEXT = "text";
      public static final String ATTR_ACTION = "action";
    }

    public static final class UiCheckbox extends UiTag {
      public static final String TAG = "uiCheckbox";
      public static final String ATTR_TEXT = "text";
      public static final String ATTR_CHECKED = "checked";
    }

    public static final class UiInput extends UiTag {
      public static final String TAG = "uiInput";
      public static final String ATTR_TEXT = "text";
    }

    public static final class UiLabel extends UiTag {
      public static final String TAG = "uiLabel";
      public static final String ATTR_TEXT = "text";
    }

    public static final class UiLink extends UiTag {
      public static final String TAG = "uiLink";
      public static final String ATTR_TEXT = "text";
      public static final String ATTR_URL = "url";
    }

    public static final class UiPulldown extends UiTag {
      public static final String TAG = "uiPulldown";
      public static final String ATTR_LIST = "list";
      public static final String ATTR_INDEX = "index";
    }
  }
}
