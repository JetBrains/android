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

import com.android.SdkConstants;
import com.android.tools.idea.actions.AndroidShowNavigationEditor;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.editors.navigation.macros.CodeGenerator;
import com.android.tools.idea.editors.navigation.model.*;
import com.android.tools.idea.editors.navigation.model.NavigationModel.Event;
import com.android.tools.idea.editors.navigation.model.NavigationModel.Event.Operation;
import com.android.tools.idea.rendering.ModuleResourceRepository;
import com.intellij.AppTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.editors.navigation.NavigationView.GAP;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class NavigationEditor implements FileEditor {
  public static final String NAVIGATION_DIRECTORY = ".navigation";
  public static final String DEFAULT_RESOURCE_FOLDER = SdkConstants.FD_RES_RAW;
  public static final String LAYOUT_DIR_NAME = SdkConstants.FD_RES_LAYOUT;
  public static final String NAVIGATION_FILE_NAME = "main.nvg.xml";

  private static final String TOOLBAR = "NavigationEditorToolbar";
  private static final Logger LOG = Logger.getInstance(NavigationEditor.class.getName());
  private static final boolean DEBUG = false;
  private static final String NAME = "Navigation";
  private static final int INITIAL_FILE_BUFFER_SIZE = 1000;
  private static final int SCROLL_UNIT_INCREMENT = 20;
  private static final NavigationModel.Event PROJECT_READ = new Event(Operation.UPDATE, Object.class);
  private static final ModelDimension UNATTACHED_STRIDE = new ModelDimension(50, 50);

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private RenderingParameters myRenderingParams;
  private NavigationModel myNavigationModel;
  private final VirtualFile myFile;
  private JComponent myComponent;
  private boolean myModified;
  private boolean myPendingFileSystemChanges;
  private Analyser myAnalyser;
  private Listener<NavigationModel.Event> myNavigationModelListener;
  private ResourceFolderManager.ResourceFolderListener myResourceFolderListener;
  private VirtualFileAdapter myVirtualFileListener;
  private FileDocumentManagerListener mySaveListener;
  private static final String[] EXCLUDED_PATH_SEGMENTS = new String[]{"/.idea/", "/idea/config/options/"};

  @Nullable
  private static VirtualFile findLayoutFile(List<VirtualFile> resourceDirectories, String navigationDirectoryName) {
    String qualifier = removePrefixIfPresent(DEFAULT_RESOURCE_FOLDER, navigationDirectoryName);
    String layoutDirName = LAYOUT_DIR_NAME + qualifier;
    for (VirtualFile root : resourceDirectories) {
      for (VirtualFile dir : root.getChildren()) {
        if (dir.isDirectory() && dir.getName().equals(layoutDirName)) {
          for (VirtualFile file : dir.getChildren()) {
            String fileName = file.getName();
            if (!fileName.startsWith(".") && fileName.endsWith(".xml")) { // Ignore files like .DS_store on mac
              return file;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static RenderingParameters getRenderingParams(@NotNull VirtualFile file, @NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    List<VirtualFile> resourceDirectories = facet.getAllResourceDirectories();
    VirtualFile layoutFile = findLayoutFile(resourceDirectories, file.getParent().getName());
    if (layoutFile == null) {
      return null;
    }
    Configuration configuration = facet.getConfigurationManager().getConfiguration(layoutFile);
    return new RenderingParameters(facet, configuration);
  }

  public NavigationEditor(Project project, VirtualFile file) {
    myFile = file;
    Module[] androidModules = Utilities.getAndroidModules(project);
    String moduleName = file.getParent().getParent().getName();
    Module module = Utilities.findModule(androidModules, moduleName);
    if (module == null) {
      String errorMessage = NAVIGATION_DIRECTORY.equals(moduleName)
                            ? "Legacy navigation editor file: please remove the file and/or close this editor"
                            : "Android module \"" + moduleName + "\" not found";
      myComponent = createErrorComponent("", errorMessage);
      return;
    }
    RenderingParameters renderingParams = getRenderingParams(file, module);
    if (renderingParams == null) {
      myComponent = createErrorComponent("", "Invalid file name: please remove the file and/or close this editor");
      return;
    }
    myRenderingParams = renderingParams;
    myAnalyser = new Analyser(module);
    myNavigationModel = read(file);
    CodeGenerator codeGenerator = new CodeGenerator(myNavigationModel, module, new Listener<String>() {
      @Override
      public void notify(@NotNull String event) {
        postDelayedRefresh();
      }
    });
    myComponent = createUI(renderingParams, myNavigationModel, codeGenerator, myFile.getParent().getName());
    createListeners();
    project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, mySaveListener);
  }

  @NotNull
  private static JPanel createUI(RenderingParameters renderingParams,
                                 NavigationModel navigationModel,
                                 CodeGenerator codeGenerator,
                                 String dirName) {
    SelectionModel selectionModel = new SelectionModel();
    NavigationView editor = new NavigationView(renderingParams, navigationModel, selectionModel, codeGenerator);
    JPanel panel = new JPanel(new BorderLayout());
    {
      JComponent toolBar = createToolbar(getActions(editor), renderingParams, dirName);
      panel.add(toolBar, BorderLayout.NORTH);
    }
    {
      Splitter splitPane = new Splitter();
      splitPane.setDividerWidth(1);
      splitPane.setShowDividerIcon(false);
      splitPane.setProportion(.8f);
      {
        JBScrollPane scrollPane = new JBScrollPane(editor);
        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        splitPane.setFirstComponent(scrollPane);
      }
      {
        Inspector inspector = new Inspector(selectionModel);
        splitPane.setSecondComponent(new JBScrollPane(inspector.container));
      }
      panel.add(splitPane);
    }
    return panel;
  }

  private void createListeners() {
    // NavigationModel listener
    myNavigationModelListener = new Listener<NavigationModel.Event>() {
      @Override
      public void notify(@NotNull NavigationModel.Event event) {
        if (event != PROJECT_READ) { // exempt the case when we are updating the model ourselves (because of a file read)
          myModified = true;
        }
      }
    };

    // Document listener to listen for 'Save All' events
    mySaveListener = new FileDocumentManagerAdapter() {
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

    // Virtual File listener
    //noinspection UnusedParameters
    myVirtualFileListener = new VirtualFileAdapter() {
      private void somethingChanged(String changeType, @NotNull VirtualFileEvent event) {
        if (!shouldIgnore(event)) {
          postDelayedRefresh();
        }
      }

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        somethingChanged("contentsChanged", event);
      }

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        somethingChanged("fileCreated", event);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        somethingChanged("fileDeleted", event);
      }
    };

    // Resource folder listener
    myResourceFolderListener = new ResourceFolderManager.ResourceFolderListener() {
      @Override
      public void resourceFoldersChanged(@NotNull AndroidFacet facet,
                                         @NotNull List<VirtualFile> folders,
                                         @NotNull Collection<VirtualFile> added,
                                         @NotNull Collection<VirtualFile> removed) {
        postDelayedRefresh();
      }
    };
  }

  // See: https://code.google.com/p/android/issues/detail?id=75755
  private static boolean shouldIgnore(VirtualFileEvent event) {
    String fileName = event.getFileName();
    if (NAVIGATION_FILE_NAME.equals(fileName)) {
      return true;
    }
    String pathName = event.getFile().getCanonicalPath();
    if (pathName == null) {
      return false;
    }
    for (String segment : EXCLUDED_PATH_SEGMENTS) {
      if (pathName.contains(segment)) {
        return true;
      }
    }
    return false;
  }

  private static JComponent createErrorComponent(String title, String errorMessage) {
    JPanel panel = new JPanel(new BorderLayout());
    {
      JLabel label = new JLabel(title);
      label.setFont(label.getFont().deriveFont(30f));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(label, BorderLayout.NORTH);
    }
    {
      JLabel label = new JLabel(errorMessage);
      label.setFont(label.getFont().deriveFont(20f));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(label, BorderLayout.CENTER);
    }
    return new JBScrollPane(panel);
  }

  private static ResourceFolderManager getResourceFolderManager(AndroidFacet facet) {
    //if (facet.isGradleProject()) {
    // Ensure that the app resources have been initialized first, since
    // we want it to add its own variant listeners before ours (such that
    // when the variant changes, the project resources get notified and updated
    // before our own update listener attempts a re-render)
    ModuleResourceRepository.getModuleResources(facet, true /*createIfNecessary*/);
    return facet.getResourceFolderManager();
    //}
    //return null;
  }

  private void postDelayedRefresh() {
    if (DEBUG) System.out.println("NavigationEditor: postDelayedRefresh");
    // Post to the event queue to coalesce events and effect re-parse when they're all in
    if (!myPendingFileSystemChanges) {
      myPendingFileSystemChanges = true;
      final Application app = ApplicationManager.getApplication();
      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          app.executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              app.runReadAction(new Runnable() {
                @Override
                public void run() {
                  myPendingFileSystemChanges = false;
                  long l = System.currentTimeMillis();
                  updateNavigationModelFromProject();
                  if (DEBUG) System.out.println("Navigation Editor: model read took: " + (System.currentTimeMillis() - l) / 1000.0);
                }
              });
            }
          });
        }
      });
    }
  }

  // See  AndroidDesignerActionPanel

  private static String[] getModuleNames(Module[] modules) {
    String[] result = new String[modules.length];
    for (int i = 0; i < modules.length; i++) {
      result[i] = modules[i].getName();
    }
    return result;
  }

  private static String removePrefixIfPresent(String prefix, String s) {
    return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
  }

  private static String[] resourceDirectoryNames(AndroidFacet facet, String type) {
    List<VirtualFile> resourceDirectories = facet.getAllResourceDirectories();
    List<String> qualifiers = new ArrayList<String>();
    for (VirtualFile root : resourceDirectories) {
      for (VirtualFile dir : root.getChildren()) {
        String name = dir.getName();
        if (name.startsWith(type) && dir.isDirectory() && dir.getChildren().length != 0) {
          qualifiers.add(name);
        }
      }
    }
    return ArrayUtil.toStringArray(qualifiers);
  }

  private static String[] getDisplayNames(String[] dirNames) {
    return Utilities.map(dirNames, new Function<String, String>() {
      @Override
      public String fun(String s) {
        return DEFAULT_RESOURCE_FOLDER + s.substring(LAYOUT_DIR_NAME.length());
      }
    });
  }

  private static JComponent createToolbar(ActionGroup actions, final RenderingParameters renderingParams, String dirName) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // UI for module and configuration selection
    {
      final Module module = renderingParams.facet.getModule();

      JPanel combos = new JPanel(new FlowLayout());
      final Module[] androidModules = Utilities.getAndroidModules(renderingParams.project);
      // Module selector
      if (androidModules.length > 1) {
        final ComboBox moduleSelector = new ComboBox(getModuleNames(androidModules));
        final String originalSelection = module.getName();
        moduleSelector.setSelectedItem(originalSelection);
        moduleSelector.addActionListener(new ActionListener() {
          boolean disabled = false;

          @Override
          public void actionPerformed(ActionEvent event) {
            if (disabled) {
              return;
            }
            int selectedIndex = moduleSelector.getSelectedIndex();
            Module newModule = androidModules[selectedIndex];
            AndroidShowNavigationEditor newEditor = new AndroidShowNavigationEditor();
            newEditor.showNavigationEditor(renderingParams.project, newModule, DEFAULT_RESOURCE_FOLDER, NAVIGATION_FILE_NAME);
            disabled = true;
            moduleSelector.setSelectedItem(originalSelection); // put the selection back so it will be correct if this editor is revisited
            disabled = false;
          }
        });
        combos.add(moduleSelector);
      }
      // Configuration selector
      String[] dirNames = resourceDirectoryNames(renderingParams.facet, LAYOUT_DIR_NAME);
      String[] navDirNames = getDisplayNames(dirNames);
      if (dirNames.length > 1) {
        final ComboBox deviceSelector = new ComboBox(navDirNames);
        final String originalSelection = dirName;
        deviceSelector.setSelectedItem(originalSelection);
        deviceSelector.addActionListener(new ActionListener() {
          boolean disabled = false;

          @Override
          public void actionPerformed(ActionEvent event) {
            if (disabled) {
              return;
            }
            String dirName = (String)deviceSelector.getSelectedItem();
            AndroidShowNavigationEditor newEditor = new AndroidShowNavigationEditor();
            newEditor.showNavigationEditor(renderingParams.project, module, dirName, NAVIGATION_FILE_NAME);
            disabled = true;
            deviceSelector.setSelectedItem(originalSelection); // put the selection back so it will be correct if this editor is revisited
            disabled = false;
          }
        });
        combos.add(deviceSelector);
      }
      panel.add(combos, BorderLayout.CENTER);
    }
    // Zoom controls
    {
      ActionManager actionManager = ActionManager.getInstance();
      ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, actions, true);
      panel.add(zoomToolBar.getComponent(), BorderLayout.EAST);
    }
    // Link to on-line help
    {
      HyperlinkLabel label = new HyperlinkLabel();
      label.setHyperlinkTarget("http://tools.android.com/navigation-editor");
      label.setHyperlinkText("   ", "What's this?", "");
      panel.add(label, BorderLayout.WEST);
    }

    return panel;
  }

  // See AndroidDesignerActionPanel
  private static ActionGroup getActions(final NavigationView myDesigner) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new AnAction(null, "Zoom Out (-)", AndroidIcons.ZoomOut) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(-1);
      }
    });
    group.add(new AnAction(null, "Fit to screen", AndroidIcons.ZoomActual) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Dimension pref = myDesigner.getPreferredSize();
        Container parent = myDesigner.getParent();
        float ratio = Math.max((float)pref.width / parent.getWidth(), (float)pref.height / parent.getHeight());
        double power = Math.log(1 / ratio) / Math.log(NavigationView.ZOOM_FACTOR);
        myDesigner.zoom((int)Math.floor(power));
      }
    });
    group.add(new AnAction(null, "Zoom In (+)", AndroidIcons.ZoomIn) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(1);
      }
    });

    return group;
  }

  private static NavigationModel read(VirtualFile file) {
    try {
      InputStream inputStream = file.getInputStream();
      if (inputStream.available() == 0) {
        return new NavigationModel();
      }
      return (NavigationModel)new XMLReader(inputStream).read();
    }
    catch (Exception e) {
      return new NavigationModel(); // the file is in an old format. It's just x/y values; discard and start with a fresh model
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
    return myModified;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  private static void modifyCount(Map<State, Integer> m, Locator l, int delta) {
    State s = l.getState();
    m.put(s, m.get(s) + delta);
  }

  private static List<State> getStatesInOrder(final NavigationModel navigationModel) {
    List<State> states = navigationModel.getStates();
    final Map<State, Integer> ioCounts = new HashMap<State, Integer>();
    for (State s : states) {
      ioCounts.put(s, 0);
    }
    for (Transition t : navigationModel.getTransitions()) {
      modifyCount(ioCounts, t.getSource(), -1);
      modifyCount(ioCounts, t.getDestination(), 1);
    }
    Collections.sort(states, new Comparator<State>() {
      @Override
      public int compare(State s1, State s2) {
        return ioCounts.get(s1) - ioCounts.get(s2);
      }
    });
    return states;
  }

  private static void layoutStatesWithUnsetLocations(final NavigationModel navigationModel, ModelDimension size) {
    List<State> states = getStatesInOrder(navigationModel);
    final Map<State, ModelPoint> stateToLocation = navigationModel.getStateToLocation();
    final Set<State> visited = new HashSet<State>();
    ModelDimension gridSize = new ModelDimension(size.width + GAP.width, size.height + GAP.height);
    final Point location = new Point(GAP.width, GAP.height);
    final int gridWidth = gridSize.width;
    final int gridHeight = gridSize.height;
    // Gather childless roots and deal with them differently, there could be many of them
    Set<State> transitionStates = getTransitionStates(navigationModel);
    Collection<State> unattached = getNonTransitionStates(states, transitionStates);
    visited.addAll(unattached);
    for (State state : states) {
      new Object() {
        public void addChildrenFor(State source) {
          if (visited.contains(source)) {
            return;
          }
          visited.add(source);
          if (!stateToLocation.containsKey(source)) {
            stateToLocation.put(source, new ModelPoint(location.x, location.y));
          }
          List<State> children = navigationModel.findDestinationsFor(source);
          children.removeAll(visited);
          location.x += gridWidth;
          if (children.isEmpty()) {
            location.y += gridHeight;
          }
          else {
            for (State child : children) {
              addChildrenFor(child);
            }
          }
          location.x -= gridWidth;
        }
      }.addChildrenFor(state);
    }
    for (State root : unattached) {
      if (!stateToLocation.containsKey(root)) {
        stateToLocation.put(root, new ModelPoint(location.x, location.y));
        location.x += UNATTACHED_STRIDE.width;
        location.y += UNATTACHED_STRIDE.height;
      }
    }
  }

  private static Set<State> getTransitionStates(NavigationModel navigationModel) {
    Set<State> result = new HashSet<State>();
    for (Transition transition : navigationModel.getTransitions()) {
      State source = transition.getSource().getState();
      State destination = transition.getDestination().getState();
      result.add(source);
      result.add(destination);
    }
    return result;
  }

  private static Collection<State> getNonTransitionStates(Collection<State> states, Set<State> transitionStates) {
    Collection<State> unattached = new ArrayList<State>(states);
    unattached.removeAll(transitionStates);
    return unattached;
  }

  private void updateNavigationModelFromProject() {
    if (DEBUG) System.out.println("NavigationEditor: updateNavigationModelFromProject...");
    if (myRenderingParams == null || myRenderingParams.project.isDisposed()) {
      return;
    }
    final NavigationModel newModel = myAnalyser.getNavigationModel(myRenderingParams.configuration);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        EventDispatcher<Event> listeners = myNavigationModel.getListeners();
        myNavigationModel.clear();
        myNavigationModel.copyAllStatesAndTransitionsFrom(newModel);
        layoutStatesWithUnsetLocations(myNavigationModel, myRenderingParams.getDeviceScreenSize());
        myModified = false;
        listeners.notify(PROJECT_READ);
      }
    });
  }

  @Override
  public void selectNotify() {
    if (myRenderingParams != null) {
      AndroidFacet facet = myRenderingParams.facet;
      updateNavigationModelFromProject();
      VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
      getResourceFolderManager(facet).addListener(myResourceFolderListener);
      myNavigationModel.getListeners().add(myNavigationModelListener);
    }
  }

  @Override
  public void deselectNotify() {
    if (myRenderingParams != null) {
      AndroidFacet facet = myRenderingParams.facet;
      VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
      getResourceFolderManager(facet).removeListener(myResourceFolderListener);
      myNavigationModel.getListeners().remove(myNavigationModelListener);
    }
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
    if (myModified && myFile.isWritable()) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream(INITIAL_FILE_BUFFER_SIZE);
      new XMLWriter(stream).write(myNavigationModel);
      myFile.setBinaryContent(stream.toByteArray());
      myModified = false;
    }
  }

  /**
   * {@link #deselectNotify()} is called before this so we don't need to repeat de-registration of listeners here
   */
  @Override
  public void dispose() {
    try {
      saveFile();
    }
    catch (IOException e) {
      LOG.error("Unexpected exception while saving navigation file", e);
    }
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
