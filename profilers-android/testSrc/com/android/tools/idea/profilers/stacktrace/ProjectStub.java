/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers.stacktrace;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.messages.MessageBus;
import java.util.Map;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * Copy of {@link DummyProject}.
 */
final class ProjectStub extends UserDataHolderBase implements Project {
  private static class ProjectStubHolder {
    private static final ProjectStub ourInstance = new ProjectStub();
  }

  @NotNull
  public static Project getInstance() {
    return ProjectStubHolder.ourInstance;
  }

  private ProjectStub() {
  }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return "";
  }

  @Override
  @NotNull
  @NonNls
  public String getLocationHash() {
    return "sample";
  }

  @Override
  @Nullable
  public String getProjectFilePath() {
    return null;
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  @Nullable
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public void save() {
  }

  @Nullable
  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return null;
  }

  @Override
  public <T> T [] getComponents(@NotNull Class<T> baseClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  @Override
  public boolean isInjectionForExtensionSupported() {
    return false;
  }

  @NotNull
  @Override
  public ExtensionsArea getExtensionArea() {
    throw new UnsupportedOperationException("getExtensionArea is not implement in : " + getClass());
  }

  @Override
  public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass,
                                                        @NotNull Object key,
                                                        @NotNull PluginId pluginId) {
    return null;
  }

  @NotNull
  @Override
  public <T> Class<T> loadClass(@NotNull String className,
                                @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
    throw new ClassNotFoundException();
  }

  @Override
  public @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
    return isExtension ? ActivityCategory.PROJECT_EXTENSION : ActivityCategory.PROJECT_SERVICE;
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  @NotNull
  public Condition<?> getDisposed() {
    return o -> isDisposed();
  }

  @Override
  public <T> T getService(@NotNull Class<T> serviceClass) {
    return null;
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
  }

  @Override
  public @NotNull
  RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId) {
    return new RuntimeException(message);
  }

  @Override
  public @NotNull
  RuntimeException createError(@NotNull @NonNls String message,
                               @NotNull PluginId pluginId,
                               @Nullable Map<String, String> attachments) {
    return new RuntimeException(message);
  }

  @Override
  public @NotNull
  RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    ExceptionUtilRt.rethrowUnchecked(error);
    return new RuntimeException(error);
  }
}
