/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.ui.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Wizard step for specifying template-specific parameters.
 */
public class TemplateParameterStep2 extends DynamicWizardStepWithHeaderAndDescription {
  public static final Logger LOG = Logger.getInstance(TemplateParameterStep2.class);
  public static final int COLUMN_COUNT = 3;
  private final Function<Parameter, Key<?>> myParameterToKey;
  private final Map<String, Object> myPresetParameters = Maps.newHashMap();
  @Nullable private final VirtualFile myTargetDirectory;
  @NotNull private final Key<String> myPackageNameKey;
  private JLabel myTemplateIcon;
  private JPanel myTemplateParameters;
  private JLabel myTemplateDescription;
  private JPanel myRootPanel;
  private JLabel myParameterDescription;
  private JSeparator myFooterSeparator;
  private Map<String, Object> myParameterDefaultValues = Maps.newHashMap();
  private TemplateEntry myCurrentTemplate;
  private JComboBox mySourceSet;
  private JLabel mySourceSetLabel;
  private boolean myUpdatingDefaults = false;

  /**
   * Creates a new template parameters wizard step.
   *
   * @param presetParameters some parameter values may be predefined outside of this step.
   *                         User will not be allowed to change their values.
   */
  public TemplateParameterStep2(@NotNull FormFactorUtils.FormFactor formFactor, Map<String, Object> presetParameters,
                                @Nullable VirtualFile targetDirectory, @Nullable Disposable disposable,
                                @NotNull Key<String> packageNameKey) {
    super("Choose options for your new file", null, formFactor.getIcon(), disposable);
    myPresetParameters.putAll(presetParameters);
    myTargetDirectory = targetDirectory;
    myPackageNameKey = packageNameKey;
    myParameterToKey = CacheBuilder.newBuilder().weakKeys().build(CacheLoader.from(new ParameterKeyFunction()));
    myRootPanel.setBorder(createBodyBorder());
    myTemplateDescription.setBorder(BorderFactory.createEmptyBorder(0, 0, myTemplateDescription.getFont().getSize(), 0));
    setBodyComponent(myRootPanel);
  }

  private static JComponent createTextFieldWithBrowse(Parameter parameter) {
    String sourceUrl = parameter.sourceUrl;
    if (sourceUrl == null) {
      LOG.warn(String.format("Source URL is missing for parameter %1$s", parameter.name));
      sourceUrl = "";
    }
    return new TextFieldWithLaunchBrowserButton(sourceUrl);
  }

  public void setPresetValue(@NotNull String key, @Nullable Object value) {
    myPresetParameters.put(key, value);
  }

  private static JComponent createEnumCombo(Parameter parameter) {
    JComboBox combo = new ComboBox();
    List<Element> options = parameter.getOptions();
    assert !options.isEmpty();
    for (Element option : options) {
      //noinspection unchecked
      combo.addItem(createItemForOption(parameter, option));
      String isDefault = option.getAttribute(Template.ATTR_DEFAULT);
      if (isDefault != null && !isDefault.isEmpty() && Boolean.valueOf(isDefault)) {
        combo.setSelectedIndex(combo.getItemCount() - 1);
      }
    }
    return combo;
  }

