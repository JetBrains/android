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
package com.android.tools.idea.common.model;

import static com.android.tools.idea.common.model.NlComponentUtil.isDescendant;
import static com.google.common.base.Verify.verifyNotNull;
import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.common.type.DesignerEditorFileTypeKt;
import com.android.tools.idea.common.util.XmlTagUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ResourceResolverCache;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Model for an XML file
 */
public class NlModel implements ModificationTracker, DataContextHolder {

  /**
   * Responsible for updating {@link NlModel} once results from LayoutLibSceneManager is available as {@link TagSnapshotTreeNode}.
   */
  public interface NlModelUpdaterInterface {

    void update(@NotNull NlModel model, @Nullable XmlTag newRoot, @NotNull List<NlModel.TagSnapshotTreeNode> roots);
  }

  public static final int DELAY_AFTER_TYPING_MS = 250;

  static final boolean CHECK_MODEL_INTEGRITY = false;
  private final Set<String> myPendingIds = Sets.newHashSet();

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final VirtualFile myFile;

  @NotNull private final Configuration myConfiguration;
  private final ListenerCollection<ModelListener> myListeners = ListenerCollection.createWithDirectExecutor();
  /** Model name. This can be used when multiple models are displayed at the same time */
  @Nullable private String myModelDisplayName;
  /** Text to display when displaying a tooltip related to this model */
  @Nullable private String myModelTooltip;
  @Nullable private NlComponent myRootComponent;
  private LintAnnotationsModel myLintAnnotationsModel;
  private final long myId;
  private final Set<Object> myActivations = Collections.newSetFromMap(new WeakHashMap<>());
  private final ModelVersion myModelVersion = new ModelVersion();
  private final DesignerEditorFileType myType;
  private long myConfigurationModificationCount;
  private final MergingUpdateQueue myUpdateQueue;

  // Variable to track what triggered the latest render (if known)
  private ChangeType myModificationTrigger;
  /** Executor used for asynchronous updates. */
  private final @NotNull ExecutorService myUpdateExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("NlModel", 1);
  private final @NotNull AtomicReference<Disposable> myThemeUpdateComputation = new AtomicReference<>();
  private boolean myDisposed;

  /**
   * {@link LayoutlibSceneManager} requires the file from model to be an {@link XmlFile} to be able to render it. This is true in case of
   * layout file and some others as well. However, we want to use model to render other file types (e.g. Java and Kotlin source files that
   * contain custom Android {@link View}s)that do not have explicit conversion to {@link XmlFile} (but might have implicit). This provider should
   * provide us with {@link XmlFile} representation of the VirtualFile fed to the model.
   */
  private final BiFunction<Project, VirtualFile, XmlFile> myXmlFileProvider;

  /**
   * Returns the responsible for registering an {@link NlComponent} to enhance it with layout-specific properties and methods.
   */
  @NotNull private final Consumer<NlComponent> myComponentRegistrar;

  @NotNull private final NlModelUpdaterInterface myModelUpdater;

  @NotNull private DataContext myDataContext;

  /**
   * Indicate which group this NlModel belongs. This can be used to categorize the NlModel when rendering or layouting.
   */
  @Nullable
  private String myGroupId = null;

  @NotNull
  public static NlModelBuilder builder(@NotNull AndroidFacet facet, @NotNull VirtualFile file, @NotNull Configuration configuration) {
    return new NlModelBuilder(facet, file, configuration);
  }

  /**
   * Method called by the NlModelBuilder to instantiate a new NlModel
   */
  @Slow
  @NotNull
  static NlModel create(@Nullable Disposable parent,
                        @Nullable String modelDisplayName,
                        @Nullable String modelTooltip,
                        @NotNull AndroidFacet facet,
                        @NotNull VirtualFile file,
                        @NotNull Configuration configuration,
                        @NotNull Consumer<NlComponent> componentRegistrar,
                        @NotNull BiFunction<Project, VirtualFile, XmlFile> xmlFileProvider,
                        @Nullable NlModelUpdaterInterface modelUpdater,
                        @NotNull DataContext dataContext) {
    return new NlModel(parent, modelDisplayName, modelTooltip, facet, file, configuration, componentRegistrar, xmlFileProvider, modelUpdater, dataContext);
  }

