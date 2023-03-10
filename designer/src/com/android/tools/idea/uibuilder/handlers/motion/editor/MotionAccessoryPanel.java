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

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.ide.common.gradle.Version;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlDependencyManager;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.MotionUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MESaveGif;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This provides the main MotionEditor Panel and interfaces it with the rest of the system.
 */
public class MotionAccessoryPanel implements AccessoryPanelInterface, MotionLayoutInterface, MotionDesignSurfaceEdits {
  private static final boolean DEBUG = false;
  private static final boolean TEMP_HACK_FORCE_APPLY = false;
  private final Project myProject;
  private final NlDesignSurface myDesignSurface;
  private final ResourceNotificationManager.ResourceChangeListener myResourceListener;
  private final NlComponentTag myMotionLayoutTag;
  private final NlComponent myMotionLayoutNlComponent;
  MotionSceneTag myMotionScene;
  VirtualFile myMotionSceneFile;
  ViewGroupHandler.AccessoryPanelVisibility mVisibility;
  MotionEditor mMotionEditor = new MotionEditor();
  public static final String TIMELINE = "Timeline";

  MotionLayoutComponentHelper myMotionHelper;
  private String mSelectedStartConstraintId;
  private String mSelectedEndConstraintId;
  private float mLastProgress = 0;

  private final List<AccessorySelectionListener> myListeners;
  private NlComponent mySelection;
  private NlComponent myMotionLayout;
  private MotionEditorSelector.Type mLastSelection = MotionEditorSelector.Type.LAYOUT;
  private MTag[] myLastSelectedTags;
  private boolean mShowPath = true;
  private boolean myUpdatingSelectionInLayoutEditor = false;
  private boolean myUpdatingSelectionFromLayoutEditor = false;

  // For screen rotation when draging in timeline panel
  private float myStartDegree = Float.NaN;
  private float myEndDegree = Float.NaN;

  // For saving a selected transition as a GIF file.
  private static final int NUM_SAVED_IMAGES = 60;
  private static final int WAIF_FOR_TIME = 100;
  private static final int GIF_PLAY_DELAY = 80;
  private MESaveGif mySaveGif;

  private void applyMotionSceneValue(boolean apply) {
    if (TEMP_HACK_FORCE_APPLY) {
      if (apply) {
        String applyMotionSceneValue = myMotionLayoutNlComponent.getAttribute(SdkConstants.TOOLS_URI, "applyMotionScene");
        if (applyMotionSceneValue != null && applyMotionSceneValue.equals("false")) {
          WriteCommandAction.runWriteCommandAction(myProject, () -> {
            // let's get rid of it, as it's the default.
            myMotionLayoutTag.mComponent.setAttribute(SdkConstants.TOOLS_URI, "applyMotionScene", null);
          });
        }
      } else {
        WriteCommandAction.runWriteCommandAction(myProject, () -> {
          myMotionLayoutTag.mComponent.setAttribute(SdkConstants.TOOLS_URI, "applyMotionScene", "false");
        });
      }
    }
  }

