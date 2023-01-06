/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res;

import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;

import com.android.annotations.concurrency.GuardedBy;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.databinding.util.DataBindingUtil;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.android.utils.HashCodes;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.concurrency.SameThreadExecutor;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@linkplain ResourceNotificationManager} provides notifications to editors that
 * are displaying Android resources, and need to update themselves when some operation
 * in the IDE edits a resource.
 * <p/>
 * <b>NOTE</b>: Editors should <b>only</b> listen for resource notifications while they
 * are actively showing. They should <b>not</b> simply add a listener when created
 * and then continue to listen in the background. A core value of the IDE is to offer
 * a quick editing experience, and if all the potential editors start listening globally
 * for events, this will slow everything down to a crawl.
 * <p/>
 * Clients should start listening for resource events when the editor is made visible,
 * and stop listening when the editor is made invisible. Furthermore, when editors are
 * made visible a second time, they can consult a modification stamp to see whether
 * anything changed while they were in the background, and if so update themselves only
 * if that's the case.
 * <p/>
 * Also note that
 * <ul>
 * <li>Resource editing events are not delivered synchronously</li>
 * <li>No locks are held when the listener are notified</li>
 * <li>All events are delivered on the event dispatch (UI) thread</li>
 * <li>Add listener or remove listener can be done from any thread</li>
 * </ul>
 */
public class ResourceNotificationManager {
  private final @NotNull Project myProject;

  private final @NotNull Object myObserverLock = new Object();
  /**
   * Module observers: one per observed module in the project, with potentially multiple listeners.
   */
  @GuardedBy("myObserverLock")
  private final @NotNull Map<Module, ModuleEventObserver> myModuleToObserverMap = new HashMap<>();

  /**
   * File observers: one per observed file, with potentially multiple listeners.
   */
  @GuardedBy("myObserverLock")
  private final @NotNull Map<VirtualFile, FileEventObserver> myFileToObserverMap = new HashMap<>();

  /**
   * Configuration observers: one per observed configuration, with potentially multiple listeners.
   */
  @GuardedBy("myObserverLock")
  private final @NotNull Map<Configuration, ConfigurationEventObserver> myConfigurationToObserverMap = new HashMap<>();

  /**
   * Project wide observer: a single one is sufficient.
   */
  @GuardedBy("myObserverLock")
  private @Nullable ProjectPsiTreeObserver myProjectPsiTreeObserver;

  private final @NotNull ProjectBuildObserver myProjectBuildObserver = new ProjectBuildObserver();

  /**
   * Whether we've already been notified about a change and we'll be firing it shortly.
   */
  private final AtomicBoolean myPendingNotify = new AtomicBoolean();

  private boolean myIgnoreChildrenChanged;

  /**
   * Counter for events other than resource repository, configuration or file events. For example,
   * this counts project builds.
   */
  private long myModificationCount;

  /**
   * Set of events we've observed since the last notification.
   */
  private @NotNull EnumSet<Reason> myEvents = EnumSet.noneOf(Reason.class);

