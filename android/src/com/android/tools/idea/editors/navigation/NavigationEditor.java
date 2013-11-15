/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.navigation;

import com.android.navigation.*;
import com.android.tools.idea.editors.navigation.macros.Unifier;
import com.intellij.AppTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.navigation.Utilities.getPropertyName;
import static com.android.tools.idea.editors.navigation.Utilities.getMethodsByName;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class NavigationEditor implements FileEditor {
  public static final String TOOLBAR = "NavigationEditorToolbar";
  private static final Logger LOG = Logger.getInstance("#" + NavigationEditor.class.getName());
  private static final boolean DEBUG = false;
  private static final String NAME = "Navigation";
  private static final int INITIAL_FILE_BUFFER_SIZE = 1000;
  private static final int SCROLL_UNIT_INCREMENT = 20;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private NavigationModel myNavigationModel;
  private final Listener<NavigationModel.Event> myNavigationModelListener;
  private VirtualFile myFile;
  private JComponent myComponent;
  private boolean myDirty;

  public NavigationEditor(Project project, VirtualFile file) {
    // Listen for 'Save All' events
    FileDocumentManagerListener saveListener = new FileDocumentManagerAdapter() {
      @Override
      public void beforeAllDocumentsSaving() {
        try {
          saveFile();
        }
        catch (IOException e) {
          LOG.error("Unexpected exception while saving navigation file", e);
        }
      }
    };
    project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, saveListener);

    myFile = file;
    try {
      myNavigationModel =
        processModel(read(file), NavigationEditorPanel.getRenderingParams(project, file).myConfiguration.getModule(), project, file);
      // component = new NavigationModelEditorPanel1(project, file, read(file));
      NavigationEditorPanel editor = new NavigationEditorPanel(project, file, myNavigationModel);
      JBScrollPane scrollPane = new JBScrollPane(editor);
      scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
      JPanel p = new JPanel(new BorderLayout());

      JComponent controls = createToolbar(editor);
      p.add(controls, BorderLayout.NORTH);
      p.add(scrollPane);
      myComponent = p;
    }
    catch (FileReadException e) {
      myNavigationModel = new NavigationModel();
      {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel message = new JLabel("Invalid Navigation File");
        Font font = message.getFont();
        message.setFont(font.deriveFont(30f));
        panel.add(message, BorderLayout.NORTH);
        panel.add(new JLabel(e.getMessage()), BorderLayout.CENTER);
        myComponent = new JBScrollPane(panel);
      }
    }
    myNavigationModelListener = new Listener<NavigationModel.Event>() {
      @Override
      public void notify(@NotNull NavigationModel.Event event) {
        myDirty = true;
      }
    };
    myNavigationModel.getListeners().add(myNavigationModelListener);
  }

  @Nullable
  public static String getInnerText(String s) {
    if (s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    assert false;
    return null;
  }

  private static class FragmentEntry {
    public final String tag;
    public final String className;

    private FragmentEntry(String tag, String className) {
      this.tag = tag;
      this.className = className;
    }
  }

  @Nullable
  private static FragmentEntry findFragmentByTag(Collection<FragmentEntry> l, String tag) {
    for (FragmentEntry fragment : l) {
      if (tag.equals(fragment.tag)) {
        return fragment;
      }
    }
    return null;
  }

  private static NavigationModel processModel(final NavigationModel model, final Module module, Project project, VirtualFile file) {
    final Map<String, ActivityState> activities = getActivities(model);
    final Map<String, MenuState> menus = getMenus(model);

    final PsiMethod methodCallMacro = getMethodsByName(module, "com.android.templates.GeneralTemplates", "call")[0];
    final PsiMethod defineAssignment = getMethodsByName(module, "com.android.templates.GeneralTemplates", "defineAssignment")[0];
    final PsiMethod defineInnerClassMacro = getMethodsByName(module, "com.android.templates.GeneralTemplates", "defineInnerClass")[0];

    final PsiMethod installMenuItemClickMacro =
      getMethodsByName(module, "com.android.templates.InstallListenerTemplates", "installMenuItemClick")[0];
    final PsiMethod installItemClickMacro =
      getMethodsByName(module, "com.android.templates.InstallListenerTemplates", "installItemClickListener")[0];

    final PsiMethod getMenuItemMacro = getMethodsByName(module, "com.android.templates.MenuAccessTemplates", "getMenuItem")[0];
    final PsiMethod launchActivityMacro = getMethodsByName(module, "com.android.templates.LaunchActivityTemplates", "launchActivity")[0];
    final PsiMethod launchActivityMacro2 = getMethodsByName(module, "com.android.templates.LaunchActivityTemplates", "launchActivity")[1];

    //model.getTransitions().clear();

    final Collection<ActivityState> activityStates = activities.values();

    if (DEBUG) System.out.println("activityStates = " + activityStates);

    for (ActivityState state : activityStates) {
      String className = state.getClassName();
      final ActivityState finalState = state;

      if (DEBUG) System.out.println("className = " + className);

      // Look for menu inflation
      {
        PsiJavaCodeReferenceElement menu = Utilities.getReferenceElement(module, className, "onCreateOptionsMenu");
        if (menu != null) {
          MenuState menuState = menus.get(menu.getLastChild().getText());
          addTransition(model, new Transition("click", new Locator(state), new Locator(menuState)));

          // Look for menu bindings
          {
            PsiMethod[] methods = getMethodsByName(module, className, "onPrepareOptionsMenu");
            if (methods.length == 1) {
              PsiMethod onPrepareOptionsMenuMethod = methods[0];
              PsiStatement[] statements = onPrepareOptionsMenuMethod.getBody().getStatements();
              if (DEBUG) System.out.println("statements = " + Arrays.toString(statements));
              for (PsiStatement s : statements) {
                Map<String, PsiMethod> constraints = new HashMap<String, PsiMethod>();
                constraints.put("$menuItem", getMenuItemMacro);
                constraints.put("$f", launchActivityMacro);
                MultiMatchResult multiMatchResult = multiMatch(installMenuItemClickMacro, constraints, s.getFirstChild());
                if (multiMatchResult != null) {
                  Map<String, Map<String, PsiElement>> subBindings = multiMatchResult.subBindings;
                  ActivityState activityState =
                    activities.get(getQualifiedName(subBindings.get("$f").get("activityClass").getFirstChild()));
                  String menuItemName =
                    subBindings.get("$menuItem").get("$id").getLastChild().getText(); // e.g. $id=PsiReferenceExpression:R.id.action_account
                  addTransition(model, new Transition("click", Locator.of(menuState, menuItemName), new Locator(activityState)));
                }
              }
            }
          }
        }
      }

      // Examine fragments associated with this activity
      String xmlFileName = NavigationEditorPanel.getXMLFileName(module, state);
      XmlFile psiFile = (XmlFile)NavigationEditorPanel.getPsiFile(false, xmlFileName, file, project);
      final List<FragmentEntry> fragments = getFragmentEntries(psiFile);

      for (FragmentEntry fragment : fragments) {
        PsiClass fragmentClass = Utilities.getPsiClass(module, fragment.className);
        PsiMethod[] methods = getMethodsByName(module, fragment.className, "installListeners");
        if (methods.length == 1) {
          PsiMethod installListenersMethod = methods[0];
          PsiStatement[] statements = installListenersMethod.getBody().getStatements();
          for (PsiStatement s : statements) {
            Map<String, PsiMethod> constraints = new HashMap<String, PsiMethod>();
            constraints.put("$f", methodCallMacro);
            MultiMatchResult multiMatchResult = multiMatch(installItemClickMacro, constraints, s.getFirstChild());
            if (multiMatchResult != null) {
              final Map<String, PsiElement> bindings = multiMatchResult.bindings;
              final Map<String, Map<String, PsiElement>> subBindings = multiMatchResult.subBindings;
              final PsiElement $target = subBindings.get("$f").get("$target");
              fragmentClass.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                  //if (DEBUG) System.out.println("$target = " + $target);
                  //if (DEBUG) System.out.println("expression = " + expression);
                  if (expression.getLExpression().getText().equals($target.getText())) {
                    PsiExpression rExpression = expression.getRExpression();
                    if (DEBUG) System.out.println("expression.getRExpression() = " + rExpression);
                    Map<String, PsiElement> bindings3 = match(defineAssignment, rExpression);
                    if (bindings3 != null) {
                      if (DEBUG) System.out.println("bindings3 = " + bindings3);
                      PsiElement fragmentLiteral = bindings3.get("$fragmentName");
                      if (fragmentLiteral instanceof PsiLiteralExpression) {
                        String fragmentTag = getInnerText(fragmentLiteral.getText());
                        FragmentEntry fragment = findFragmentByTag(fragments, fragmentTag);
                        if (fragment != null) {
                          addTransition(model, new Transition("click", Locator.of(finalState, null),
                                                              Locator.of(finalState, fragment.tag))); // e.g. "messageFragment"
                          return;
                        }
                      }
                    }
                    Map<String, PsiMethod> constraints = new HashMap<String, PsiMethod>();
                    constraints.put("$f", launchActivityMacro2);
                    MultiMatchResult multiMatchResult1 = multiMatch(defineInnerClassMacro, constraints, rExpression);
                    if (multiMatchResult1 != null) {
                      PsiElement activityClass = multiMatchResult1.subBindings.get("$f").get("activityClass").getFirstChild();
                      State toState = activities.get(getQualifiedName(activityClass));
                      if (DEBUG) System.out.println("toState = " + toState);
                      if (toState != null) {
                        PsiElement $listView = bindings.get("$listView");
                        String viewName = $listView == null ? null : getPropertyName(removeTrailingParens($listView.getText()));
                        addTransition(model, new Transition("click", Locator.of(finalState, viewName), new Locator(toState)));
                      }
                    }
                  }
                }
              });
            }
          }
        }
      }
    }
    return model;
  }

  static class MultiMatchResult {
    final Map<String, PsiElement> bindings;
    final Map<String, Map<String, PsiElement>> subBindings;

    MultiMatchResult(Map<String, PsiElement> bindings, Map<String, Map<String, PsiElement>> subBindings) {
      this.bindings = bindings;
      this.subBindings = subBindings;
    }
  }

  @Nullable
  private static MultiMatchResult multiMatch(PsiMethod macro, Map<String, PsiMethod> subMacros, PsiElement element) {
    Map<String, PsiElement> bindings = match(macro, element);
    if (DEBUG) System.out.println("bindings = " + bindings);
    if (bindings == null) {
      return null;
    }
    Map<String, Map<String, PsiElement>> subBindings = new HashMap<String, Map<String, PsiElement>>();
    for (Map.Entry<String, PsiMethod> entry : subMacros.entrySet()) {
      String name = entry.getKey();
      PsiMethod template = entry.getValue();
      Map<String, PsiElement> subBinding = match(template, bindings.get(name));
      if (subBinding == null) {
        return null;
      }
      subBindings.put(name, subBinding);
    }
    return new MultiMatchResult(bindings, subBindings);
  }

  private static List<FragmentEntry> getFragmentEntries(XmlFile psiFile) {
    final List<FragmentEntry> fragments = new ArrayList<FragmentEntry>();
    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (tag.getName().equals("fragment")) {
          String fragmentTag = tag.getAttributeValue("android:tag");
          String fragmentClassName = tag.getAttributeValue("android:name");
          if (DEBUG) System.out.println("fragmentClassName = " + fragmentClassName);
          fragments.add(new FragmentEntry(fragmentTag, fragmentClassName));
        }
      }
    });
    return fragments;
  }

  private static String removeTrailingParens(String text) {
    return text.endsWith("()") ? text.substring(0, text.length() - 2) : text;
  }

  private static boolean addTransition(NavigationModel model, Transition transition) {
    if (DEBUG) System.out.println("Adding transition: " + transition);
    return model.add(transition);
  }

  @Nullable
  private static Map<String, PsiElement> match(PsiMethod method, PsiElement element) {
    return new Unifier().unify(method.getParameterList(), method.getBody().getStatements()[0].getFirstChild(), element);
  }

  @Nullable
  private static String getQualifiedName(PsiElement element) {
    if (element instanceof PsiTypeElement) {
      PsiTypeElement psiTypeElement = (PsiTypeElement)element;
      PsiType type1 = psiTypeElement.getType();
      if (type1 instanceof PsiClassReferenceType) {
        PsiClassReferenceType type = (PsiClassReferenceType)type1;
        PsiClass resolve = type.resolve();
        if (resolve != null) {
          return resolve.getQualifiedName();
        }
      }
    }
    return null;
  }

  private static Map<String, MenuState> getMenus(NavigationModel model) {
    Map<String, MenuState> menus = new HashMap<String, MenuState>();
    for (State state : model.getStates()) {
      if (state instanceof MenuState) {
        MenuState menuState = (MenuState)state;
        menus.put(state.getXmlResourceName(), menuState);
      }
    }
    return menus;
  }

  private static Map<String, ActivityState> getActivities(NavigationModel model) {
    Map<String, ActivityState> activities = new HashMap<String, ActivityState>();
    for (State state : model.getStates()) {
      if (state instanceof ActivityState) {
        ActivityState activityState = (ActivityState)state;
        activities.put(state.getClassName(), activityState);
      }
    }
    return activities;
  }


  // See  AndroidDesignerActionPanel
  protected JComponent createToolbar(NavigationEditorPanel myDesigner) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, getActions(myDesigner), true);
    JPanel bottom = new JPanel(new BorderLayout());
    //bottom.add(layoutToolBar.getComponent(), BorderLayout.WEST);
    bottom.add(zoomToolBar.getComponent(), BorderLayout.EAST);
    panel.add(bottom, BorderLayout.SOUTH);

    return panel;
  }

  private static class FileReadException extends Exception {
    private FileReadException(Throwable throwable) {
      super(throwable);
    }
  }

  // See AndroidDesignerActionPanel
  private static ActionGroup getActions(final NavigationEditorPanel myDesigner) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new AnAction(null, "Zoom Out (-)", AndroidIcons.ZoomOut) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(false);
      }
    });
    group.add(new AnAction(null, "Reset Zoom to 100% (1)", AndroidIcons.ZoomActual) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.setScale(1);
      }
    });
    group.add(new AnAction(null, "Zoom In (+)", AndroidIcons.ZoomIn) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(true);
      }
    });

    return group;
  }

  private static NavigationModel read(VirtualFile file) throws FileReadException {
    try {
      return (NavigationModel)new XMLReader(file.getInputStream()).read();
    }
    catch (Exception e) {
      throw new FileReadException(e);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return myDirty;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  private void saveFile() throws IOException {
    if (myDirty) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream(INITIAL_FILE_BUFFER_SIZE);
      new XMLWriter(stream).write(myNavigationModel);
      myFile.setBinaryContent(stream.toByteArray());
      myDirty = false;
    }
  }

  @Override
  public void dispose() {
    try {
      saveFile();
    }
    catch (IOException e) {
      LOG.error("Unexpected exception while saving navigation file", e);
    }

    myNavigationModel.getListeners().remove(myNavigationModelListener);
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