  public MotionAccessoryPanel(@NotNull NlDesignSurface surface,
                              @NotNull NlComponent parent,
                              @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    if (DEBUG) {
      Debug.log("MotionAccessoryPanel created: " + this + "  parent: " + parent);
    }
    myDesignSurface = surface;
    myProject = surface.getProject();
    myMotionLayoutNlComponent = parent;
    myMotionLayoutTag = new NlComponentTag(parent, null);
    mVisibility = visibility;
    MotionLayoutComponentHelper.clearCache();
    myMotionHelper = MotionLayoutComponentHelper.create(myMotionLayoutNlComponent);
    myListeners = new ArrayList<>();
    myResourceListener = createResourceChangeListener();

    mMotionEditor.myTrack.init(myDesignSurface);
    SelectionModel designSurfaceSelection = myDesignSurface.getSelectionModel();
    List<NlComponent> dsSelection = designSurfaceSelection.getSelection();
    designSurfaceSelection.addListener((model, selection) -> handleSelectionChanged(model, selection));
    mMotionEditor.addCommandListener(new MotionEditor.Command() {

      @Override
      public void perform(Action action, MTag[] tag) {
        switch (action) {
          case DELETE: {
            for (int i = 0; i < tag.length; i++) {
              tag[i].getTagWriter().deleteTag().commit("delete " + tag[i].getTagName());
            }
          }
          break;
          case COPY:
            CharSequence[] buff = new CharSequence[tag.length];
            for (int i = 0; i < tag.length; i++) {
              buff[i] = MTag.serializeTag(tag[i]);
            }
            break;
        }
      }
    });
    mMotionEditor.addSelectionListener(new MotionEditorSelector.Listener() {
      @Override
      public void selectionChanged(MotionEditorSelector.Type selection, MTag[] tag, int flags) {
        if (DEBUG) {
          Debug.logStack("Selection changed " + selection, 23);
        }
        mLastSelection = selection;
        myLastSelectedTags = tag;
        switch (selection) {
          case CONSTRAINT_SET:
            String id = tag[0].getAttributeValue("id");
            if (DEBUG) {
              Debug.log("id of constraint set " + id);
            }
            if (id != null) {
              mSelectedStartConstraintId = Utils.stripID(id);
              mSelectedEndConstraintId = null;
              myMotionHelper.setState(mSelectedStartConstraintId);
            }
            if (TEMP_HACK_FORCE_APPLY) {
              applyMotionSceneValue(true);
            }
            break;
          case TRANSITION:
            mSelectedStartConstraintId = Utils.stripID(tag[0].getAttributeValue("constraintSetStart"));
            mSelectedEndConstraintId = Utils.stripID(tag[0].getAttributeValue("constraintSetEnd"));
            myMotionHelper.setTransition(mSelectedStartConstraintId, mSelectedEndConstraintId);
            myMotionHelper.setProgress(mLastProgress);
            if (flags == MotionEditorSelector.Listener.CONTROL_FLAG) {
              mShowPath = !mShowPath;
            }
            myMotionHelper.setShowPaths(mShowPath);

            break;
          case LAYOUT:
            if (TEMP_HACK_FORCE_APPLY) {
              applyMotionSceneValue(false);
            }
            selectOnDesignSurface(tag);
            if (DEBUG) {
              Debug.log("LAYOUT myMotionHelper.setState(null); ");
            }
            myMotionHelper.setState(null);
            mSelectedStartConstraintId = null;
            break;
          case CONSTRAINT:
            // TODO: This should always be a WrapMotionScene (remove this code when bug is fixed):
            selectOnDesignSurface(tag);
            if (tag[0] instanceof MotionSceneTag) {
              MotionSceneTag msTag = ((MotionSceneTag)tag[0]);
              id = Utils.stripID(msTag.getAttributeValue("id"));
              MTag[] layoutViews = myMotionLayoutTag.getChildTags();
              for (int i = 0; i < layoutViews.length; i++) {
                MTag view = layoutViews[i];
                String vid = Utils.stripID(view.getAttributeValue("id"));
                if (vid.equals(id)) {
                  updateSelectionInLayoutEditor((NlComponentTag)view);
                }
              }
            } else if (tag[0] instanceof NlComponentTag) {
              updateSelectionInLayoutEditor((NlComponentTag)tag[0]);
            }
            break;
          case LAYOUT_VIEW:
            if (tag.length > 0 && tag[0] instanceof NlComponentTag) {
              updateSelectionInLayoutEditor((NlComponentTag)tag[0]);
            }
            break;
          case KEY_FRAME_GROUP:
            // The NelePropertiesModel should be handling the properties in these cases...
            break;
        }
        if (!mMotionEditor.isUpdatingModel()) {
          fireSelectionChanged(Collections.singletonList(mySelection));
        }
      }
    });
    mMotionEditor.addTimeLineListener(new MotionEditorSelector.TimeLineListener() {
      @Override
      public void command(MotionEditorSelector.TimeLineCmd cmd, float pos) {
        switch (cmd) {
          case MOTION_PROGRESS: {
            myMotionHelper.setProgress(pos);
            mLastProgress = pos;
            applyRotation();
          }  break;
          case MOTION_SCRUB:
            surface.setAnimationScrubbing(true);
            startScreenRotating();
            //noinspection fallthrough
          case MOTION_PLAY: {
            LayoutlibSceneManager manager = surface.getSceneManager();
            manager.updateSceneView();
            manager.requestLayoutAndRenderAsync(false);
            surface.setRenderSynchronously(true);
          }  break;
          case MOTION_STOP: {
            surface.setRenderSynchronously(false);
            surface.setAnimationScrubbing(false);
            LayoutlibSceneManager manager = surface.getSceneManager();
            manager.requestLayoutAndRenderAsync(false);
            stopScreenRotating();
          } break;
          case MOTION_CAPTURE: {
            int playMode = mMotionEditor.getPlayMode();
            // when it's yoyo mode (playMode: 2), we need to save images for both forward and backward
            int numImages = playMode == 2 ? NUM_SAVED_IMAGES : NUM_SAVED_IMAGES / 2;
            float speed = mMotionEditor.getTimeLineSpeed();
            int playDelay  = (int)(GIF_PLAY_DELAY / speed);
            String filename = mSelectedStartConstraintId + "->" + mSelectedEndConstraintId + ".gif";
            LayoutlibSceneManager manager = surface.getSceneManager();
            manager.updateSceneView();
            manager.requestLayoutAndRenderAsync(false);
            surface.setRenderSynchronously(true);
            FileSaverDescriptor descriptor = new FileSaverDescriptor("Save Transition as GIF",
                                                                     "Save the selected transition as GIF",
                                                                     "gif");
            FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, (Project)null);
            VirtualFileWrapper fileWrapper = saveFileDialog.save(filename);
            if (fileWrapper != null) {
              File file = fileWrapper.getFile();
              mySaveGif = new MESaveGif(file, playDelay, true, "Written by Android Studio");
              mySaveGif.saveGif(myDesignSurface, numImages, playMode, myMotionHelper,
                                myProject, WAIF_FOR_TIME);
            }
          } break;
        }
      }
    });
    MotionSceneTag.Root motionScene = getMotionScene(myMotionLayoutNlComponent);