  /**
   * Do not instantiate directly; this is a Service and its lifecycle is managed by the IDE;
   * use {@link #getInstance(Project)} instead.
   */
  public ResourceNotificationManager(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Returns the {@linkplain ResourceNotificationManager} for the given project.
   *
   * @param project the project to return the notification manager for
   * @return a notification manager
   */
  public static @NotNull ResourceNotificationManager getInstance(@NotNull Project project) {
    return project.getService(ResourceNotificationManager.class);
  }

  public @NotNull ResourceVersion getCurrentVersion(@NotNull AndroidFacet facet, @Nullable PsiFile file,
                                                    @Nullable Configuration configuration) {
    LocalResourceRepository repository = ResourceRepositoryManager.getAppResources(facet);
    if (file != null) {
      long fileStamp = file.getModificationStamp();
      if (configuration != null) {
        return new ResourceVersion(repository.getModificationCount(), fileStamp, configuration.getModificationCount(),
                                   configuration.getConfigurationManager().getStateVersion(), myModificationCount);

      }
      else {
        return new ResourceVersion(repository.getModificationCount(), fileStamp, 0L, 0L, myModificationCount);
      }
    }
    else {
      return new ResourceVersion(repository.getModificationCount(), 0L, 0L, 0L, myModificationCount);
    }
  }

  /**
   * Registers an interest in resources accessible from the given module.
   *
   * @param listener      the listener to notify when there is a resource change
   * @param facet         the facet for the Android module whose resources the listener is interested in
   * @param file          an optional file to observe for any edits. Note that this is not currently
   *                      limited to this listener; all listeners in the module will be notified when
   *                      there is an edit of this file.
   * @param configuration if file is not null, this is an optional configuration
   *                      you can listen for changes in (must be a configuration corresponding to the file)
   * @return the current resource modification stamp of the given module
   */
  public @NotNull ResourceVersion addListener(@NotNull ResourceChangeListener listener,
                                              @NotNull AndroidFacet facet,
                                              @Nullable VirtualFile file,
                                              @Nullable Configuration configuration) {
    Module module = facet.getModule();
    synchronized (myObserverLock) {
      ModuleEventObserver moduleEventObserver = myModuleToObserverMap.get(module);
      if (moduleEventObserver == null) {
        if (myModuleToObserverMap.isEmpty()) {
          if (myProjectPsiTreeObserver == null) {
            myProjectPsiTreeObserver = new ProjectPsiTreeObserver();
          }
          myProjectBuildObserver.startListening();
        }
        moduleEventObserver = new ModuleEventObserver(facet);
        myModuleToObserverMap.put(module, moduleEventObserver);
        Disposer.register(facet, () -> facetDisposed(facet));
      }
      moduleEventObserver.addListener(listener);

      if (file != null) {
        FileEventObserver fileEventObserver = myFileToObserverMap.get(file);
        if (fileEventObserver == null) {
          fileEventObserver = new FileEventObserver(module);
          myFileToObserverMap.put(file, fileEventObserver);
        }
        fileEventObserver.addListener(listener);

        if (configuration != null) {
          ConfigurationEventObserver configurationEventObserver = myConfigurationToObserverMap.get(configuration);
          if (configurationEventObserver == null) {
            configurationEventObserver = new ConfigurationEventObserver(configuration);
            myConfigurationToObserverMap.put(configuration, configurationEventObserver);
          }
          configurationEventObserver.addListener(listener);
        }
      }
      else {
        assert configuration == null : configuration;
      }
    }

    return getCurrentVersion(facet, file != null ? AndroidPsiUtils.getPsiFileSafely(myProject, file) : null, configuration);
  }

  private void facetDisposed(@NotNull AndroidFacet facet) {
    synchronized (myObserverLock) {
      myModuleToObserverMap.remove(facet.getModule());
    }
  }

  /**
   * Registers an interest in resources accessible from the given module.
   *
   * @param listener      the listener to notify when there is a resource change
   * @param facet         the facet for the Android module whose resources the listener is interested in
   * @param file          the file passed in to the corresponding {@link #addListener} call
   * @param configuration the configuration passed in to the corresponding {@link #addListener} call
   */
  public void removeListener(@NotNull ResourceChangeListener listener,
                             @NotNull AndroidFacet facet,
                             @Nullable VirtualFile file,
                             @Nullable Configuration configuration) {
    synchronized (myObserverLock) {
      if (file != null) {
        if (configuration != null) {
          ConfigurationEventObserver configurationEventObserver = myConfigurationToObserverMap.get(configuration);
          if (configurationEventObserver != null) {
            configurationEventObserver.removeListener(listener);
            if (!configurationEventObserver.hasListeners()) {
              myConfigurationToObserverMap.remove(configuration);
            }
          }
        }
        FileEventObserver fileEventObserver = myFileToObserverMap.get(file);
        if (fileEventObserver != null) {
          fileEventObserver.removeListener(listener);
          if (!fileEventObserver.hasListeners()) {
            myFileToObserverMap.remove(file);
          }
        }
      }
      else {
        assert configuration == null : configuration;
      }

      Module module = facet.getModule();
      ModuleEventObserver moduleEventObserver = myModuleToObserverMap.get(module);
      if (moduleEventObserver != null) {
        moduleEventObserver.removeListener(listener);
        if (!moduleEventObserver.hasListeners()) {
          myModuleToObserverMap.remove(module);
          if (myModuleToObserverMap.isEmpty() && myProjectPsiTreeObserver != null) {
            myProjectBuildObserver.stopListening();
            myProjectPsiTreeObserver = null;
          }
        }
      }
    }
  }

  /**
   * Returns an implementation of {@link PsiTreeChangeListener} that is not registered and is used as
   * a delegate (e.g in {@link AndroidFileChangeListener}).
   * <p>
   * If no listener has been added to the {@link ResourceNotificationManager}, this method returns null.
   */
  public @Nullable PsiTreeChangeListener getPsiListener() {
    synchronized (myObserverLock) {
      return myProjectPsiTreeObserver;
    }
  }

  /**
   * Something happened. Either schedule a notification or if one is already pending, do nothing.
   */
  private void notice(@NotNull Reason reason, @Nullable VirtualFile source) {
    myEvents.add(reason);
    if (!myPendingNotify.compareAndSet(false, true)) {
      return;
    }

    Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> {
      if (!myPendingNotify.compareAndSet(true, false)) {
        return;
      }

      if (source == null) {
        // Ensure that the notification happens after all pending Swing Runnables
        // have been processed, including any created *after* the initial notice()
        // call which scheduled this runnable, since they could for example be
        // ResourceFolderRepository.rescan() Runnables, and we want those to finish
        // before the final notify. Notice how we clear the pending notify flag
        // above though, such that if another event appears between the first
        // invoke later and the second, it will schedule another complete notification
        // event.
        scheduleFinalNotification();
      }
      else {
        application.runWriteAction(() -> {
          scheduleFinalNotificationAfterRepositoriesHaveBeenUpdated(source);
        });
      }
    });
  }

