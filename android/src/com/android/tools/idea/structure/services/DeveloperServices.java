/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.services;

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.intellij.ProjectTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.structure.services.BuildSystemOperationsLookup.getBuildSystemOperations;

/**
 * Helper class which collects developer services from all plugins and provides a simple
 * interface to access them.
 */
public final class DeveloperServices {

  private static final Logger LOG = Logger.getInstance(DeveloperService.class);

  private static Multimap<Module, DeveloperService> ourServices = ArrayListMultimap.create();

  public static Iterable<DeveloperService> getAll(@NotNull Module module) {
    initializeFor(module);
    return ourServices.get(module);
  }

  @NotNull
  public static Iterable<DeveloperService> getFor(@NotNull Module module, final ServiceCategory category) {
    return Iterables.filter(getAll(module), new Predicate<DeveloperService>() {
      @Override
      public boolean apply(DeveloperService service) {
        return service.getCategory() == category;
      }
    });
  }

  private static void initializeFor(@NotNull final Module module) {
    if (ourServices.containsKey(module)) {
      return;
    }

    for (DeveloperServiceCreators creators : DeveloperServiceCreators.EP_NAME.getExtensions()) {
      try {
        for (DeveloperServiceCreator creator : creators.getCreators()) {
          DeveloperService service = creator.createService(module);
          if (service != null) {
            ourServices.put(module, service);
          }
        }
      }
      catch (Exception e) {
        LOG.warn("Caught exception while initializing services", e);
      }
    }

    getBuildSystemOperations(module.getProject()).initializeServices(module, new Runnable() {
      @Override
      public void run() {
        for (DeveloperService service : ourServices.get(module)) {
          service.updateInstalledState();
        }
      }
    });

    MessageBusConnection connection = module.getMessageBus().connect(module);
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleRemoved(Project project, Module moduleRemoved) {
        if (module == moduleRemoved) {
          ourServices.removeAll(module);
        }
      }
    });
  }
}