    myMotionScene = motionScene;

    myMotionSceneFile = (motionScene == null) ? null : motionScene.mVirtualFile;
    final VirtualFile sceneFile = myMotionSceneFile;
    String sceneFileName = (sceneFile == null) ? "" : sceneFile.getName();
    String layoutFileName = getLayoutFileName(surface);
    mMotionEditor.setMTag(myMotionScene, myMotionLayoutTag, layoutFileName, sceneFileName, getSetupError());
    if (myMotionScene == null) {
      return;
    }
    MTag[] cSet = myMotionScene.getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET);
    if (DEBUG) {
      Debug.log(" select constraint set " + cSet[0].getAttributeValue("id"));
    }
    if (cSet != null && cSet.length > 0) {
      mMotionEditor.selectTag(cSet[0], 0);
    }
    parent.putClientProperty(TIMELINE, this);
    if (DEBUG) {
      Debug.log("harness " + parent);
    }
    AndroidFacet facet = parent.getModel().getFacet();
    ResourceNotificationManager.getInstance(myProject).addListener(myResourceListener, facet, null, null);
    handleSelectionChanged(designSurfaceSelection, dsSelection);
  }

  @NotNull
  private static String getLayoutFileName(@NotNull NlDesignSurface surface) {
    NlModel model = surface.getModel();
    if (model == null) {
      return "";
    }
    return model.getVirtualFile().getName().replace(SdkConstants.DOT_XML, "");
  }

  @NotNull
  private ResourceNotificationManager.ResourceChangeListener createResourceChangeListener() {
    return new ResourceNotificationManager.ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull ImmutableSet<ResourceNotificationManager.Reason> reason) {
        // When a motion editor item is selected: the MTags of the MotionEditor must be updated (they are now stale).
        // When a layout view is selected: Ignore this notification. The properties panel is reading from XmlTags directly there is
        // no need to update the MTags of the MotionEditor. A selection change may have side effects for the Nele property panel if
        // this notification comes too early.
        boolean forLayout = mLastSelection == MotionEditorSelector.Type.LAYOUT || mLastSelection == MotionEditorSelector.Type.LAYOUT_VIEW;
        if (forLayout) {
          return;
        }
        mLastSelection = null;
        myLastSelectedTags = null;
        MotionSceneTag.Root motionScene = getMotionScene(myMotionLayoutNlComponent);
        if (motionScene != null) {
          myMotionScene = motionScene;
          myMotionSceneFile = motionScene.mVirtualFile;
          mMotionEditor.setMTag(myMotionScene, myMotionLayoutTag, "", "", getSetupError());

          if (myLastSelectedTags == null) {
            // The previous selection could not be restored.
            // Select something in the MotionScene to avoid the properties panel reverting back to the MotionLayout.
            selectSomething(motionScene);
          }
        }
        fireSelectionChanged(Collections.singletonList(mySelection));
        if (motionScene != null) {
          LayoutPullParsers.saveFileIfNecessary(motionScene.mXmlFile);
        }
      }
    };
  }

  private void updateSelectionInLayoutEditor(@NotNull NlComponentTag tag) {
    updateSelectionInLayoutEditor(Collections.singletonList(tag.mComponent));
  }

  private void updateSelectionInLayoutEditor(@NotNull List<NlComponent> selected) {
    if (myUpdatingSelectionInLayoutEditor || myUpdatingSelectionFromLayoutEditor) {
      return;
    }
    myUpdatingSelectionInLayoutEditor = true;
    try {
      myDesignSurface.getSelectionModel().setSelection(selected);
      myDesignSurface.repaint();
    }
    finally {
      myUpdatingSelectionInLayoutEditor = false;
    }
  }

  private void selectSomething(@NotNull MotionSceneTag motionScene) {
    MTag[] sets = motionScene.getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET);
    if (sets.length == 0) {
      // Nothing to select...
      return;
    }
    mLastSelection = MotionEditorSelector.Type.CONSTRAINT_SET;
    myLastSelectedTags = new MTag[]{sets[0]};
  }

  private String getSetupError() {
    GoogleMavenArtifactId artifact = GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT;
    NlDependencyManager dep = NlDependencyManager.getInstance();
    if (dep == null) return null;
    if (myMotionLayout == null) return null;
    if (myMotionLayout.getModel() == null) return null;

    Version v = dep.getModuleDependencyVersion(artifact, myMotionLayout.getModel().getFacet());
    NlDependencyManager.getInstance().getModuleDependencyVersion(artifact, myMotionLayout.getModel().getFacet());
    String error = "Version ConstraintLayout library must be version 2.0.0 beta3 or later";
    if (v == null) return null;
    if (v.compareTo(Version.Companion.parse("2.0.0-beta03")) < 0) return error;
    return null;
  }

  private void selectOnDesignSurface(MTag[] tag) {
    if (DEBUG) {
      Debug.log("Selection changed " + ((tag.length > 0) ? tag[0] : "empty"));
    }
    if (true) return;
    ArrayList<NlComponent> list = new ArrayList<>();
    for (int i = 0; i < tag.length; i++) {
      MTag mTag = tag[i];
      if (mTag instanceof NlComponentTag) {
        list.add(((NlComponentTag)mTag).mComponent);
      }
    }
    if (list.isEmpty()) {
      list.add(myMotionLayoutNlComponent);
    }

    if (DEBUG) {
      Debug.log(" set section " + tag.length + " " + tag[0].getTagName());
    }
    updateSelectionInLayoutEditor(list);
  }

  private void handleSelectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    if (myUpdatingSelectionInLayoutEditor || myUpdatingSelectionFromLayoutEditor) {
      // We initiated the selection change in the layout editor.
      // There is no need to adjust the selection here.
      return;
    }
    if (DEBUG) {
      Debug.log(" handleSelectionChanged ");
    }
    String[] ids = new String[selection.size()];
    int count = 0;
    for (NlComponent component : selection) {
      ids[count++] = Utils.stripID(component.getId());
    }
    myUpdatingSelectionFromLayoutEditor = true;
    try {
      mMotionEditor.selectById(ids);

      fireSelectionChanged(selection);
    }
    finally {
      myUpdatingSelectionFromLayoutEditor = false;
    }
  }

  @Nullable
  MotionSceneTag.Root getMotionScene(NlComponent motionLayout) {
    String ref = motionLayout.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
    if (ref == null) {
      System.err.println("getAttribute(layoutDescription ) returned null");
      return null;
    }
    int index = ref.lastIndexOf("@xml/");
    if (index < 0) {
      System.err.println("layoutDescription  did not have \"@xml/\"");
      return null;
    }
    String fileName = ref.substring(index + 5);
    if (fileName.isEmpty()) {
      System.err.println("layoutDescription \""+ref+"\"");
      return null;
    }

    // let's open the file
    AndroidFacet facet = motionLayout.getModel().getFacet();

    List<VirtualFile> resourcesXML = IdeResourcesUtil.getResourceSubdirs(ResourceFolderType.XML, StudioResourceRepositoryManager
      .getModuleResources(facet).getResourceDirs());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile virtualFile = null;
    for (VirtualFile dir : resourcesXML) {
      virtualFile = dir.findFileByRelativePath(fileName + ".xml");
      if (virtualFile != null) {
        break;
      }
    }
    if (virtualFile == null) {
      System.err.println("virtualFile == null");
      return null;
    }

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, virtualFile);
    return MotionSceneTag.parse(motionLayout, myProject, virtualFile, xmlFile, mMotionEditor.myTrack);
  }

  @NotNull
  @Override
  public JPanel getPanel() {
    return mMotionEditor;
  }

  @NotNull
  @Override
  public JPanel createPanel(AccessoryPanel.Type type) {
    return new JPanel() {{
      setBackground(Color.RED);
    }};
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type, @NotNull List<NlComponent> selection) {

    if (selection.isEmpty()) {
      mySelection = null;
      return;
    }

    mySelection = selection.get(0);
    myMotionLayout = MotionUtils.getMotionLayoutAncestor(mySelection);

    fireSelectionChanged(selection);
  }

  @Override
  public void deactivate() {
    mMotionEditor.stopAnimation();
    myMotionLayout = null;
    MotionLayoutComponentHelper.clearCache();
    AndroidFacet facet = myMotionLayoutNlComponent.getModel().getFacet();
    ResourceNotificationManager.getInstance(myProject).removeListener(myResourceListener, facet, null, null);
  }

  @Override
  public void updateAfterModelDerivedDataChanged() {
    MotionLayoutComponentHelper.clearCache();
    myMotionHelper = MotionLayoutComponentHelper.create(myMotionLayoutNlComponent);

    // ok, so I found out why the live edit wasn't working -- actually everything was working, but...
    // live edit works by editing the layoutParams of the view. Which is ok as normally this is
    // indeed what drive the position of a widget.
    // Not so much for MotionLayout! in a transition the position of the widget will depend
    // on the Scene (and progress).
    // So everything was correctly working, updating the layoutparams of the concerned view,
    // but nothing was moving as what we will need to do here is to change the constraintset *live*
    // Additionally we need to correctly reset the state of the motionhelper if we recreate it.
    if (mLastSelection == MotionEditorSelector.Type.LAYOUT) {
      myMotionHelper.setState(null);
      mSelectedStartConstraintId = null;
    } else if (mLastSelection == MotionEditorSelector.Type.CONSTRAINT_SET) {
      myMotionHelper.setState(mSelectedStartConstraintId);
    } else if (mSelectedStartConstraintId != null && mSelectedEndConstraintId == null) {
      myMotionHelper.setState(mSelectedStartConstraintId);
    } else if (mSelectedStartConstraintId != null && mSelectedEndConstraintId != null) {
      myMotionHelper.setTransition(mSelectedStartConstraintId, mSelectedEndConstraintId);
      mMotionEditor.stopAnimation();
    } else {
      myMotionHelper.setState(null);
      mSelectedStartConstraintId = null;
    }

    // Ok, so to handle the "layout" mode, we need a few things.
    // 1. we need to capture in a constraintset the base layout, because we need to reapply it
    // 2. when "layout" is selected we can turn off the Scene (we do that already) but
    //    we also need to reapply the base layout constraints
    // 3. updating live via layoutparams do work right now, but applying the constraints doesn't
    //    -> I think because while we do correctly deactivate the Scene, at loading time we
    //       apply the start constraintset... so boom. If we captured the scene as a constraintset
    //       we should be able to make that work correctly.
    //    -> temporary solution : when selecting the base layout, write the applyMotionScene = false to the XML
    //       -> TEMP_HACK_FORCE_APPLY = true

    // ok so dragging an object doesn't work, but bias does. I think it's because it somehow use the base layout attributes
    // to decide what's possible.
  }

  @Override
  public void addListener(@NotNull AccessorySelectionListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull AccessorySelectionListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void requestSelection() {
    fireSelectionChanged(Collections.singletonList(mySelection));
  }

  private void fireSelectionChanged(@NotNull List<NlComponent> components) {
    boolean forLayout = mLastSelection == MotionEditorSelector.Type.LAYOUT || mLastSelection == MotionEditorSelector.Type.LAYOUT_VIEW;
    MotionEditorSelector.Type type = forLayout ? null : mLastSelection;
    MTag[] tags = forLayout ? null : myLastSelectedTags;
    List<NlComponent> selectedComponents = forLayout ? convertToLayoutSelection() : components;
    List<AccessorySelectionListener> copy = new ArrayList<>(myListeners);
    copy.forEach(listener -> listener.selectionChanged(this, type, tags, selectedComponents));
  }

  private List<NlComponent> convertToLayoutSelection() {
    List<NlComponent> views = new ArrayList<>();
    if (myLastSelectedTags != null) {
      for (MTag tag : myLastSelectedTags) {
        if (tag instanceof NlComponentTag) {
          NlComponent component = ((NlComponentTag)tag).getComponent();
          if (component != null) {
            views.add(component);
          }
        }
      }
    }
    if (views.isEmpty()) {
      if (myMotionLayoutNlComponent != null) {
        views.add(myMotionLayoutNlComponent);
      }
    }
    return views;
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MotionLayoutInterface
  ////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean showPopupMenuActions() {
    return false;
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MotionDesignSurfaceEdits
  ////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean handlesWriteForComponent(String id) {
    boolean handlesWrite = getSelectedConstraintSet() != null;
    return handlesWrite;
    //SmartPsiElementPointer<XmlTag> constraint = getSelectedConstraint();
    //if (constraint != null) {
    //  String constraintId = constraint.getElement().getAttribute("android:id").getValue();
    //  return id.equals(stripID(constraintId));
    //}
    //return false;
  }

  @Override
  public String getSelectedConstraintSet() {
    return mSelectedStartConstraintId;
  }

  // TODO: merge with the above parse function
  @Override
  @Nullable
  public XmlFile getTransitionFile(@NotNull NlComponent component) {
    // get the parent if need be
    if (!NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.MOTION_LAYOUT)) {
      component = component.getParent();
      if (component == null || !NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.MOTION_LAYOUT)) {
        return null;
      }
    }
    String file = component.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
    if (file == null) {
      return null;
    }
    int index = file.lastIndexOf("@xml/");
    String fileName = file.substring(index + 5);
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }
    AndroidFacet facet = component.getModel().getFacet();
    List<VirtualFile> resourcesXML = IdeResourcesUtil.getResourceSubdirs(ResourceFolderType.XML, StudioResourceRepositoryManager
      .getModuleResources(facet).getResourceDirs());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    return (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, virtualFile);
  }

  @Override
  @Nullable
  public XmlTag getConstraintSet(XmlFile file, String constraintSetId) {
    XmlTag[] children = file.getRootTag().findSubTags("ConstraintSet");
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        String childId = Utils.stripID(attribute.getValue());
        if (childId.equalsIgnoreCase(constraintSetId)) {
          return children[i];
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public XmlTag getConstrainView(XmlTag constraintSet, String id) {
    XmlTag[] children = constraintSet.getSubTags();
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        String value = attribute.getValue();
        int index = value.lastIndexOf("id/");
        value = value.substring(index + 3);
        if (value != null && value.equalsIgnoreCase(id)) {
          return children[i];
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public List<XmlTag> getKeyframes(XmlFile file, String componentId) {
    XmlTag[] children = file.getRootTag().findSubTags("KeyFrames");
    List<XmlTag> found = new ArrayList();
    for (int i = 0; i < children.length; i++) {
      XmlTag[] keyframes = children[i].getSubTags();
      for (int j = 0; j < keyframes.length; j++) {
        XmlTag keyframe = keyframes[j];
        XmlAttribute attribute = keyframe.getAttribute("motion:target");
        if (attribute != null) {
          String keyframeTarget = attribute.getValue();
          int index = keyframeTarget.indexOf('/');
          if (index != -1) {
            keyframeTarget = keyframeTarget.substring(index + 1);
          }
          if (componentId.equalsIgnoreCase(keyframeTarget)) {
            found.add(keyframe);
          }
        }
      }
    }
    return found;
  }

  public MotionSceneTag getMotionScene() {
    return myMotionScene;
  }

  /**
   * start rotating the surface when the tool attributes are set properly
   */
  private void startScreenRotating() {
    myStartDegree = Float.NaN;
    myEndDegree = Float.NaN;

    if (mLastSelection != MotionEditorSelector.Type.TRANSITION) {
      applyRotation();
      return;
    }

    MTag startConstraintSet = null;
    MTag endConstraintSet = null;
    if (myLastSelectedTags != null && myLastSelectedTags.length > 0) {
      startConstraintSet = mMotionEditor.getMeModel().findStartConstraintSet(myLastSelectedTags[0]);
      endConstraintSet = mMotionEditor.getMeModel().findEndConstraintSet(myLastSelectedTags[0]);
    }

    // fetch the starting and ending degrees from startConstraintSet and endConstraintSet, respectively.
    if (startConstraintSet != null && endConstraintSet != null) {
        String startScreenRotation = startConstraintSet.getAttributeValue(MotionSceneAttrs.ATTR_TOOLS_SCREEN_ROTATION);
        String endScreenRotation = endConstraintSet.getAttributeValue(MotionSceneAttrs.ATTR_TOOLS_SCREEN_ROTATION);

        if (startScreenRotation != null && NumberUtils.isParsable(startScreenRotation)) {
          myStartDegree = Float.parseFloat(startScreenRotation);
        }
        if (endScreenRotation != null && NumberUtils.isParsable(endScreenRotation)) {
          myEndDegree = Float.parseFloat(endScreenRotation);
        }
    }
    applyRotation();
  }

  /**
   * actual function that apply screen (surface) rotation
   */
  private void applyRotation() {
    /*
     * if myStartDegree is NaN or myEndDegree is NaN, rotationDegree would also become NaN
     * Otherwise, mLastProgress will be mapped to value in the range between myStartDegree and myEndDegree
     */
    float rotationDegree = myStartDegree + mLastProgress * (myEndDegree - myStartDegree);
    myDesignSurface.setRotateSufaceDegree(rotationDegree);
  }

  /**
   * stop rotating the surface, and resume the surface to the original orientation
   */
  private void stopScreenRotating() {
    myStartDegree = Float.NaN;
    myEndDegree = Float.NaN;
    myDesignSurface.setRotateSufaceDegree(Float.NaN);
  }
}
