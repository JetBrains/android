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
import com.android.navigation.Dimension;
import com.android.navigation.*;
import com.android.navigation.NavigationModel.Event;
import com.android.navigation.NavigationModel.Event.Operation;
import com.android.tools.idea.actions.AndroidShowNavigationEditor;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.editors.navigation.macros.CodeGenerator;
import com.android.tools.idea.model.ManifestInfo;
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
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.Point;
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
  public static final String NAVIGATION_FILE_NAME = "main.nvg.xml";

  private static final String TOOLBAR = "NavigationEditorToolbar";
  private static final Logger LOG = Logger.getInstance("#" + NavigationEditor.class.getName());
  private static final boolean DEBUG = false;
  private static final String NAME = "Navigation";
  private static final int INITIAL_FILE_BUFFER_SIZE = 1000;
  private static final int SCROLL_UNIT_INCREMENT = 20;
  private static final NavigationModel.Event PROJECT_READ = new Event(Operation.UPDATE, Object.class);
  private static final com.android.navigation.Dimension UNATTACHED_STRIDE = new com.android.navigation.Dimension(50, 50);

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private RenderingParameters myRenderingParams;
  private NavigationModel myNavigationModel;
  private SelectionModel mySelectionModel = new SelectionModel();
  private final VirtualFile myFile;
  private JComponent myComponent;
  private CodeGenerator myCodeGenerator;
  private boolean myModified;
  private boolean myPendingFileSystemChanges;
  private Analyser myAnalyser;
  private Listener<NavigationModel.Event> myNavigationModelListener;
  private ResourceFolderManager.ResourceFolderListener myResourceFolderListener;
  private VirtualFileAdapter myVirtualFileListener;
  private final ErrorHandler myErrorHandler;
  private FileDocumentManagerListener mySaveListener;

  @Nullable
  public static RenderingParameters getRenderingParams(@NotNull Project project, @NotNull VirtualFile file, @NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    Configuration configuration = facet.getConfigurationManager().getConfiguration(file);
    ManifestInfo manifestInfo = ManifestInfo.get(module, false);
    String themeName = manifestInfo.getManifestTheme();
    configuration.setTheme(themeName);
    return new RenderingParameters(project, configuration, facet);
  }

  public NavigationEditor(Project project, VirtualFile file) {
    myFile = file;
    myErrorHandler = new ErrorHandler();
    Module[] androidModules = Utilities.getAndroidModules(project);
    String moduleName = file.getParent().getParent().getName();
    Module module = Utilities.findModule(androidModules, moduleName);
    if (module == null) {
      String errorMessage = NAVIGATION_DIRECTORY.equals(moduleName) ? "Old navigation file" : "Android module \"" + moduleName + "\" not found";
      myErrorHandler.handleError("", errorMessage);
    }
    else {
      initFor(project, file, module);
    }
    createListeners();
    project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, mySaveListener);
  }

  private void initFor(Project project, VirtualFile file, Module module) {
    init(file, getRenderingParams(project, file, module));
  }

  private void init(VirtualFile navigationModelFile, @Nullable RenderingParameters renderingParams) {
    if (renderingParams != null) {
      myRenderingParams = renderingParams;
      Configuration configuration = renderingParams.myConfiguration;
      Module module = configuration.getModule();
      myAnalyser = new Analyser(module);
      try {
        myNavigationModel = read(navigationModelFile);
        myCodeGenerator = new CodeGenerator(myNavigationModel, module);
        NavigationView editor = new NavigationView(renderingParams, myNavigationModel, mySelectionModel);
        // Create UI
        {
          JPanel panel = new JPanel(new BorderLayout());
          {
            JComponent toolBar = createToolbar(editor);
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
              Inspector inspector = new Inspector(mySelectionModel);
              splitPane.setSecondComponent(new JBScrollPane(inspector.container));
            }
            panel.add(splitPane);
          }
          myComponent = panel;
        }
      }
      catch (FileReadException e) {
        myErrorHandler.handleError("Invalid Navigation File", e.getMessage());
        if (DEBUG) {
          //noinspection CallToPrintStackTrace
          e.printStackTrace();
        }
      }
    }
  }

  private void createListeners() {
    // NavigationModel listener
    myNavigationModelListener = new Listener<NavigationModel.Event>() {
      @Override
      public void notify(@NotNull NavigationModel.Event event) {
        if (event.operation == Operation.INSERT && event.operandType == Transition.class) {
          ArrayList<Transition> transitions = myNavigationModel.getTransitions();
          Transition transition = transitions.get(transitions.size() - 1); // todo don't rely on this being the last
          myCodeGenerator.implementTransition(transition);
        }
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
    myVirtualFileListener = new VirtualFileAdapter() {
      private void somethingChanged(String changeType, @NotNull VirtualFileEvent event) {
        if (DEBUG) System.out.println("NavigationEditor: fileListener:: " + changeType + ": " + event);
        postDelayedRefresh();
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
        if (DEBUG) System.out.println("NavigationEditor: resourceFoldersChanged" + folders);
        postDelayedRefresh();
      }
    };
  }

  public class ErrorHandler {
    public void handleError(String title, String errorMessage) {
      myNavigationModel = new NavigationModel();
      {
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
        myComponent = new JBScrollPane(panel);
      }
    }
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

  protected JComponent createToolbar(NavigationView myDesigner) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // UI for module and configuration selection
    {
      final String dirName = myFile.getParent().getName();
      final Module module = myRenderingParams.myFacet.getModule();

      JPanel combos = new JPanel(new FlowLayout());
      final Module[] androidModules = Utilities.getAndroidModules(myRenderingParams.myProject);
      // Module selector
      if (androidModules.length > 1) {
        final ComboBox moduleSelector = new ComboBox(getModuleNames(androidModules));
        moduleSelector.setSelectedItem(module.getName());
        moduleSelector.addActionListener(new ActionListener() {
          boolean disabled = false;

          @Override
          public void actionPerformed(ActionEvent actionEvent) {
            if (disabled) {
              return;
            }
            int selectedIndex = moduleSelector.getSelectedIndex();
            Module newModule = androidModules[selectedIndex];
            new AndroidShowNavigationEditor().showNavigationEditor(myRenderingParams.myProject, newModule, DEFAULT_RESOURCE_FOLDER,
                                                                   NAVIGATION_FILE_NAME);
            disabled = true;
            moduleSelector.setSelectedItem(module.getName());
            disabled = false;
          }
        });
        combos.add(moduleSelector);
      }
      // Configuration selector
      {
        final String phone = "phone";
        final String tablet = "tablet";
        final ComboBox deviceSelector = new ComboBox(new Object[]{phone, tablet});
        final String portrait = "portrait";
        final String landscape = "landscape";
        final ComboBox orientationSelector = new ComboBox(new Object[]{portrait, landscape});
        deviceSelector.setSelectedItem(dirName.contains("-sw600dp") ? tablet : phone);
        orientationSelector.setSelectedItem(dirName.contains("-land") ? landscape : portrait);
        ActionListener actionListener = new ActionListener() {
          boolean disabled = false;

          @Override
          public void actionPerformed(ActionEvent actionEvent) {
            if (disabled) {
              return;
            }
            Object device = deviceSelector.getSelectedItem();
            Object deviceQualifier = (device == tablet) ? "-sw600dp" : "";
            Object orientation = orientationSelector.getSelectedItem();
            Object orientationQualifier = (orientation == landscape) ? "-land" : "";
            new AndroidShowNavigationEditor()
              .showNavigationEditor(myRenderingParams.myProject, module,
                                    "raw" + deviceQualifier + orientationQualifier, NAVIGATION_FILE_NAME);
            disabled = true;
            deviceSelector.setSelectedItem(dirName.contains("-sw600dp") ? tablet : phone);
            orientationSelector.setSelectedItem(dirName.contains("-land") ? landscape : portrait);
            disabled = false;

          }
        };
        {
          deviceSelector.addActionListener(actionListener);
          combos.add(deviceSelector);
        }
        {
          orientationSelector.addActionListener(actionListener);
          combos.add(orientationSelector);
        }
      }
      panel.add(combos, BorderLayout.CENTER);
    }
    // Zoom controls
    {
      ActionManager actionManager = ActionManager.getInstance();
      ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, getActions(myDesigner), true);
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

  private static class FileReadException extends Exception {
    private FileReadException(Throwable throwable) {
      super(throwable);
    }
  }

  // See AndroidDesignerActionPanel
  private static ActionGroup getActions(final NavigationView myDesigner) {
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
      InputStream inputStream = file.getInputStream();
      if (inputStream.available() == 0) {
        return new NavigationModel();
      }
      return (NavigationModel)new XMLReader(inputStream).read();
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
    return myModified;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  private void layoutStatesWithUnsetLocations(NavigationModel navigationModel) {
    Collection<State> states = navigationModel.getStates();
    final Map<State, com.android.navigation.Point> stateToLocation = navigationModel.getStateToLocation();
    final Set<State> visited = new HashSet<State>();
    Dimension size = myRenderingParams.getDeviceScreenSize();
    Dimension gridSize = new Dimension(size.width + GAP.width, size.height + GAP.height);
    final Point location = new Point(GAP.width, GAP.height);
    final int gridWidth = gridSize.width;
    final int gridHeight = gridSize.height;
    // Gather childless roots and deal with them differently, there could be many of them
    Set<State> transitionStates = getTransitionStates();
    Collection<State> unattached = getNonTransitionStates(states, transitionStates);
    visited.addAll(unattached);
    for (State state : states) {
      if (visited.contains(state)) {
        continue;
      }
      new Object() {
        public void addChildrenFor(State source) {
          visited.add(source);
          if (!stateToLocation.containsKey(source)) {
            stateToLocation.put(source, new com.android.navigation.Point(location.x, location.y));
          }
          List<State> children = findDestinationsFor(source, visited);
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
      stateToLocation.put(root, new com.android.navigation.Point(location.x, location.y));
      location.x += UNATTACHED_STRIDE.width;
      location.y += UNATTACHED_STRIDE.height;
    }
  }

  private Set<State> getTransitionStates() {
    Set<State> result = new HashSet<State>();
    for (Transition transition : myNavigationModel.getTransitions()) {
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

  private List<State> findDestinationsFor(State source, Set<State> visited) {
    java.util.List<State> result = new ArrayList<State>();
    for (Transition transition : myNavigationModel.getTransitions()) {
      if (transition.getSource().getState() == source) {
        State destination = transition.getDestination().getState();
        if (!visited.contains(destination)) {
          result.add(destination);
        }
      }
    }
    return result;
  }

  private void updateNavigationModelFromProject() {
    if (DEBUG) System.out.println("NavigationEditor: updateNavigationModelFromProject...");
    if (myRenderingParams == null || myRenderingParams.myProject.isDisposed()) {
      return;
    }
    EventDispatcher<NavigationModel.Event> listeners = myNavigationModel.getListeners();
    boolean notificationWasEnabled = listeners.isNotificationEnabled();
    listeners.setNotificationEnabled(false);
    myNavigationModel.clear();
    myNavigationModel.getTransitions().clear();
    myAnalyser.deriveAllStatesAndTransitions(myNavigationModel, myRenderingParams.myConfiguration);
    layoutStatesWithUnsetLocations(myNavigationModel);
    listeners.setNotificationEnabled(notificationWasEnabled);

    myModified = false;
    listeners.notify(PROJECT_READ);
  }

  @Override
  public void selectNotify() {
    if (myRenderingParams != null) {
      AndroidFacet facet = myRenderingParams.myFacet;
      updateNavigationModelFromProject();
      VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
      getResourceFolderManager(facet).addListener(myResourceFolderListener);
      myNavigationModel.getListeners().add(myNavigationModelListener);
    }
  }

  @Override
  public void deselectNotify() {
    if (myRenderingParams != null) {
      AndroidFacet facet = myRenderingParams.myFacet;
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
    if (myModified) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream(INITIAL_FILE_BUFFER_SIZE);
      new XMLWriter(stream).write(myNavigationModel);
      myFile.setBinaryContent(stream.toByteArray());
      myModified = false;
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