  protected NlModel(@Nullable Disposable parent,
                    @Nullable String modelDisplayName,
                    @Nullable String modelTooltip,
                    @NotNull AndroidFacet facet,
                    @NotNull VirtualFile file,
                    @NotNull Configuration configuration,
                    @NotNull Consumer<NlComponent> componentRegistrar,
                    @NotNull DataContext dataContext) {
    this(parent, modelDisplayName, modelTooltip, facet, file, configuration, componentRegistrar, NlModel::getDefaultXmlFile, null, dataContext);
  }

  @VisibleForTesting
  protected NlModel(@Nullable Disposable parent,
                    @Nullable String modelDisplayName,
                    @Nullable String modelTooltip,
                    @NotNull AndroidFacet facet,
                    @NotNull VirtualFile file,
                    @NotNull Configuration configuration,
                    @NotNull Consumer<NlComponent> componentRegistrar,
                    @NotNull BiFunction<Project, VirtualFile, XmlFile> xmlFileProvider,
                    @Nullable NlModelUpdaterInterface modelUpdater,
                    @NotNull DataContext dataContext) {
    myFacet = facet;
    myXmlFileProvider = xmlFileProvider;
    myModelDisplayName = modelDisplayName;
    myModelTooltip = modelTooltip;
    myFile = file;
    myConfiguration = configuration;
    myComponentRegistrar = componentRegistrar;
    myConfigurationModificationCount = myConfiguration.getModificationCount();
    myId = System.nanoTime() ^ file.getName().hashCode();
    if (parent != null) {
      Disposer.register(parent, this);
    }
    myType = DesignerEditorFileTypeKt.typeOf(getFile());
    myUpdateQueue = new MergingUpdateQueue("android.layout.preview.edit", DELAY_AFTER_TYPING_MS,
                                           true, null, this, null, SWING_THREAD);
    myUpdateQueue.setRestartTimerOnAdd(true);
    if (modelUpdater == null) {
      myModelUpdater = new DefaultModelUpdater();
    } else {
      myModelUpdater = modelUpdater;
    }
    myDataContext = dataContext;
  }

  @NotNull
  @VisibleForTesting
  public MergingUpdateQueue getUpdateQueue() {
    return myUpdateQueue;
  }

  /**
   * Notify model that it's active. A model is active by default.
   *
   * @param source caller used to keep track of the references to this model. See {@link #deactivate(Object)}
   * @return true if the model was not active before and was activated.
   */
  public boolean activate(@NotNull Object source) {
    if (getFacet().isDisposed()) {
      return false;
    }

    // TODO: Tracking the source is just a workaround for the model being shared so the activations and deactivations are
    // handled correctly. This should be solved by moving the removing this responsibility from the model. The model shouldn't
    // need to keep track of activations/deactivation and they should be handled by the caller.
    boolean wasActive;
    synchronized (myActivations) {
      wasActive = !myActivations.isEmpty();
      myActivations.add(source);
    }
    if (!wasActive) {
      // This was the first activation so enable listeners

      // If the resources have changed or the configuration has been modified, request a model update
      if (myConfiguration.getModificationCount() != myConfigurationModificationCount) {
        updateTheme();
      }
      myListeners.forEach(listener -> listener.modelActivated(this));
      return true;
    }
    else {
      return false;
    }
  }

  public void updateTheme() {
    ResourceUrl themeUrl = ResourceUrl.parse(myConfiguration.getTheme());
    if (themeUrl != null && themeUrl.type == ResourceType.STYLE) {
      Disposable computationToken = Disposer.newDisposable();
      Disposer.register(this, computationToken);
      Disposable oldComputation = myThemeUpdateComputation.getAndSet(computationToken);
      if (oldComputation != null) {
        Disposer.dispose(oldComputation);
      }
      ReadAction.nonBlocking(() -> updateTheme(themeUrl, computationToken)).expireWith(computationToken).submit(myUpdateExecutor);
    }
  }

