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
package com.android.tools.idea.npw;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.android.tools.idea.ui.LabelWithEditLink;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.*;
import com.google.common.base.Optional;
import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtilRt;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.wizard.WizardConstants.IS_LIBRARY_KEY;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Wizard step for specifying template-specific parameters.
 * This class is used for configuring Android Activities AND non-Android lib modules.
 *
 * @deprecated Replaced by {@link ConfigureTemplateParametersStep}
 */
public class TemplateParameterStep2 extends DynamicWizardStepWithDescription {
  public static final Logger LOG = Logger.getInstance(TemplateParameterStep2.class);
  public static final int COLUMN_COUNT = 3;
  private static final Key<File> KEY_TEMPLATE_ICON = createKey("page.template.icon", ScopedStateStore.Scope.STEP, File.class);

  private final Function<Parameter, Key<?>> myParameterToKey;
  private final Map<String, Object> myPresetParameters = Maps.newHashMap();
  @NotNull private final Key<String> myPackageNameKey;
  private final LoadingCache<File, Optional<Icon>> myThumbnailsCache = CacheBuilder.newBuilder().build(new TemplateIconLoader());
  /**
   * Can be null if this is used for non-android libs.
   * TODO: Use for icon
   */
  @Nullable private final FormFactorUtils.FormFactor myFormFactor;
  private final SourceProvider[] mySourceProviders;
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
  private Map<Parameter, List<JComponent>> myParameterComponents = new WeakHashMap<Parameter, List<JComponent>>();
  private final StringEvaluator myEvaluator = new StringEvaluator();
  private Map<JComponent, Parameter> myDataComponentParameters = new WeakHashMap<JComponent, Parameter>();
  private final String myStepTitle;

  /**
   * Creates a new template parameters wizard step.
   *
   * @param presetParameters some parameter values may be predefined outside of this step.
   *                         User will not be allowed to change their values.
   */
  public TemplateParameterStep2(@Nullable FormFactorUtils.FormFactor formFactor, Map<String, Object> presetParameters,
                                @Nullable Disposable disposable, @NotNull Key<String> packageNameKey,
                                SourceProvider[] sourceProviders, String stepTitle) {
    super(disposable);
    myFormFactor = formFactor;
    mySourceProviders = sourceProviders;
    myPresetParameters.putAll(presetParameters);
    myPackageNameKey = packageNameKey;
    myParameterToKey = CacheBuilder.newBuilder().weakKeys().build(CacheLoader.from(new ParameterKeyFunction()));
    myRootPanel.setBorder(createBodyBorder());
    myTemplateDescription.setBorder(BorderFactory.createEmptyBorder(0, 0, myTemplateDescription.getFont().getSize(), 0));
    setBodyComponent(myRootPanel);
    myStepTitle = stepTitle;
  }

  public void setPresetValue(@NotNull String key, @Nullable Object value) {
    myPresetParameters.put(key, value);
    invokeUpdate(null);
  }

  private static JComponent createEnumCombo(Parameter parameter) {
    List<Element> options = parameter.getOptions();
    ApiComboBoxItem[] items = new ApiComboBoxItem[options.size()];
    int initialSelection = -1;
    int i = 0;
    assert !options.isEmpty();
    for (Element option : options) {
      //noinspection unchecked
      items[i++] = createItemForOption(parameter, option);
      String isDefault = option.getAttribute(Template.ATTR_DEFAULT);
      if (isDefault != null && !isDefault.isEmpty() && Boolean.valueOf(isDefault)) {
        initialSelection = i - 1;
      }
    }
    @SuppressWarnings("UndesirableClassUsage")
    JComboBox comboBox = new JComboBox(items);
    comboBox.setSelectedIndex(initialSelection);
    return comboBox;
  }

