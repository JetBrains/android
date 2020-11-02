/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.motion.adapters;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

public class MXProject  implements Project {
  @NotNull
  @Override
  public String getName() {
    return null;
  }

  @Override
  public VirtualFile getBaseDir() {
    return null;
  }

  @Nullable
  @Override
  public @SystemIndependent String getBasePath() {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Nullable
  @Override
  public @SystemIndependent String getProjectFilePath() {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @NotNull
  @Override
  public String getLocationHash() {
    return null;
  }

  @Override
  public void save() {

  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return null;
  }

  @NotNull
  @Override
  public PicoContainer getPicoContainer() {
    return null;
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    return null;
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @NotNull
  @Override
  public Condition<?> getDisposed() {
    return null;
  }

  @NotNull
  @Override
  public RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    return null;
  }

  @NotNull
  @Override
  public RuntimeException createError(@NotNull String message, @NotNull PluginId pluginId) {
    return null;
  }

  @NotNull
  @Override
  public RuntimeException createError(@NotNull String message, @NotNull PluginId pluginId, @Nullable Map<String, String> attachments) {
    return null;
  }

  @NotNull
  @Override
  public <T> Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) {
    return null;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

  }
}
