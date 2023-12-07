// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Experimental
public class GradleBlockModelMap {

  @SuppressWarnings("rawtypes")
  private Map<Class<? extends GradleDslModel>, Map<Class<? extends GradleDslModel>, BlockModelBuilder<?, ?>>>
    blockMapCache = new ConcurrentHashMap<>();

  @SuppressWarnings("rawtypes")
  private Map<Class<? extends GradlePropertiesDslElement>, ImmutableMap<String, PropertiesElementDescription>> elementMapCache =
    new ConcurrentHashMap<>();

  GradleBlockModelMap() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        blockMapCache.clear();
        elementMapCache.clear();
      }

      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        blockMapCache.clear();
        elementMapCache.clear();
      }
    });
  }

  @SuppressWarnings("unchecked")
  public <M extends GradleDslModel, PM extends GradleDslModel, PD extends GradlePropertiesDslElement> M getBlockModel(PD dslElement,
                                                                                                                      Class<? extends PM> parentType,
                                                                                                                      Class<M> modelInterface) {
    validateTypes(dslElement, parentType, modelInterface);
    Map<Class<? extends GradleDslModel>, BlockModelBuilder<?, ?>> blockMap = getOrCreateBlockMap(parentType);
    BlockModelBuilder<M, PD> builder = (BlockModelBuilder<M, PD>)blockMap.get(modelInterface);
    if (builder == null) {
      throw new IllegalArgumentException("Block model for " + modelInterface + " is not registered in " + parentType);
    }
    return builder.create(dslElement);
  }

  private <P extends GradleDslModel, M extends GradleDslModel> void validateTypes(GradlePropertiesDslElement element,
                                                                                  Class<? extends P> parentType,
                                                                                  Class<M> modelInterface) {

  }

  @NotNull
  public ImmutableMap<String, PropertiesElementDescription> getOrCreateElementMap(Class<? extends GradlePropertiesDslElement> parentType) {
    return elementMapCache.computeIfAbsent(parentType, GradleBlockModelMap::calculateElements);
  }

  @NotNull
  private Map<Class<? extends GradleDslModel>, BlockModelBuilder<?, ?>> getOrCreateBlockMap(Class<? extends GradleDslModel> parentType) {
    return blockMapCache.computeIfAbsent(parentType,
                                         GradleBlockModelMap::calculateBlocks);
  }

  @NotNull
  public <T extends GradleDslModel> Set<Class<? extends GradleDslModel>> childrenOf(Class<T> parentType) {
    Map<Class<? extends GradleDslModel>, BlockModelBuilder<?, ?>> modelsMap = getOrCreateBlockMap(parentType);
    return Collections.unmodifiableSet(modelsMap.keySet());
  }

  @TestOnly
  void resetCache() {
    blockMapCache.clear();
    elementMapCache.clear();
  }

  @SuppressWarnings("rawtypes")
  public static ImmutableMap<String, PropertiesElementDescription> getElementMap(Class<? extends GradlePropertiesDslElement> parentType) {
    return ApplicationManager.getApplication().getService(GradleBlockModelMap.class).getOrCreateElementMap(parentType);
  }

  public static <T extends GradleDslModel, P extends GradleDslModel, PD extends GradlePropertiesDslElement> T get(PD dslElement,
                                                                                                                  Class<? extends P> parentType,
                                                                                                                  Class<T> modelInterface) {
    return getInstance().getBlockModel(dslElement, parentType, modelInterface);
  }

  public static GradleBlockModelMap getInstance() {
    return ApplicationManager.getApplication().getService(GradleBlockModelMap.class);
  }

  private static ImmutableMap<String, PropertiesElementDescription> calculateElements(Class<? extends GradlePropertiesDslElement> pt) {
    ImmutableMap.Builder<String, PropertiesElementDescription> builder =
      ImmutableMap.builder();
    BlockModelProvider.EP.forEachExtensionSafe(p -> {
      Map<String, PropertiesElementDescription<?>> elementsMap =
        p.elementsMap();
      if (elementsMap != null) {
        builder.putAll(elementsMap);
      }
    });
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private static Map<Class<? extends GradleDslModel>, BlockModelBuilder<?, ?>> calculateBlocks(Class<? extends GradleDslModel> parentType) {
    Map<Class<? extends GradleDslModel>, BlockModelBuilder<?, ?>>
      result = new HashMap<>();

    BlockModelProvider.EP.forEachExtensionSafe(
      p -> {
        if (!parentType.isAssignableFrom(p.getParentClass())) {
          return;
        }
        List<BlockModelBuilder<?, ?>>
          builders = p.availableModels();
        if (builders != null) {
          builders.forEach(builder -> {
            result.put(
              builder.modelClass(),
              builder);
          });
        }
      });
    return result;
  }

  @ApiStatus.Experimental
  public interface BlockModelBuilder<M extends GradleDslModel, P extends GradlePropertiesDslElement> {
    @NotNull
    Class<M> modelClass();

    @NotNull
    M create(@NotNull P parent);
  }

  @ApiStatus.Experimental
  public interface BlockModelProvider<ParentModel extends GradleDslModel, ParentDsl extends GradlePropertiesDslElement> {
    ExtensionPointName<BlockModelProvider> EP = ExtensionPointName.create("org.jetbrains.idea.gradle.dsl.blockModel");

    @NotNull
    Class<ParentModel> getParentClass();

    @NotNull List<BlockModelBuilder<?, ParentDsl>> availableModels();

    @NotNull Map<String, PropertiesElementDescription<?>> elementsMap();
  }
}