  public static ComboBoxItem createItemForOption(Parameter parameter, Element option) {
    String optionId = option.getAttribute(SdkConstants.ATTR_ID);
    assert optionId != null && !optionId.isEmpty() : SdkConstants.ATTR_ID;
    NodeList childNodes = option.getChildNodes();
    assert childNodes.getLength() == 1 && childNodes.item(0).getNodeType() == Node.TEXT_NODE;
    String optionLabel = childNodes.item(0).getNodeValue().trim();
    int minSdk = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_API, parameter.name, 1);
    int minBuildApi = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_BUILD_API, parameter.name, 1);
    return new ComboBoxItem(optionId, optionLabel, minSdk, minBuildApi);
  }

  private static int getIntegerOptionValue(Element option, String attribute, @Nullable String parameterName, int defaultValue) {
    String stringValue = option.getAttribute(attribute);
    try {
      return StringUtil.isEmpty(stringValue) ? defaultValue : Integer.parseInt(stringValue);
    }
    catch (Exception e) {
      LOG.warn(String.format("Invalid %1$s value (%2$s) for option %3$s in parameter %4$s", attribute, stringValue,
                             option.getAttribute(SdkConstants.ATTR_ID), parameterName), e);
      return defaultValue;
    }
  }

  private static void addComponent(JComponent parent, JComponent component, int row, int column, boolean isLast) {
    GridConstraints gridConstraints = new GridConstraints();
    gridConstraints.setRow(row);
    gridConstraints.setColumn(column);

    boolean isGreedyComponent = component instanceof JTextField || component instanceof Spacer ||
                                component instanceof LabelWithEditLink || component instanceof TextAccessor ||
                                component instanceof EditorComboBox;

    int columnSpan = (isLast && isGreedyComponent) ? COLUMN_COUNT - column : 1;
    gridConstraints.setColSpan(columnSpan);
    gridConstraints.setAnchor(GridConstraints.ALIGN_LEFT);
    gridConstraints.setHSizePolicy(isGreedyComponent
                                   ? GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW
                                   : GridConstraints.SIZEPOLICY_CAN_SHRINK);
    gridConstraints.setVSizePolicy(component instanceof Spacer
                                   ? GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW
                                   : GridConstraints.SIZEPOLICY_FIXED);
    gridConstraints.setFill(GridConstraints.FILL_HORIZONTAL);
    parent.add(component, gridConstraints);
    if (isLast && !isGreedyComponent && column < COLUMN_COUNT - 1) {
      addComponent(parent, new Spacer(), row, column + 1, true);
    }
  }

  private Map<Parameter, Object> getParameterObjectMap(Collection<Parameter> parameters,
                                                       Map<Parameter, Object> parametersWithDefaultValues,
                                                       Map<Parameter, Object> parametersWithNonDefaultValues) {
    Map<Parameter, Object> computedDefaultValues = ParameterDefaultValueComputer.
      newDefaultValuesMap(parameters, parametersWithNonDefaultValues, new DeduplicateValuesFunction());
    Map<Parameter, Object> parameterValues = Maps.newHashMap(parametersWithDefaultValues);
    for (Map.Entry<Parameter, Object> entry : computedDefaultValues.entrySet()) {
      if (!parametersWithNonDefaultValues.keySet().contains(entry.getKey()) && entry.getValue() != null) {
        parameterValues.put(entry.getKey(), entry.getValue());
      }
    }
    return parameterValues;
  }

  @Override
  public boolean isStepVisible() {
    return myState.get(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE) != null;
  }

  @NotNull
  private List<JComponent> createComponents(final Parameter parameter) {
    JLabel label = new JLabel(parameter.name + ":");
    final JComponent dataComponent;
    if (AddAndroidActivityPath.PACKAGE_NAME_PARAMETERS.contains(parameter.id)) {
      Module module = getModule();
      if (module != null) {
        dataComponent = createPackageEntry(parameter, module);
      }
      else {
        dataComponent = new LabelWithEditLink();
      }
    }
    else if (AddAndroidActivityPath.CLASS_NAME_PARAMETERS.contains(parameter.id)) {
      Module module = getModule();
      if (module != null) {
        dataComponent = createClassEntry(parameter, module);
      }
      else {
        dataComponent = new JTextField();
      }
    }
    else {
      switch (parameter.type) {
        case BOOLEAN:
          label.setText(null);
          dataComponent = new JCheckBox(parameter.name);
          break;
        case ENUM:
          dataComponent = createEnumCombo(parameter);
          break;
        case EXTERNAL:
          dataComponent = createTextFieldWithBrowse(parameter);
          break;
        case STRING:
          dataComponent = new JTextField();
          break;
        case SEPARATOR:
          return Collections.<JComponent>singletonList(new JSeparator(SwingConstants.HORIZONTAL));
        default:
          throw new IllegalStateException(parameter.type.toString());
      }
    }
    register(parameter, dataComponent);
    return Arrays.asList(label, dataComponent);
  }

  private JComponent createClassEntry(Parameter parameter, Module module) {
    ChooseClassAction browseAction = new ChooseClassAction(module.getProject(), parameter);
    String historyKey = AddAndroidActivityPath.getRecentHistoryKey(parameter.id);
    // Need to add empty entry to the history, otherwise it will select entry used last
    RecentsManager.getInstance(module.getProject()).registerRecentEntry(historyKey, "");
    ReferenceEditorComboWithBrowseButton control =
        new ReferenceEditorComboWithBrowseButton(browseAction, "", module.getProject(), true,
                                                 new OnlyShowActivities(), historyKey);
    if (!StringUtil.isEmpty(control.getText())) {
      control.prependItem("");
      control.setText("");
    }
    addJBDocumentListener(control.getChildComponent().getDocument(), control);
    // Discourage from growing
    control.setPreferredSize(new Dimension(1, 1));
    return control;
  }

  private JComponent createPackageEntry(@NotNull Parameter parameter, @NotNull Module module) {
    Project project = module.getProject();
    com.intellij.openapi.editor.Document doc =
        JavaReferenceEditorUtil.createDocument("", project, false, JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE);
    assert doc != null;
    final EditorComboBox textField = new EditorComboBox(doc, project, StdFileTypes.JAVA);
    final List<String> recentEntries = AddAndroidActivityPath.getParameterValueHistory(parameter, project);
    if (recentEntries != null) {
      textField.setHistory(ArrayUtil.toStringArray(recentEntries));
    }
    addJBDocumentListener(doc, textField);
    textField.setPreferredSize(new Dimension(1, 1));
    return textField;
  }

  private void addJBDocumentListener(com.intellij.openapi.editor.Document doc, final JComponent textField) {
    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent event) {
        saveState(textField);
      }
    };
    Disposable disposable = getDisposable();
    if (disposable != null) {
      doc.addDocumentListener(listener, disposable);
    }
    else {
      doc.addDocumentListener(listener);
    }
  }

  @SuppressWarnings("unchecked")
  private void register(Parameter parameter, JComponent dataComponent) {
    Key<?> key = getParameterKey(parameter);
    if (dataComponent instanceof JCheckBox) {
      register((Key<Boolean>)key, (JCheckBox)dataComponent);
    }
    else if (dataComponent instanceof EditorComboBox) { // Should be above JComboBox
      register((Key<String>)key, (EditorComboBox)dataComponent, new ComponentBinding<String, EditorComboBox>() {
        @Override
        public void setValue(@Nullable String newValue, @NotNull EditorComboBox component) {
          String text = Strings.nullToEmpty(newValue);
          component.prependItem(text);
          component.setText(text);
        }

        @Nullable
        @Override
        public String getValue(@NotNull EditorComboBox component) {
          return component.getText();
        }
      });
    }
    else if (dataComponent instanceof JComboBox) {
      register(key, (JComboBox)dataComponent);
    }
    else if (dataComponent instanceof JTextField) {
      register((Key<String>)key, (JTextField)dataComponent);
    }
    else if (dataComponent instanceof TextFieldWithBrowseButton) {
      register((Key<String>)key, (TextFieldWithBrowseButton)dataComponent);
    }
    else if (dataComponent instanceof LabelWithEditLink) {
      register((Key<String>)key, (LabelWithEditLink)dataComponent, new ComponentBinding<String, LabelWithEditLink>() {
        @Override
        public void setValue(@Nullable String newValue, @NotNull LabelWithEditLink component) {
          component.setText(Strings.nullToEmpty(newValue));
        }

        @Nullable
        @Override
        public String getValue(@NotNull LabelWithEditLink component) {
          return component.getText();
        }

        @Nullable
        @Override
        public Document getDocument(@NotNull LabelWithEditLink component) {
          //return component.getDocument();
          return null;
        }
      });
    }
    else if (dataComponent instanceof TextAccessor) {
      register((Key<String>)key, dataComponent, new ComponentBinding<String, JComponent>() {
        @Override
        public void setValue(@Nullable String newValue, @NotNull JComponent component) {
          ((TextAccessor)component).setText(Strings.nullToEmpty(newValue));
        }

        @Nullable
        @Override
        public String getValue(@NotNull JComponent component) {
          return ((TextAccessor)component).getText();
        }
      });
    }
    else {
      throw new IllegalArgumentException(dataComponent.getClass().getName());
    }
  }

  @Override
  protected JLabel getDescriptionText() {
    return myParameterDescription;
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    if (myCurrentTemplate != null) {
      updateStateWithDefaults(myCurrentTemplate.getParameters());
    }
  }

  @Override
  public boolean validate() {
    setErrorHtml(null);

    AndroidVersion minApi = myState.get(AddAndroidActivityPath.KEY_MIN_SDK);
    Integer buildApi = myState.get(AddAndroidActivityPath.KEY_BUILD_SDK);

    TemplateEntry templateEntry = myState.get(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE);
    if (templateEntry == null) {
      return false;
    }
    for (Parameter param : templateEntry.getParameters()) {
      if (param != null) {
        Object value = getStateParameterValue(param);
        String error = param.validate(getProject(), getModule(), getSourceProvider(),
                                      myState.get(myPackageNameKey),
                                      value != null ? value : "");
        if (error != null) {
          // Highlight?
          setErrorHtml(error);
          return false;
        }

        // Check to see that the selection's constraints are met if this is a combo box
        if (value instanceof ComboBoxItem) {
          ComboBoxItem selectedItem = (ComboBoxItem)value;

          if (minApi != null && selectedItem.minApi > minApi.getFeatureLevel()) {
            setErrorHtml(String.format("The \"%s\" option for %s requires a minimum API level of %d",
                                       selectedItem.label, param.name, selectedItem.minApi));
            return false;
          }
          if (buildApi != null && selectedItem.minBuildApi > buildApi) {
            setErrorHtml(String.format("The \"%s\" option for %s requires a minimum API level of %d",
                                       selectedItem.label, param.name, selectedItem.minBuildApi));
            return false;
          }
        }
      }
    }
    return true;
  }

  @Nullable
  private SourceProvider getSourceProvider() {
    return myState.get(AddAndroidActivityPath.KEY_SOURCE_PROVIDER);
  }

  @Override
  public void init() {
    super.init();
    List<SourceProvider> sourceProviders = getSourceProviders();
    if (sourceProviders.size() > 0) {
      myState.put(AddAndroidActivityPath.KEY_SOURCE_PROVIDER, sourceProviders.get(0));
    }
    register(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE, (JComponent)myTemplateDescription.getParent(),
             new ComponentBinding<TemplateEntry, JComponent>() {
               @Override
               public void setValue(@Nullable TemplateEntry newValue, @NotNull JComponent component) {
                 setSelectedTemplate(newValue);
               }
             }
    );
    register(KEY_DESCRIPTION, myFooterSeparator, new ComponentBinding<String, JSeparator>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JSeparator component) {
        component.setVisible(!StringUtil.isEmpty(newValue));
      }
    });
  }

  private void setSelectedTemplate(@Nullable TemplateEntry template) {
    if (template == null) {
      return;
    }
    TemplateMetadata metadata = template.getMetadata();
    Image image = template.getImage();
    final ImageIcon icon;
    if (image != null) {
      icon = new ImageIcon(image.getScaledInstance(256, 256, Image.SCALE_SMOOTH), template.getTitle());
    }
    else {
      icon = null;
    }
    myTemplateIcon.setIcon(icon);
    myTemplateIcon.setText(template.getTitle());

    String string = ImportUIUtil.makeHtmlString(metadata.getDescription());
    myTemplateDescription.setText(string);
    //myState.put(KEY_TITLE, template.getTitle());
    updateControls(template);
  }

  private void updateControls(@Nullable TemplateEntry entry) {
    if (Objects.equal(myCurrentTemplate, entry)) {
      return;
    }
    myCurrentTemplate = entry;
    final Set<Parameter> parameters;
    if (entry != null) {
      updateStateWithDefaults(entry.getParameters());
      parameters = ImmutableSet.copyOf(filterNonUIParameters(entry));
    }
    else {
      parameters = ImmutableSet.of();
    }
    for (Component component : myTemplateParameters.getComponents()) {
      myTemplateParameters.remove(component);
      if (component instanceof JComponent) {
        deregister((JComponent)component);
      }
    }
    GridLayoutManager layout = new GridLayoutManager(parameters.size() + 1, COLUMN_COUNT);
    layout.setSameSizeHorizontally(false);
    myTemplateParameters.setLayout(layout);
    int row = 0;
    for (final Parameter parameter : parameters) {
      addComponents(parameter, row++);
    }
    addSourceSetControls(row);
  }

  private void addSourceSetControls(int row) {
    List<SourceProvider> sourceProviders = getSourceProviders();
    if (sourceProviders.size() > 1) {
      if (mySourceSetLabel == null) {
        mySourceSetLabel = new JLabel("Target Source Set:");
        mySourceSet = new ComboBox();
        register(AddAndroidActivityPath.KEY_SOURCE_PROVIDER, mySourceSet);
        setControlDescription(mySourceSet, "The selected folder contains multiple source sets, " +
                                           "this can include source sets that do not yet exist on disk. " +
                                           "Please select the target source set in which to create the files.");
      }
      mySourceSet.removeAllItems();
      for (SourceProvider sourceProvider : sourceProviders) {
        //noinspection unchecked
        mySourceSet.addItem(new ComboBoxItem(sourceProvider, sourceProvider.getName(), 0, 0));
      }
      addComponent(myTemplateParameters, mySourceSetLabel, row, 0, false);
      addComponent(myTemplateParameters, mySourceSet, row, 1, true);
    }
  }

  @NotNull
  private List<SourceProvider> getSourceProviders() {
    Module module = getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        if (myTargetDirectory != null) {
          return IdeaSourceProvider.getSourceProvidersForFile(facet, myTargetDirectory, facet.getMainSourceSet());
        }
        else {
          return IdeaSourceProvider.getAllSourceProviders(facet);
        }
      }
    }
    return ImmutableList.of();
  }

  private Iterable<Parameter> filterNonUIParameters(TemplateEntry entry) {
    return Iterables.filter(entry.getParameters(), new Predicate<Parameter>() {
      @Override
      public boolean apply(Parameter input) {
        return input != null && !StringUtil.isEmpty(input.name) && !myPresetParameters.containsKey(input.id);
      }
    });
  }

  @VisibleForTesting
  protected void updateStateWithDefaults(Collection<Parameter> parameters) {
    if (myUpdatingDefaults) {
      return;
    }
    myUpdatingDefaults = true;
    try {
      for (Parameter parameter : parameters) {
        if (myPresetParameters.containsKey(parameter.id)) {
          myState.unsafePut(getParameterKey(parameter), myPresetParameters.get(parameter.id));
        }
      }
      Map<Parameter, Object> parameterDefaults = refreshParameterDefaults(parameters, myParameterDefaultValues);
      for (Map.Entry<Parameter, Object> entry : parameterDefaults.entrySet()) {
        myState.unsafePut(getParameterKey(entry.getKey()), entry.getValue());
        myParameterDefaultValues.put(entry.getKey().id, entry.getValue());
      }
    }
    finally {
      myUpdatingDefaults = false;
    }
  }

  private Map<Parameter, Object> refreshParameterDefaults(Collection<Parameter> parameters, Map<String, Object> defaultValues) {
    final Map<Parameter, Object> parametersAtDefault = Maps.newHashMap();
    final Map<Parameter, Object> parametersAtNonDefault = Maps.newHashMap();

    for (Parameter parameter : parameters) {
      if (isDefaultParameterValue(parameter, defaultValues)) {
        parametersAtDefault.put(parameter, defaultValues.get(parameter.id));
      }
      else {
        parametersAtNonDefault.put(parameter, getStateParameterValue(parameter));
      }
    }
    return getParameterObjectMap(parameters, parametersAtDefault, parametersAtNonDefault);
  }

  @NotNull
  public Key<?> getParameterKey(@NotNull Parameter parameter) {
    //noinspection ConstantConditions
    return myParameterToKey.apply(parameter);
  }

  @Nullable
  private Object getStateParameterValue(Parameter parameter) {
    if (myPresetParameters.containsKey(parameter.id)) {
      return myPresetParameters.get(parameter.id);
    }
    else {
      return myState.get(getParameterKey(parameter));
    }
  }

  private boolean isDefaultParameterValue(Parameter parameter, Map<String, Object> defaultValues) {
    Object stateValue = getStateParameterValue(parameter);
    if (stateValue == null) {
      return true;
    }
    else {
      Object defaultValue = defaultValues.get(parameter.id);
      return Objects.equal(defaultValue, stateValue);
    }
  }

  private void addComponents(Parameter parameter, int row) {
    List<JComponent> keyComponents = createComponents(parameter);
    int column = 0;
    for (Iterator<JComponent> iterator = keyComponents.iterator(); iterator.hasNext(); ) {
      JComponent keyComponent = iterator.next();
      addComponent(myTemplateParameters, keyComponent, row, column++, !iterator.hasNext());
      setControlDescription(keyComponent, parameter.help);
    }
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Template parameters";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    for (Component component : myTemplateParameters.getComponents()) {
      if (!(component instanceof JLabel) && component.isFocusable()) {
        return (JComponent)component;
      }
    }
    return myTemplateParameters;
  }

  private static class ParameterKeyFunction implements Function<Parameter, Key<?>> {
    @Override
    public Key<?> apply(Parameter input) {
      final Class<?> clazz;
      switch (input.type) {
        case BOOLEAN:
          clazz = Boolean.class;
          break;
        case ENUM:
        case EXTERNAL:
        case STRING:
        case SEPARATOR:
          clazz = String.class;
          break;
        default:
          throw new IllegalStateException(input.type.toString());
      }
      assert input.id != null;
      return createKey(input.id, ScopedStateStore.Scope.PATH, clazz);
    }
  }

  private static class OnlyShowActivities implements JavaCodeFragment.VisibilityChecker {
    private static boolean isActivitySubclass(@NotNull PsiClass classDecl) {
      for (PsiClass superClass : classDecl.getSupers()) {
        String typename = superClass.getQualifiedName();
        if ("android.app.Activity".equals(typename) || isActivitySubclass(superClass)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Visibility isDeclarationVisible(PsiElement declaration, @Nullable PsiElement place) {
      if (declaration instanceof PsiClass) {
        PsiClass classDecl = (PsiClass)declaration;
        if (PsiClassUtil.isRunnableClass(classDecl, true, true) && isActivitySubclass(classDecl)) {
          return Visibility.VISIBLE;
        }
      }
      return Visibility.NOT_VISIBLE;
    }
  }

  private class ChooseClassAction implements ActionListener {
    private final Project myProject;
    private Parameter myParameter;

    public ChooseClassAction(Project project, Parameter parameter) {
      myProject = project;
      myParameter = parameter;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final OnlyShowActivities filter = new OnlyShowActivities();
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
          .createWithInnerClassesScopeChooser("Select Activity",
                                            GlobalSearchScope.projectScope(myProject),
                                            new ClassFilter() {
            @Override
            public boolean isAccepted(PsiClass aClass) {
              return filter.isDeclarationVisible(aClass, null) == JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
            }
          }, null
        );
      //noinspection unchecked
      Key<String> key = (Key<String>)getParameterKey(myParameter);
      final String targetClassName = myState.get(key);
      if (targetClassName != null) {
        final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.allScope(myProject));
        if (aClass != null) {
          chooser.selectDirectory(aClass.getContainingFile().getContainingDirectory());
        }
      }
      chooser.showDialog();
      PsiClass aClass = chooser.getSelected();
      if (aClass != null) {
        myState.put(key, aClass.getQualifiedName());
      }
    }
  }

  private class DeduplicateValuesFunction implements ParameterDefaultValueComputer.Deduplicator {
    private final Project project;
    private final Module module;
    private final SourceProvider provider;
    private final String packageName;

    private DeduplicateValuesFunction() {
      project = getProject();
      module = getModule();
      provider = myState.get(AddAndroidActivityPath.KEY_SOURCE_PROVIDER);
      packageName = myState.get(myPackageNameKey);
    }

    @Override
    @Nullable
    public String deduplicate(@NotNull Parameter parameter, @Nullable String value) {
      if (StringUtil.isEmpty(value) || !parameter.constraints.contains(Parameter.Constraint.UNIQUE)) {
        return value;
      }
      int i = 2;
      String suggested = value;
      while (!parameter.uniquenessSatisfied(project, module, provider, packageName, suggested)) {
        suggested = value + (i++);
      }
      return suggested;
    }
  }
}