  private void scheduleFinalNotificationAfterRepositoriesHaveBeenUpdated(@NotNull VirtualFile source) {
    // The following code calls scheduleFinalNotification exactly once after the dispatchToRepositories
    // call returns and all callbacks passed to runAfterPendingUpdatesFinish are called. To avoid
    // calling scheduleFinalNotification prematurely, the initial value of count is set to 1.
    // This guarantees that it stays positive until the dispatchToRepositories method returns.
    AtomicInteger count = new AtomicInteger(1);
    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(myProject);
    resourceFolderRegistry.dispatchToRepositories(source, (repository, file) -> {
      count.incrementAndGet();
      repository.invokeAfterPendingUpdatesFinish(SameThreadExecutor.INSTANCE, () -> {
        if (count.decrementAndGet() == 0) {
          scheduleFinalNotification();
        }
      });
    });
    if (count.decrementAndGet() == 0) {
      scheduleFinalNotification();
    }
  }

  private void scheduleFinalNotification() {
    ApplicationManager.getApplication().invokeLater(() -> {
      ImmutableSet<Reason> reason = ImmutableSet.copyOf(myEvents);
      myEvents = EnumSet.noneOf(Reason.class);
      notifyListeners(reason);
      myEvents.clear();
    });
  }

  private void notifyListeners(@NotNull ImmutableSet<Reason> reason) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    List<ModuleEventObserver> observers;
    synchronized (myObserverLock) {
      observers = new ArrayList<>(myModuleToObserverMap.values());
    }
    for (ModuleEventObserver moduleEventObserver : observers) {
      // Not every module may have pending changes; each one will check.
      moduleEventObserver.notifyListeners(reason);
    }
  }

  /**
   * A {@linkplain ModuleEventObserver} registers listeners for various module-specific events (such as
   * resource folder manager changes) and then notifies {@link #notice(Reason, VirtualFile)} when it sees an event.
   */
  private class ModuleEventObserver implements ModificationTracker, ResourceFolderManager.ResourceFolderListener {
    private final AndroidFacet myFacet;
    private long myGeneration;
    private final Object myListenersLock = new Object();
    private MessageBusConnection myConnection;

    @GuardedBy("myListenersLock")
    private final @NotNull List<ResourceChangeListener> myListeners = new ArrayList<>(4);

    private ModuleEventObserver(@NotNull AndroidFacet facet) {
      myFacet = facet;
      myGeneration = getAppResourcesModificationCount();
    }

    @Override
    public long getModificationCount() {
      return myGeneration;
    }

    private void addListener(@NotNull ResourceChangeListener listener) {
      synchronized (myListenersLock) {
        if (myListeners.isEmpty()) {
          registerListeners();
        }
        myListeners.add(listener);
      }
    }

    private void removeListener(@NotNull ResourceChangeListener listener) {
      synchronized (myListenersLock) {
        myListeners.remove(listener);

        if (myListeners.isEmpty()) {
          unregisterListeners();
        }
      }
    }

    private void registerListeners() {
      if (AndroidModel.isRequired(myFacet)) {
        // Ensure that project resources have been initialized first, since
        // we want all repos to add their own variant listeners before ours (such that
        // when the variant changes, the project resources get notified and updated
        // before our own update listener attempts to re-render).
        ResourceRepositoryManager.getProjectResources(myFacet);

        assert myConnection == null;
        myConnection = myFacet.getModule().getProject().getMessageBus().connect(myFacet);
        myConnection.subscribe(ResourceFolderManager.TOPIC, this);
        ResourceFolderManager.getInstance(myFacet); // Make sure ResourceFolderManager is initialized.
      }
    }

    private void unregisterListeners() {
      if (myConnection != null) {
        myConnection.disconnect();
      }
    }

    private void notifyListeners(@NotNull ImmutableSet<Reason> reason) {
      if (myFacet.isDisposed()) {
        return;
      }
      long generation = getAppResourcesModificationCount();
      if (reason.size() == 1 && reason.contains(Reason.RESOURCE_EDIT) && generation == myGeneration) {
        // Notified of an edit in some file that could potentially affect the resources, but
        // it didn't cause the modification stamp to increase: ignore. (If there are other reasons,
        // such as a variant change, then notify regardless.)
        return;
      }

      myGeneration = generation;
      ApplicationManager.getApplication().assertIsDispatchThread();
      List<ResourceChangeListener> listeners;
      synchronized (myListenersLock) {
        listeners = new ArrayList<>(myListeners);
      }
      for (ResourceChangeListener listener : listeners) {
        listener.resourcesChanged(reason);
      }
    }

    private long getAppResourcesModificationCount() {
      LocalResourceRepository appResources = ResourceRepositoryManager.getInstance(myFacet).getCachedAppResources();
      return appResources == null ? 0 : appResources.getModificationCount();
    }

    private boolean hasListeners() {
      synchronized (myListenersLock) {
        return !myListeners.isEmpty();
      }
    }

    // ---- Implements ResourceFolderManager.ResourceFolderListener ----

    @Override
    public void mainResourceFoldersChanged(@NotNull AndroidFacet facet, @NotNull List<? extends VirtualFile> folders) {
      if (facet.getModule() == myFacet.getModule()) {
        myModificationCount++;
        notice(Reason.GRADLE_SYNC, null);
      }
    }

    @Override
    public void testResourceFoldersChanged(@NotNull AndroidFacet facet, @NotNull List<? extends VirtualFile> folders) {
      if (facet.getModule() == myFacet.getModule()) {
        myModificationCount++;
        notice(Reason.GRADLE_SYNC, null);
      }
    }
  }

  private class ProjectBuildObserver implements ProjectSystemBuildManager.BuildListener {
    private boolean myAlreadyAddedBuildListener;
    private boolean myIgnoreBuildEvents;

    private void startListening() {
      if (!myAlreadyAddedBuildListener) { // See comment in stopListening.
        myAlreadyAddedBuildListener = true;
        getProjectSystem(myProject).getBuildManager().addBuildListener(myProject, this);
      }
      myIgnoreBuildEvents = false;
    }

    private void stopListening() {
      // Unfortunately, we can't remove build tasks once they've been added.
      //    https://youtrack.jetbrains.com/issue/IDEA-139893
      // Therefore, we leave the listeners around, and make sure we only add
      // them once -- and we also ignore any build events that appear when we're
      // not intending to be actively listening
      // ProjectBuilder.getInstance(myProject).removeAfterProjectBuildTask(this);
      myIgnoreBuildEvents = true;
    }

    @Override
    public void buildStarted(@NotNull ProjectSystemBuildManager.BuildMode mode) {
    }

    @Override
    public void beforeBuildCompleted(@NotNull ProjectSystemBuildManager.BuildResult result) {
    }

    @Override
    public void buildCompleted(@NotNull ProjectSystemBuildManager.BuildResult result) {
      if (!myIgnoreBuildEvents) {
        myModificationCount++;
        notice(Reason.PROJECT_BUILD, null);
      }
    }
  }

  private class ProjectPsiTreeObserver implements PsiTreeChangeListener {

    private ProjectPsiTreeObserver() {
    }

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = false;
    }

    @Override
    public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = true;

      if (isIgnorable(event)) {
        return;
      }

      VirtualFile file = getVirtualFile(event);
      if (file != null && isRelevantFile(file)) {
        PsiElement child = event.getChild();
        PsiElement parent = event.getParent();

        if (child instanceof XmlAttribute && parent instanceof XmlTag) {
          // Typing in a new attribute. Don't need to do any rendering until there is an actual value.
          if (((XmlAttribute)child).getValueElement() == null) {
            return;
          }
        }
        else if (parent instanceof XmlAttribute && child instanceof XmlAttributeValue) {
          XmlAttributeValue attributeValue = (XmlAttributeValue)child;
          if (attributeValue.getValue().isEmpty()) {
            // Just added a new blank attribute; nothing to render yet.
            return;
          }
        }
        else if (parent instanceof XmlAttributeValue && child instanceof XmlToken && event.getOldChild() == null) {
          // Just added attribute value
          String text = child.getText();
          // Check if this is an attribute that takes a resource.
          if (text.startsWith(PREFIX_RESOURCE_REF) && !DataBindingUtil.isBindingExpression(text)) {
            if (text.equals(PREFIX_RESOURCE_REF) || text.equals(ANDROID_PREFIX)) {
              // Using code completion to insert resource reference; not yet done.
              return;
            }
            ResourceUrl url = ResourceUrl.parse(text);
            if (url != null && url.name.isEmpty()) {
              // Using code completion to insert resource reference; not yet done.
              return;
            }
          }
        }
        notice(Reason.EDIT, file);
      }
      else {
        notice(Reason.RESOURCE_EDIT, file);
      }
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = true;

      if (isIgnorable(event)) {
        return;
      }

      VirtualFile file = getVirtualFile(event);
      if (file != null && isRelevantFile(file)) {
        PsiElement child = event.getChild();
        PsiElement parent = event.getParent();
        if (parent instanceof XmlAttribute && child instanceof XmlToken) {
          // Typing in attribute name. Don't need to do any rendering until there is an actual value.
          XmlAttributeValue valueElement = ((XmlAttribute)parent).getValueElement();
          if (valueElement == null || valueElement.getValue().isEmpty()) {
            return;
          }
        }

        notice(Reason.EDIT, file);
      } else {
        notice(Reason.RESOURCE_EDIT, file);
      }
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = true;

      if (isIgnorable(event)) {
        return;
      }

      VirtualFile file = getVirtualFile(event);
      if (file != null && isRelevantFile(file)) {
        PsiElement child = event.getChild();
        PsiElement parent = event.getParent();
        if (parent instanceof XmlAttribute && child instanceof XmlToken) {
          // Typing in attribute name. Don't need to do any rendering until there is an actual value.
          XmlAttributeValue valueElement = ((XmlAttribute)parent).getValueElement();
          if (valueElement == null || valueElement.getValue().isEmpty()) {
            return;
          }
        }
        else if (parent instanceof XmlAttributeValue && child instanceof XmlToken && event.getOldChild() != null) {
          String newText = child.getText();
          String prevText = event.getOldChild().getText();
          // See if user is working on an incomplete URL, and is still not complete, e.g. typing in @string/foo manually.
          if (newText.startsWith(PREFIX_RESOURCE_REF) && !DataBindingUtil.isBindingExpression(newText)) {
            ResourceUrl prevUrl = ResourceUrl.parse(prevText);
            ResourceUrl newUrl = ResourceUrl.parse(newText);
            if (prevUrl != null && prevUrl.name.isEmpty()) {
              prevUrl = null;
            }
            if (newUrl != null && newUrl.name.isEmpty()) {
              newUrl = null;
            }
            if (prevUrl == null && newUrl == null) {
              return;
            }
          }
        }
        notice(Reason.EDIT, file);
      }
      else {
        notice(Reason.RESOURCE_EDIT, file);
      }
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      check(event);
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      if (myIgnoreChildrenChanged) {
        return;
      }

      check(event);
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      // When renaming a PsiFile, if the file extension stays the same (i.e the file type
      // stays the same) the generated PsiTreeChangeEvent will trigger "propertyChanged()" (this method) with
      // the changed file set as the event's element.
      // On the other hand, if the file extension is changed, a new object is created and the event will
      // trigger "childReplaced()" instead, the parent being the file's directory and the renamed
      // file being the new child.
      if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(event.getPropertyName())) {
        PsiElement child = event.getElement();
        VirtualFile file = child instanceof PsiFile ? ((PsiFile)child).getVirtualFile() : null;
        notice(Reason.RESOURCE_EDIT, file);
      }
    }

    private @Nullable VirtualFile getVirtualFile(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      return psiFile == null ? null : psiFile.getVirtualFile();
    }

    /**
     * Checks if the file is present in {@link #myFileToObserverMap}.
     */
    private boolean isRelevantFile(@NotNull VirtualFile virtualFile) {
      synchronized (myObserverLock) {
        return myFileToObserverMap.containsKey(virtualFile);
      }
    }

    private boolean isIgnorable(@NotNull PsiTreeChangeEvent event) {
      // We can ignore edits in whitespace, XML error nodes, and modification in comments.
      // (Note that editing text in an attribute value, including whitespace characters,
      // is not a PsiWhiteSpace element; it's an XmlToken of token type XML_ATTRIBUTE_VALUE_TOKEN
      // Moreover, We only ignore the modification of commented texts (in such case the type of
      // parent is XmlComment), because the user may *mark* some components/attributes as comments
      // for debugging purpose. In that case the child is instance of XmlComment but parent isn't,
      // so we will NOT ignore the event.
      PsiElement child = event.getChild();
      PsiElement parent = event.getParent();
      if (child instanceof PsiErrorElement || parent instanceof XmlComment) {
        return true;
      }

      if ((child instanceof PsiWhiteSpace || child instanceof XmlText || parent instanceof XmlText)
          && IdeResourcesUtil.getFolderType(event.getFile()) != ResourceFolderType.VALUES) {
        // Editing text or whitespace has no effect outside of values files.
        return true;
      }

      PsiFile file = event.getFile();
      // Spurious events from the IDE doing internal things, such as the formatter using a light virtual
      // filesystem to process text formatting chunks etc.
      return file != null && (file.getParent() == null || !file.getViewProvider().isPhysical());
    }

    private void check(@NotNull PsiTreeChangeEvent event) {
      if (isIgnorable(event)) {
        return;
      }

      VirtualFile file = getVirtualFile(event);
      if (file != null && isRelevantFile(file)) {
        notice(Reason.EDIT, file);
      }
      else {
        notice(Reason.RESOURCE_EDIT, file);
      }
    }
  }

  private class FileEventObserver implements BulkFileListener {
    private final List<ResourceChangeListener> myListeners = new ArrayList<>(2);
    private final Module myModule;
    private MessageBusConnection myMessageBusConnection;

    private FileEventObserver(Module module) {
      myModule = module;
    }

    private void addListener(@NotNull ResourceChangeListener listener) {
      if (myListeners.isEmpty()) {
        registerListeners();
      }
      myListeners.add(listener);
    }

    private void removeListener(@NotNull ResourceChangeListener listener) {
      myListeners.remove(listener);

      if (myListeners.isEmpty()) {
        unregisterListeners();
      }
    }

    private void registerListeners() {
      myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(myModule);
      myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    private void unregisterListeners() {
      myMessageBusConnection.disconnect();
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      events.stream()
        .filter(event -> {
          VirtualFile file = event.getFile();
          if (file == null) {
            return false;
          }

          VirtualFile parent = file.getParent();
          if (parent == null) {
            return false;
          }

          ResourceFolderType resType = ResourceFolderType.getFolderType(parent.getName());
          return ResourceFolderType.DRAWABLE == resType ||
                 ResourceFolderType.MIPMAP == resType;
        })
        .findAny()
        .ifPresent(event -> notice(Reason.IMAGE_RESOURCE_CHANGED, event.getFile()));
    }

    private boolean hasListeners() {
      return !myListeners.isEmpty();
    }
  }

  private class ConfigurationEventObserver implements ConfigurationListener {
    private final Configuration myConfiguration;
    private final List<ResourceChangeListener> myListeners = new ArrayList<>(2);

    private ConfigurationEventObserver(@NotNull Configuration configuration) {
      myConfiguration = configuration;
    }

    private void addListener(@NotNull ResourceChangeListener listener) {
      if (myListeners.isEmpty()) {
        registerListeners();
      }
      myListeners.add(listener);
    }

    private void removeListener(@NotNull ResourceChangeListener listener) {
      myListeners.remove(listener);

      if (myListeners.isEmpty()) {
        unregisterListeners();
      }
    }

    private boolean hasListeners() {
      return !myListeners.isEmpty();
    }

    private void registerListeners() {
      myConfiguration.addListener(this);
    }

    private void unregisterListeners() {
      myConfiguration.removeListener(this);
    }

    // ---- Implements ConfigurationListener ----

    @Override
    public boolean changed(int flags) {
      if ((flags & MASK_RENDERING) != 0) {
        notice(Reason.CONFIGURATION_CHANGED, null);
      }
      return true;
    }
  }

  /**
   * Interface that should be implemented by clients interested in resource edits and events that affect resources.
   */
  public interface ResourceChangeListener {
    /**
     * One or more resources have changed.
     *
     * @param reason the set of reasons that the resources have changed since the last notification
     */
    void resourcesChanged(@NotNull ImmutableSet<Reason> reason);
  }

  /**
   * A version timestamp of the resources. This snapshot version is immutable, so you can hold on
   * to it and compare it with your most recent version.
   */
  public static class ResourceVersion {
    private final long myResourceGeneration;
    private final long myFileGeneration;
    private final long myConfigurationGeneration;
    private final long myProjectConfigurationGeneration;
    private final long myOtherGeneration;

    private ResourceVersion(long resourceGeneration,
                            long fileGeneration,
                            long configurationGeneration,
                            long projectConfigurationGeneration,
                            long otherGeneration) {
      myResourceGeneration = resourceGeneration;
      myFileGeneration = fileGeneration;
      myConfigurationGeneration = configurationGeneration;
      myProjectConfigurationGeneration = projectConfigurationGeneration;
      myOtherGeneration = otherGeneration;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResourceVersion version = (ResourceVersion)o;

      return myResourceGeneration == version.myResourceGeneration &&
          myFileGeneration == version.myFileGeneration &&
          myConfigurationGeneration == version.myConfigurationGeneration &&
          myProjectConfigurationGeneration == version.myProjectConfigurationGeneration &&
          myOtherGeneration == version.myOtherGeneration;
    }

    @Override
    public int hashCode() {
      return HashCodes.mix(Long.hashCode(myResourceGeneration),
                           Long.hashCode(myFileGeneration),
                           Long.hashCode(myConfigurationGeneration),
                           Long.hashCode(myProjectConfigurationGeneration),
                           Long.hashCode(myOtherGeneration));
    }

    @Override
    public @NotNull String toString() {
      return "ResourceVersion{" +
             "resource=" + myResourceGeneration +
             ", file=" + myFileGeneration +
             ", configuration=" + myConfigurationGeneration +
             ", projectConfiguration=" + myProjectConfigurationGeneration +
             ", other=" + myOtherGeneration +
             '}';
    }
  }

  /**
   * The reason the resources have changed.
   */
  public enum Reason {
    /**
     * An edit which affects the resource repository was performed (e.g. changing the value of a string
     * is a resource edit, but editing the layout parameters of a widget in a layout file is not).
     */
    RESOURCE_EDIT,

    /**
     * Edit of a file that is being observed (if you're for example watching a menu file, this will include
     * edits in whitespace etc.
     */
    EDIT,

    /**
     * The configuration changed (for example, the locale may have changed).
     */
    CONFIGURATION_CHANGED,

    /**
     * The module SDK changed.
     */
    SDK_CHANGED,

    /**
     * The active variant changed, which affects available resource sets and values.
     */
    VARIANT_CHANGED,

    /**
     * A sync happened. This can change dynamically generated resources for example.
     */
    GRADLE_SYNC,

    /**
     * Project build. Not a direct resource edit, but for example when a custom view
     * is compiled it can affect how a resource like layouts should be rendered.
     */
    PROJECT_BUILD,

    /**
     * Image changed. This might be needed to invalidate layoutlib drawable caches.
     */
    IMAGE_RESOURCE_CHANGED
  }
}