  @Slow
  private void updateTheme(@NotNull ResourceUrl themeUrl, @NotNull Disposable computationToken) {
    if (myThemeUpdateComputation.get() != computationToken) {
      return; // A new update has already been scheduled.
    }
    try {
      ResourceResolver resolver = myConfiguration.getResourceResolver();
      if (resolver.getTheme(themeUrl.name, themeUrl.isFramework()) == null) {
        String theme = myConfiguration.getConfigurationManager().computePreferredTheme(myConfiguration);
        if (myThemeUpdateComputation.get() != computationToken) {
          return; // A new update has already been scheduled.
        }
        ApplicationManager.getApplication().invokeLater(() -> myConfiguration.setTheme(theme), a -> myDisposed);
      }
    }
    finally {
      if (myThemeUpdateComputation.compareAndSet(computationToken, null)) {
        Disposer.dispose(computationToken);
      }
    }
  }

  private void deactivate() {
    myConfigurationModificationCount = myConfiguration.getModificationCount();
  }

  /**
   * Notify model that it's not active. This means it can stop watching for events etc. It may be activated again in the future.
   *
   * @param source the source is used to keep track of the references that are using this model. Only when all the sources have called
   *               deactivate(Object), the model will be really deactivated.
   * @return true if the model was active before and was deactivated.
   */
  public boolean deactivate(@NotNull Object source) {
    boolean shouldDeactivate;
    synchronized (myActivations) {
      boolean removed = myActivations.remove(source);
      // If there are no more activations, call the private #deactivate()
      shouldDeactivate = removed && myActivations.isEmpty();
    }
    if (shouldDeactivate) {
      deactivate();
      return true;
    }
    else {
      return false;
    }
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  /**
   * Returns the {@code XmlFile} PSI representation of {@code virtualFile} in {@code project}.
   * @deprecated Use {@link NlModelBuilder} and do not use this method.
   */
  @NotNull
  private static XmlFile getDefaultXmlFile(Project project, VirtualFile virtualFile) {
    XmlFile file = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
    return verifyNotNull(file);
  }

  @NotNull
  public XmlFile getFile() {
    return myXmlFileProvider.apply(getProject(), myFile);
  }

  @NotNull
  public DesignerEditorFileType getType() {
    return myType;
  }

  @Nullable
  public LintAnnotationsModel getLintAnnotationsModel() {
    return myLintAnnotationsModel;
  }

  public void setLintAnnotationsModel(@Nullable LintAnnotationsModel model) {
    myLintAnnotationsModel = model;
    // Deliberately not rev'ing the model version and firing changes here;
    // we know only the warnings layer cares about this change and can be
    // updated by a single repaint
  }

  @NotNull
  public Set<String> getPendingIds() {
    return myPendingIds;
  }

  public void syncWithPsi(@NotNull XmlTag newRoot, @NotNull List<TagSnapshotTreeNode> roots) {
    myModelUpdater.update(this, newRoot, roots);
  }

  protected void setRootComponent(NlComponent root) {
    myRootComponent = root;
  }

  public void checkStructure() {
    if (CHECK_MODEL_INTEGRITY) {
      ApplicationManager.getApplication().runReadAction(() -> {
        Set<NlComponent> unique = Sets.newIdentityHashSet();
        Set<XmlTag> uniqueTags = Sets.newIdentityHashSet();
        checkUnique(getFile().getRootTag(), uniqueTags);
        uniqueTags.clear();
        if (myRootComponent != null) {
          checkUnique(myRootComponent.getTagDeprecated(), uniqueTags);
          checkUnique(myRootComponent, unique);
          checkStructure(myRootComponent);
        }
      });
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkUnique(NlComponent component, Set<NlComponent> unique) {
    if (CHECK_MODEL_INTEGRITY) {
      assert !unique.contains(component);
      unique.add(component);

      for (NlComponent child : component.getChildren()) {
        checkUnique(child, unique);
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkUnique(XmlTag tag, Set<XmlTag> unique) {
    if (CHECK_MODEL_INTEGRITY) {
      assert !unique.contains(tag);
      unique.add(tag);
      for (XmlTag subTag : tag.getSubTags()) {
        checkUnique(subTag, unique);
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkStructure(NlComponent component) {
    if (CHECK_MODEL_INTEGRITY) {
      // This is written like this instead of just "assert component.w != -1" to ease
      // setting breakpoint to debug problems
      if (NlComponentHelperKt.getHasNlComponentInfo(component)) {
        int w = NlComponentHelperKt.getW(component);
        if (w == -1) {
          assert false : w;
        }
      }
      if (component.getSnapshot() == null) {
        assert false;
      }
      if (component.getTagDeprecated() == null) {
        assert false;
      }
      if (!component.getTagName().equals(component.getTagDeprecated().getName())) {
        assert false;
      }

      if (!component.getTagDeprecated().isValid()) {
        assert false;
      }

      // Look for parent chain cycle
      NlComponent p = component.getParent();
      while (p != null) {
        if (p == component) {
          assert false;
        }
        p = p.getParent();
      }

      for (NlComponent child : component.getChildren()) {
        if (child == component) {
          assert false;
        }
        if (child.getParent() == null) {
          assert false;
        }
        if (child.getParent() != component) {
          assert false;
        }
        if (child.getTagDeprecated().getParent() != component.getTagDeprecated()) {
          assert false;
        }

        // Check recursively
        checkStructure(child);
      }
    }
  }

  /**
   * Adds a new {@link ModelListener}. If the listener already exists, this method will make sure that the listener is only
   * added once.
   */
  public void addListener(@NotNull ModelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull ModelListener listener) {
    myListeners.remove(listener);
  }

  /**
   * Calls all the listeners {@link ModelListener#modelDerivedDataChanged(NlModel)} method.
   *
   * // TODO: move this mechanism to LayoutlibSceneManager, or, ideally, remove the need for it entirely by
   * // moving all the derived data into the Scene.
   */
  public void notifyListenersModelDerivedDataChanged() {
    myListeners.forEach(listener -> listener.modelDerivedDataChanged(this));
  }

  /**
   * Calls all the listeners {@link ModelListener#modelChangedOnLayout(NlModel, boolean)} method.
   *
   * TODO: move these listeners out of NlModel, since the model shouldn't care about being laid out.
   *
   * @param animate if true, warns the listeners to animate the layout update
   */
  public void notifyListenersModelChangedOnLayout(boolean animate) {
    myListeners.forEach(listener -> listener.modelChangedOnLayout(this, animate));
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public Module getModule() {
    return myFacet.getModule();
  }

  @NotNull
  public Project getProject() {
    return getModule().getProject();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public ImmutableList<NlComponent> getComponents() {
    return myRootComponent != null ? ImmutableList.of(myRootComponent) : ImmutableList.of();
  }

  @NotNull
  public Stream<NlComponent> flattenComponents() {
    return myRootComponent != null ? Stream.of(myRootComponent).flatMap(NlComponent::flatten) : Stream.empty();
  }

  /**
   * This will warn model listeners that the model has been changed "live", without
   * the attributes of components being actually committed. Listeners such as Scene Managers will
   * likely want for example to schedule a layout pass in reaction to that callback.
   *
   * @param animate should the changes be animated or not.
   */
  public void notifyLiveUpdate(boolean animate) {
    myListeners.forEach(listener -> listener.modelLiveUpdate(this, animate));
  }

  /**
   * A node in a tree structure where each node provides a {@link TagSnapshot}.
   */
  public interface TagSnapshotTreeNode {
    @Nullable
    TagSnapshot getTagSnapshot();

    @NotNull
    List<TagSnapshotTreeNode> getChildren();
  }

  @NotNull
  public ImmutableList<NlComponent> findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(getFile(), offset, XmlTag.class, false);
    return (tag != null) ? findViewsByTag(tag) : ImmutableList.of();
  }

  @Nullable
  public NlComponent findViewByTag(@NotNull XmlTag tag) {
    return myRootComponent != null ? myRootComponent.findViewByTag(tag) : null;
  }

  @Nullable
  public NlComponent find(@NotNull String id) {
    return flattenComponents().filter(c -> id.equals(c.getId())).findFirst().orElse(null);
  }

  @Nullable
  public NlComponent find(@NotNull Predicate<NlComponent> condition) {
    return flattenComponents().filter(condition).findFirst().orElse(null);
  }

  @NotNull
  private ImmutableList<NlComponent> findViewsByTag(@NotNull XmlTag tag) {
    if (myRootComponent == null) {
      return ImmutableList.of();
    }

    return myRootComponent.findViewsByTag(tag);
  }

  @Nullable
  public NlComponent findViewByPsi(@Nullable PsiElement element) {
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    while (element != null) {
      if (element instanceof XmlTag) {
        return findViewByTag((XmlTag)element);
      }
      // noinspection AssignmentToMethodParameter
      element = element.getParent();
    }

    return null;
  }

  @Nullable
  public ResourceReference findAttributeByPsi(@NotNull PsiElement element) {
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    while (element != null) {
      if (element instanceof XmlAttribute) {
        XmlAttribute attribute = (XmlAttribute)element;
        ResourceNamespace namespace = IdeResourcesUtil.resolveResourceNamespace(attribute, attribute.getNamespacePrefix());
        if (namespace == null) {
          return null;
        }
        return ResourceReference.attr(namespace, attribute.getLocalName());
      }
      element = element.getParent();
    }
    return null;
  }

  public void delete(final Collection<NlComponent> components) {
    // Group by parent and ask each one to participate
    WriteCommandAction.runWriteCommandAction(getProject(), "Delete Component", null, () -> handleDeletion(components), getFile());
    notifyModified(ChangeType.DELETE);
  }

  private static void handleDeletion(@NotNull Collection<NlComponent> components) {
    // Segment the deleted components into lists of siblings
    Multimap<NlComponent, NlComponent> siblingLists = NlComponentUtil.groupSiblings(components);

    // Notify parent components about children getting deleted
    for (NlComponent parent : siblingLists.keySet()) {
      if (parent == null) {
        continue;
      }

      Collection<NlComponent> children = siblingLists.get(parent);

      if (!parent.getMixin().maybeHandleDeletion(children)) {
        for (NlComponent component : children) {
          NlComponent p = component.getParent();
          if (p != null) {
            p.removeChild(component);
          }

          XmlTag tag = component.getTagDeprecated();
          if (tag.isValid()) {
            PsiElement parentTag = tag.getParent();
            tag.delete();
            if (parentTag instanceof XmlTag) {
              ((XmlTag)parentTag).collapseIfEmpty();
            }
          }
        }
      }
    }
  }

  /**
   * Creates a new component of the given type. It will optionally insert it as a child of the given parent (and optionally
   * right before the given sibling or null to append at the end.)
   * <p/>
   * Note: This operation can only be called when the caller is already holding a write lock. This will be the
   * case from {@link ViewHandler} callbacks such as {@link ViewHandler#onCreate} and {@link DragHandler#commit}.
   * <p/>
   * Note: The caller is responsible for calling {@link #notifyModified(ChangeType)} if the creation completes successfully.
   *
   * @param tag        The XmlTag for the component.
   * @param parent     The parent to add this component to.
   * @param before     The sibling to insert immediately before, or null to append
   * @param insertType The reason for this creation.
   */
  @Nullable
  public NlComponent createComponent(@NotNull final XmlTag tag,
                                     @Nullable NlComponent parent,
                                     @Nullable NlComponent before,
                                     @NotNull InsertType insertType) {
    XmlTag addedTag = tag;
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      XmlTag parentTag = parent.getTagDeprecated();
      addedTag = WriteAction.compute(() -> {
        if (before != null) {
          return (XmlTag)parentTag.addBefore(tag, before.getTagDeprecated());
        }
        return parentTag.addSubTag(tag, false);
      });
    }

    NlComponent child = createComponent(addedTag);

    if (parent != null) {
      parent.addChild(child, before);
    }
    if (child.postCreate(insertType)) {
      return child;
    }
    return null;
  }

  /**
   * Simply create a component. In most cases you probably want
   * {@link #createComponent(XmlTag, NlComponent, NlComponent, InsertType)}.
   */
  @NotNull
  public NlComponent createComponent(@NotNull XmlTag tag) {
    NlComponent component = new NlComponent(this, tag);
    myComponentRegistrar.accept(component);
    return component;
  }

  @NotNull
  public List<NlComponent> createComponents(@NotNull DnDTransferItem item, @NotNull InsertType insertType) {
    List<NlComponent> components = new ArrayList<>(item.getComponents().size());
    for (DnDTransferComponent dndComponent : item.getComponents()) {
      XmlTag tag = XmlTagUtil.createTag(getProject(), dndComponent.getRepresentation());
      NlComponent component = createComponent(tag, null, null, insertType);
      if (component == null) {
        // User may have cancelled
        return Collections.emptyList();
      }
      component.postCreateFromTransferrable(dndComponent);
      components.add(component);
    }
    return components;
  }


  /**
   * Returns true if the specified components can be added to the specified receiver.
   */
  public boolean canAddComponents(@NotNull List<NlComponent> toAdd,
                                  @NotNull NlComponent receiver,
                                  @Nullable NlComponent before) {
    return canAddComponents(toAdd, receiver, before, false);
  }

  public boolean canAddComponents(@NotNull List<NlComponent> toAdd,
                                  @NotNull NlComponent receiver,
                                  @Nullable NlComponent before,
                                  boolean ignoreMissingDependencies) {
    if (before != null && before.getParent() != receiver) {
      return false;
    }
    if (toAdd.isEmpty()) {
      return false;
    }
    if (toAdd.stream().anyMatch(c -> !c.canAddTo(receiver))) {
      return false;
    }

    // If the receiver is a (possibly indirect) child of any of the dragged components, then reject the operation
    if (isDescendant(receiver, toAdd)) {
      return false;
    }

    return ignoreMissingDependencies || checkIfUserWantsToAddDependencies(toAdd);
  }

  private boolean checkIfUserWantsToAddDependencies(List<NlComponent> toAdd) {
    // May bring up a dialog such that the user can confirm the addition of the new dependencies:
    return NlDependencyManager.getInstance().checkIfUserWantsToAddDependencies(toAdd, getFacet());
  }

  /**
   * Adds components to the specified receiver before the given sibling.
   * If insertType is a move the components specified should be components from this model.
   * The callback function {@param #onComponentAdded} gives a chance to do additional task when components are added.
   */
  public void addComponents(@NotNull List<NlComponent> toAdd,
                            @NotNull NlComponent receiver,
                            @Nullable NlComponent before,
                            @NotNull InsertType insertType,
                            @Nullable Runnable onComponentAdded) {
    addComponents(toAdd, receiver, before, insertType, onComponentAdded, null);
  }

  public void addComponents(@NotNull List<NlComponent> componentToAdd,
                            @NotNull NlComponent receiver,
                            @Nullable NlComponent before,
                            @NotNull InsertType insertType,
                            @Nullable Runnable onComponentAdded,
                            @Nullable Runnable attributeUpdatingTask) {
    addComponents(componentToAdd, receiver, before, insertType, onComponentAdded, attributeUpdatingTask, null);
  }

  /**
   * Adds components to the specified receiver before the given sibling.
   * If insertType is a move the components specified should be components from this model.
   * The callback function {@param #onComponentAdded} gives a chance to do additional task when components are added.
   */
  public void addComponents(@NotNull List<NlComponent> componentToAdd,
                            @NotNull NlComponent receiver,
                            @Nullable NlComponent before,
                            @NotNull InsertType insertType,
                            @Nullable Runnable onComponentAdded,
                            @Nullable Runnable attributeUpdatingTask,
                            @Nullable String groupId) {
    // Fix for b/124381110
    // The components may be added by addComponentInWriteCommand after this method returns.
    // Make a copy of the components such that the caller can change the list without causing problems.
    ImmutableList<NlComponent> toAdd = ImmutableList.copyOf(componentToAdd);

    // Note: we don't really need to check for dependencies if all we do is moving existing components.
    if (!canAddComponents(toAdd, receiver, before, insertType == InsertType.MOVE)) {
      return;
    }

    final Runnable callback =
      () -> addComponentInWriteCommand(toAdd, receiver, before, insertType, onComponentAdded, attributeUpdatingTask, groupId);
    if (insertType == InsertType.MOVE) {
      // The components are just moved, so there are no new dependencies.
      callback.run();
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      NlDependencyManager.getInstance().addDependencies(toAdd, getFacet(), false, callback);
    });
  }

  private void addComponentInWriteCommand(@NotNull List<NlComponent> toAdd,
                                          @NotNull NlComponent receiver,
                                          @Nullable NlComponent before,
                                          @NotNull InsertType insertType,
                                          @Nullable Runnable onComponentAdded,
                                          @Nullable Runnable attributeUpdatingTask,
                                          @Nullable String groupId) {
    DumbService.getInstance(getProject()).runWhenSmart(() -> {
      NlWriteCommandActionUtil.run(toAdd, generateAddComponentsDescription(toAdd, insertType), groupId, () -> {
        if (attributeUpdatingTask != null) {
          // Update the attribute before adding components, if need.
          attributeUpdatingTask.run();
        }
        handleAddition(toAdd, receiver, before, insertType);
      });

      notifyModified(ChangeType.ADD_COMPONENTS);
      if (onComponentAdded != null) {
        onComponentAdded.run();
      }
    });
  }

  @NotNull
  private static String generateAddComponentsDescription(@NotNull List<NlComponent> toAdd, @NotNull InsertType insertType) {
    DragType dragType = insertType.getDragType();
    String componentType = "";
    if (toAdd.size() == 1) {
      String tagName = toAdd.get(0).getTagName();
      componentType = tagName.substring(tagName.lastIndexOf('.') + 1);
    }
    return dragType.getDescription(componentType);
  }

  /**
   * Add tags component to the specified receiver before the given sibling.
   */
  public void addTags(@NotNull List<NlComponent> added,
                      @NotNull NlComponent receiver,
                      @Nullable NlComponent before,
                      final @NotNull InsertType insertType) {
    NlWriteCommandActionUtil.run(added, generateAddComponentsDescription(added, insertType), () -> {
      for (NlComponent component : added) {
        component.addTags(receiver, before, insertType);
      }
    });

    notifyModified(ChangeType.ADD_COMPONENTS);
  }

  /**
   * Looks up the existing set of id's reachable from this model
   */
  @NotNull
  public Set<String> getIds() {
    LocalResourceRepository resources = ResourceRepositoryManager.getAppResources(getFacet());
    Set<String> ids = new HashSet<>(resources.getResources(ResourceNamespace.TODO(), ResourceType.ID).keySet());
    Set<String> pendingIds = getPendingIds();
    if (!pendingIds.isEmpty()) {
      Set<String> all = new HashSet<>(pendingIds.size() + ids.size());
      all.addAll(ids);
      all.addAll(pendingIds);
      ids = all;
    }
    return ids;
  }

  private void handleAddition(@NotNull List<NlComponent> added,
                              @NotNull NlComponent receiver,
                              @Nullable NlComponent before,
                              @NotNull InsertType insertType) {
    Set<String> ids = getIds();

    for (NlComponent component : added) {
      component.moveTo(receiver, before, insertType, ids);
    }
  }

  @NotNull
  public InsertType determineInsertType(@NotNull DragType dragType, @Nullable DnDTransferItem item, boolean asPreview) {
    if (item != null && item.isFromPalette()) {
      return asPreview ? InsertType.CREATE_PREVIEW : InsertType.CREATE;
    }
    switch (dragType) {
      case CREATE:
        return asPreview ? InsertType.CREATE_PREVIEW : InsertType.CREATE;
      case MOVE:
        return item != null && myId != item.getModelId() ? InsertType.COPY : InsertType.MOVE;
      case COPY:
        return InsertType.COPY;
      case PASTE:
      default:
        return InsertType.PASTE;
    }
  }

  public long getId() {
    return myId;
  }

  public void setModelDisplayName(@Nullable String name) {
    myModelDisplayName = name;
  }

  @Nullable
  public String getModelDisplayName() {
    return myModelDisplayName;
  }

  @Nullable
  public String getModelTooltip() {
    return myModelTooltip;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    boolean shouldDeactivate;
    myLintAnnotationsModel = null;
    synchronized (myActivations) {
      // If there are no activations left, make sure we deactivate the model correctly
      shouldDeactivate = !myActivations.isEmpty();
      myActivations.clear();
    }
    if (shouldDeactivate) {
      deactivate(); // ensure listeners are unregistered if necessary
    }

    myListeners.clear();
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  @Override
  public String toString() {
    return NlModel.class.getSimpleName() + " for " + myFile;
  }

  // ---- Implements ModificationTracker ----

  public enum ChangeType {
    RESOURCE_EDIT,
    EDIT,
    RESOURCE_CHANGED,
    ADD_COMPONENTS,
    DELETE,
    DND_COMMIT,
    DND_END,
    DROP,
    RESIZE_END, RESIZE_COMMIT,
    UPDATE_HIERARCHY,
    BUILD,
    CONFIGURATION_CHANGE
  }

  /**
   * Maintains multiple counter depending on what did change in the model
   */
  static class ModelVersion {
    private final AtomicLong myVersion = new AtomicLong();
    @SuppressWarnings("unused") ChangeType mLastReason;

    public void increase(ChangeType reason) {
      myVersion.incrementAndGet();
      mLastReason = reason;
    }

    public long getVersion() {
      return myVersion.get();
    }
  }

  @Override
  public long getModificationCount() {
    return myModelVersion.getVersion();
  }

  public long getConfigurationModificationCount() {
    return myConfigurationModificationCount;
  }

  public void notifyModified(@NotNull ChangeType reason) {
    myModelVersion.increase(reason);
    updateTheme();
    myModificationTrigger = reason;
    myListeners.forEach(listener -> listener.modelChanged(this));
  }

  /**
   * Schedules {@link #notifyModified(ChangeType)} to be called via an {@link MergingUpdateQueue}, so once user activity (typing) has
   * stopped. {@link #notifyModified(ChangeType)} gets called on the EDT, just like the "original" callback from
   * {@link ResourceNotificationManager}.
   */
  public void notifyModifiedViaUpdateQueue(@NotNull ChangeType reason) {
    myUpdateQueue.queue(
      new Update("edit") {
        @Override
        public void run() {
          notifyModified(reason);
        }

        @Override
        public boolean canEat(Update update) {
          return true;
        }
      }
    );
  }

  @Nullable
  public ChangeType getLastChangeType() {
    return myModificationTrigger;
  }

  public void resetLastChange() {
    myModificationTrigger = null;
  }

  /**
   * Returns the {@link DataContext} associated to this model. The {@link DataContext} allows storing information that is specific to this
   * model but is not part of it. For example, context information about how the model should be represented in a specific surface.
   *
   * The {@link DataContext} might change at any point so make sure you always call this method to obtain the latest data.
   */
  @NotNull
  @Override
  public final DataContext getDataContext() {
    return myDataContext;
  }

  @Nullable
  public final String getGroupId() {
    return myGroupId;
  }

  public final void setGroupId(@Nullable String groupId) {
    myGroupId = groupId;
  }

  /**
   * Updates the NlModel data context with the given one.
   */
  @Override
  public final void setDataContext(@NotNull DataContext dataContext) {
    myDataContext = dataContext;
  }
}