  public static ApiComboBoxItem createItemForOption(Parameter parameter, Element option) {
    String optionId = option.getAttribute(SdkConstants.ATTR_ID);
    assert optionId != null && !optionId.isEmpty() : SdkConstants.ATTR_ID;
    NodeList childNodes = option.getChildNodes();
    assert childNodes.getLength() == 1 && childNodes.item(0).getNodeType() == Node.TEXT_NODE;
    String optionLabel = childNodes.item(0).getNodeValue().trim();
    int minSdk = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_API, parameter.name, 1);
    int minBuildApi = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_BUILD_API, parameter.name, 1);
    return new ApiComboBoxItem(optionId, optionLabel, minSdk, minBuildApi);
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

  private static int addComponent(JComponent parent, JComponent component, int row, int column, boolean isLast) {
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

    return columnSpan;
  }

  private Map<Parameter, Object> getParameterObjectMap(Collection<Parameter> parameters,
                                                       Map<Parameter, Object> parametersWithDefaultValues,
                                                       Map<Parameter, Object> parametersWithNonDefaultValues)
    throws CircularParameterDependencyException {
    Map<Parameter, Object> computedParameterValues =
      ParameterValueResolver.resolve(parameters, parametersWithNonDefaultValues, getImplicitParameters(), new DeduplicateValuesFunction());

    Map<Parameter, Object> allParameterValues = Maps.newHashMap(parametersWithDefaultValues);
    for (Map.Entry<Parameter, Object> entry : computedParameterValues.entrySet()) {
      if (!parametersWithNonDefaultValues.keySet().contains(entry.getKey()) && entry.getValue() != null) {
        allParameterValues.put(entry.getKey(), entry.getValue());
      }
    }
    return allParameterValues;
  }

  private Map<String, Object> getImplicitParameters() {
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<String, Object>();
    for (Key<?> parameter : AddAndroidActivityPath.IMPLICIT_PARAMETERS) {
      Object value = myState.get(parameter);
      if (value != null) {
        builder.put(parameter.name, value);
      }
    }
    return builder.build();
  }

  @Override
  public boolean isStepVisible() {
    return myState.get(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE) != null &&
           !myState.getNotNull(IS_LIBRARY_KEY, false) &&
           super.isStepVisible();
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
          label = null;
          dataComponent = new JCheckBox(parameter.name);
          break;
        case ENUM:
          dataComponent = createEnumCombo(parameter);
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
    if (!StringUtil.isEmpty(parameter.help) && dataComponent.getAccessibleContext() != null) {
      dataComponent.getAccessibleContext().setAccessibleDescription(parameter.help);
    }
    register(parameter, dataComponent);
    if (label != null) {
      label.setLabelFor(dataComponent);
    }
    return label != null ? Arrays.asList(label, dataComponent) : Arrays.asList(dataComponent);
  }

  private JComponent createClassEntry(@NotNull Parameter parameter, @NotNull Module module) {
    ChooseClassAction browseAction = new ChooseClassAction(parameter, module);
    String historyKey = AddAndroidActivityPath.getRecentHistoryKey(parameter.id);
    // Need to add empty entry to the history, otherwise it will select entry used last
    RecentsManager.getInstance(module.getProject()).registerRecentEntry(historyKey, "");
    ReferenceEditorComboWithBrowseButton control =
        new ReferenceEditorComboWithBrowseButton(browseAction, "", module.getProject(), true,
                                                 new OnlyShowActivities(module), historyKey);
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
    myDataComponentParameters.put(dataComponent, parameter);
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
          return component.getDocument();
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
  protected JLabel getDescriptionLabel() {
    return myParameterDescription;
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    if (myCurrentTemplate != null) {
      updateStateWithDefaults(myCurrentTemplate.getParameters());
      updateControlsEnabled();
      updateControlsVisibility();
    }
  }

  private void updateControlsEnabled() {
    if (myUpdatingDefaults) {
      return;
    }
    Map<String, Object> contextValues = getContextValues();
    for (Parameter parameter : myCurrentTemplate.getParameters()) {
      String enabledStr = parameter.enabled;
      if (!StringUtil.isEmpty(enabledStr)) {
        boolean enabled = myEvaluator.evaluateBooleanExpression(enabledStr, contextValues, true);
        List<JComponent> components = myParameterComponents.get(parameter);
        if (components != null) {
          for (JComponent component : components) {
            Parameter componentParameter = myDataComponentParameters.get(component);
            if (!enabled && componentParameter != null) {
              myState.remove(getParameterKey(componentParameter));
              updateStateWithDefaults(Sets.newHashSet(componentParameter));
            }
            component.setEnabled(enabled);
          }
        }
      }
    }
  }

  private void updateControlsVisibility() {
    if (myUpdatingDefaults) {
      return;
    }
    Map<String, Object> contextValues = getContextValues();
    for (Parameter parameter : myCurrentTemplate.getParameters()) {
      String visibility = parameter.visibility;
      if (!StringUtil.isEmpty(visibility)) {
        boolean visible = myEvaluator.evaluateBooleanExpression(visibility, contextValues, true);
        List<JComponent> components = myParameterComponents.get(parameter);
        if (components != null) {
          for (JComponent component : components) {
            component.setVisible(visible);
          }
        }
      }
    }
  }

  private Map<String, Object> getContextValues() {
    Map<String, Object> values = Maps.newHashMap();
    for (Key key : myState.getAllKeys()) {
      values.put(key.name, myState.get(key));
    }
    return values;
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
        String error = param.validate(getProject(), getModule(), myState.get(AddAndroidActivityPath.KEY_SOURCE_PROVIDER),
                                      myState.get(myPackageNameKey), value != null ? value : "", getRelatedValues(param));
        if (error != null) {
          // Highlight?
          setErrorHtml(error);
          return false;
        }

        // Check to see that the selection's constraints are met if this is a combo box
        if (value instanceof ApiComboBoxItem) {
          ApiComboBoxItem selectedItem = (ApiComboBoxItem)value;

          if (minApi == null || buildApi == null) {
            return false;
          }

          String message = selectedItem.validate(minApi.getFeatureLevel(), buildApi);
          if (message != null) {
            setErrorHtml(message);
            return false;
          }
        }
      }
    }
    return true;
  }

  private Set<Object> getRelatedValues(Parameter param) {
    Set<Object> relatedValues = Sets.newHashSet();
    for (Parameter related : param.template.getRelatedParams(param)) {
      relatedValues.add(getStateParameterValue(related));
    }
    return relatedValues;
  }

  @Override
  public void init() {
    super.init();
    if (mySourceProviders.length > 0) {
      myState.put(AddAndroidActivityPath.KEY_SOURCE_PROVIDER, mySourceProviders[0]);
      myState.put(AddAndroidActivityPath.KEY_SOURCE_PROVIDER_NAME, mySourceProviders[0].getName());
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
    register(KEY_TEMPLATE_ICON, myTemplateIcon, new ComponentBinding<File, JLabel>() {
      @Override
      public void setValue(@Nullable File newValue, @NotNull JLabel component) {
        Optional<Icon> thumbnail = newValue == null ? Optional.<Icon>absent() : myThumbnailsCache.getUnchecked(newValue);
        Icon icon = thumbnail.orNull();
        component.setIcon(icon);
        component.setVisible(icon != null);
      }
    });
    registerValueDeriver(KEY_TEMPLATE_ICON, new ValueDeriver<File>() {
      @Nullable
      @Override
      public File deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable File currentValue) {
        return getTemplateIconPath(state.get(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE));
      }
    });
    registerValueDeriver(AddAndroidActivityPath.KEY_SOURCE_PROVIDER_NAME, new ValueDeriver<String>() {
      @Nullable
      @Override
      public String deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable String currentValue) {
        SourceProvider sourceProvider = state.get(AddAndroidActivityPath.KEY_SOURCE_PROVIDER);
        return sourceProvider == null ? null : sourceProvider.getName();
      }
    });
  }

  @Nullable
  private File getTemplateIconPath(@Nullable TemplateEntry entry) {
    if (entry == null) {
      return null;
    }
    String path = entry.getMetadata().getThumbnailPath(new Function<String, Object>() {
      @Override
      public Object apply(String variableName) {
        Map<String, Object> contextValues = getContextValues();
        Object value = contextValues.get(variableName);
        if (value == null) {
          LOG.warn("Thumbnail variable not found: " + variableName);
        }
        return value != null ? value.toString() : null;
      }
    });
    if (!StringUtil.isEmpty(path)) {
      File file = new File(entry.getTemplateDir(), FileUtilRt.toSystemDependentName(path, '/'));
      return file.isFile() ? file : null;
    }
    else {
      return null;
    }
  }

  private void setSelectedTemplate(@Nullable TemplateEntry template) {
    if (template == null) {
      return;
    }
    TemplateMetadata metadata = template.getMetadata();
    myTemplateIcon.setText(template.getTitle());

    String string = ImportUIUtil.makeHtmlString(metadata.getDescription());
    myTemplateDescription.setText(string);
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
    int lastRow = addParameterComponents(parameters.size() + 1, parameters);
    addSourceSetControls(lastRow);
  }

  private int addParameterComponents(final int rowCount, final Set<Parameter> parameters) {
    CellLocation location = new CellLocation();
    myTemplateParameters.removeAll();
    GridLayoutManager layout = new GridLayoutManager(rowCount + 1, COLUMN_COUNT);
    layout.setSameSizeHorizontally(false);
    myTemplateParameters.setLayout(layout);

    for (final Parameter parameter : parameters) {
      addComponents(parameter, location);
    }
    if (location.column > 0) {
      //add spacers before moving to the next row.
      if (location.column < COLUMN_COUNT) {
        addComponent(myTemplateParameters, new Spacer(), location.row, location.column, true);
      }
      location.row++;
    }
    return location.row;
  }

  private static class CellLocation {
    public int row = 0, column = 0;
  }

  private void addSourceSetControls(int row) {
    if (mySourceProviders.length > 1) {
      if (mySourceSetLabel == null) {
        mySourceSetLabel = new JLabel("Target Source Set:");
        //noinspection UndesirableClassUsage
        mySourceSet = new JComboBox();
        register(AddAndroidActivityPath.KEY_SOURCE_PROVIDER, mySourceSet);
        setControlDescription(mySourceSet, "The selected folder contains multiple source sets, " +
                                           "this can include source sets that do not yet exist on disk. " +
                                           "Please select the target source set in which to create the files.");
      }
      mySourceSet.removeAllItems();
      for (SourceProvider sourceProvider : mySourceProviders) {
        //noinspection unchecked
        mySourceSet.addItem(new ApiComboBoxItem(sourceProvider, sourceProvider.getName(), 0, 0));
      }
      addComponent(myTemplateParameters, mySourceSetLabel, row, 0, false);
      addComponent(myTemplateParameters, mySourceSet, row, 1, true);
    }
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
      try {
        Map<Parameter, Object> parameterDefaults = refreshParameterDefaults(parameters, myParameterDefaultValues);
        for (Map.Entry<Parameter, Object> entry : parameterDefaults.entrySet()) {
          myState.unsafePut(getParameterKey(entry.getKey()), entry.getValue());
          myParameterDefaultValues.put(entry.getKey().id, entry.getValue());
        }
      }
      catch (CircularParameterDependencyException exception) {
        LOG.error("Circular dependency between parameters in template %1$s", exception, myCurrentTemplate.getTitle());
      }
    }
    finally {
      myUpdatingDefaults = false;
    }
  }

  private Map<Parameter, Object> refreshParameterDefaults(Collection<Parameter> parameters, Map<String, Object> defaultValues)
    throws CircularParameterDependencyException {
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

  private void addComponents(Parameter parameter, CellLocation location) {
    List<JComponent> keyComponents = createComponents(parameter);

    // If a group of components take a full row, we ensure we are on
    // a fresh row at the start, and also ensure we end on a new row
    // for the next component.
    // We only group components together on the same row if both
    // component sets indicate they allow it.
    // Right now, our indication for requiring a full row is simply
    // if the # of components is > 1.  Only checkbox is allowed to
    // share a row.
    boolean isFullRow = keyComponents.size() > 1;

    // We start a new row if these components are "fullrow" while on an previously used row
    // or if there isn't enough space.
    if ((isFullRow && location.column > 0)
        || location.column + keyComponents.size() > COLUMN_COUNT) {

      // Add spacers before moving to the next row.
      if (location.column < COLUMN_COUNT) {
        addComponent(myTemplateParameters, new Spacer(), location.row, location.column, true);
      }
      location.column = 0;
      location.row++;
    }

    // For any component that didn't return a label (checkbox for now), we manually add a null label here to keep the layout the same.
    if (location.column == 0 && keyComponents.size() == 1 && keyComponents.get(0) instanceof JCheckBox) {
      location.column += addComponent(myTemplateParameters, new JLabel(), location.row, location.column, false);
    }

    myParameterComponents.put(parameter, keyComponents);
    for (Iterator<JComponent> iterator = keyComponents.iterator(); iterator.hasNext(); ) {
      JComponent keyComponent = iterator.next();
      location.column += addComponent(myTemplateParameters, keyComponent, location.row, location.column,
                                      isFullRow && !iterator.hasNext() );
      setControlDescription(keyComponent, parameter.help);
    }

    if (isFullRow) {
      location.row++;
      location.column = 0;
    }
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Template parameters";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return myStepTitle;
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Nullable
  @Override
  protected Icon getStepIcon() {
    return myFormFactor == null ? null : myFormFactor.getIcon();
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
    private final Module myModule;

    public OnlyShowActivities(Module module) {
      myModule = module;
    }

    private static boolean isActivitySubclass(@NotNull PsiClass classDecl) {
      for (PsiClass superClass : classDecl.getSupers()) {
        String typename = superClass.getQualifiedName();
        if (SdkConstants.CLASS_ACTIVITY.equals(typename) || isActivitySubclass(superClass)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Visibility isDeclarationVisible(PsiElement declaration, @Nullable PsiElement place) {
      if (declaration instanceof PsiClass) {
        PsiClass classDecl = (PsiClass)declaration;
        if (PsiClassUtil.isRunnableClass(classDecl, true, true) &&
            isActivitySubclass(classDecl) && isOnClasspath(classDecl)) {
          return Visibility.VISIBLE;
        }
      }
      return Visibility.NOT_VISIBLE;
    }

    private boolean isOnClasspath(@NotNull PsiClass classDecl) {
      GlobalSearchScope scope = myModule.getModuleWithDependenciesAndLibrariesScope(false);
      VirtualFile file = classDecl.getContainingFile().getVirtualFile();
      return scope.contains(file);
    }
  }

  private class ChooseClassAction implements ActionListener {
    private Parameter myParameter;
    @NotNull private final Module myModule;

    public ChooseClassAction(@NotNull Parameter parameter, @NotNull Module module) {
      myParameter = parameter;
      myModule = module;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final OnlyShowActivities filter = new OnlyShowActivities(myModule);
      Project project = myModule.getProject();
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createWithInnerClassesScopeChooser("Select Activity", GlobalSearchScope.projectScope(project), new ClassFilter() {
                                              @Override
                                              public boolean isAccepted(PsiClass aClass) {
                                                return filter.isDeclarationVisible(aClass, null) ==
                                                       JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
                                              }
                                            }, null
        );
      //noinspection unchecked
      Key<String> key = (Key<String>)getParameterKey(myParameter);
      final String targetClassName = myState.get(key);
      if (targetClassName != null) {
        final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(targetClassName, GlobalSearchScope.allScope(project));
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

  private static class TemplateIconLoader extends CacheLoader<File, Optional<Icon>> {
    @Nullable
    @Override
    public Optional<Icon> load(@NotNull File key) {
      Logger log = Logger.getInstance(ActivityGalleryStep.class);
      try {
        if (key.isFile()) {
          BufferedImage image = ImageIO.read(key);
          if (image != null) {
            return Optional.<Icon>of(new ImageIcon(image.getScaledInstance(256, 256, Image.SCALE_SMOOTH)));
          }
          else {
            log.error("File " + key.getAbsolutePath() + " exists but is not a valid image");
          }
        }
        else {
          log.error("Image file " + key.getAbsolutePath() + " was not found");
        }
      }
      catch (IOException e) {
        log.warn(e);
      }
      return Optional.absent();
    }
  }

  private class DeduplicateValuesFunction implements ParameterValueResolver.Deduplicator {
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
      String suggested = value;
      String extension = FileUtilRt.getExtension(value);
      boolean hasExtension = !extension.isEmpty();
      int extensionOffset = value.length() - extension.length();
      //noinspection ForLoopThatDoesntUseLoopVariable
      for (int i = 2; !parameter.uniquenessSatisfied(project, module, provider, packageName, suggested, getRelatedValues(parameter)); i++) {
        if (hasExtension) {
          suggested = value.substring(0, extensionOffset) + i + value.substring(extensionOffset);
        }
        else {
          suggested = value + i;
        }
      }
      return suggested;
    }
  }
}
