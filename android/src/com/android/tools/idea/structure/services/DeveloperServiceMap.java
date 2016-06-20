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
package com.android.tools.idea.structure.services;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A simple mapping between service identifier and DeveloperService instances. Intent is to provide external content a way to express what
 * service they depend on to complete a given action. For example, a tutorial may include an action to add a given set of dependencies. To
 * do this, it needs the appropriate DeveloperService instance to install the correct dependencies. {@see DeveloperServiceMetadata.getId()}
 * which should couple to your id field in your service.xml.
 *
 * TODO: Make this a project level component? http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_components.html
 * This would allow us to just pass the Project to the panel and we wouldn't need to pass where we're in an action context (as the project
 * is already available).
 * TODO: Allow for new modules being added to the current project. There should be an event to listen for. Also will need to scrub for
 * cached values that assume the set of modules are fixed (ie button states need to update)
 */
public class DeveloperServiceMap {

  private HashMap<String, DeveloperServiceList> myServiceCache = new HashMap<>();

  public DeveloperServiceMap(@NotNull Set<Module> modules) {
    for (Module module : modules) {
      for (DeveloperService service : DeveloperServices.getAll(module)) {
        put(service.getMetadata().getId(), service);
      }
    }
  }

  /**
   * Add to existing list if present or create new.
   */
  private void put(@NotNull String key, @NotNull DeveloperService service) {
    if (myServiceCache.containsKey(key)) {
      List<DeveloperService> developerServices = myServiceCache.get(key);
      developerServices.add(service);
      return;
    }

    DeveloperServiceList services = new DeveloperServiceList();
    services.add(service);
    myServiceCache.put(key, services);
  }

  @Nullable("Null when key doesn't match known services")
  public DeveloperServiceList get(@NotNull String key) {
    return myServiceCache.get(key);
  }

  /**
   * Gets the single service for a given module service id pair.
   */
  @Nullable("Null if no pairing found.")
  public DeveloperService get(@NotNull String key, @NotNull Module module) {
    List<DeveloperService> services = myServiceCache.get(key);
    for (DeveloperService service : services) {
      if (service.getModule().equals(module)) {
        return service;
      }
    }
    getLog().error("No service found for module " + module.getName());
    return null;
  }

  private static Logger getLog() {
    return Logger.getInstance(DeveloperServiceMap.class);
  }

  public static class DeveloperServiceList extends ArrayList<DeveloperService> {

    private Set<Module> myModules = new HashSet<>();

    /**
     * Gets the single service for a given module service id pair.
     */
    @Nullable("Null if no pairing found.")
    public DeveloperService get(@NotNull Module module) {
      for (DeveloperService service : this) {
        if (service.getModule().equals(module)) {
          return service;
        }
      }
      getLog().error("No service found for module " + module.getName());
      return null;
    }

    /**
     * Convenience method to get project which is consistent across all service instances.
     */
    @Nullable("Null when no services found.")
    public Project getProject() {
      if (size() == 0) {
        return null;
      }
      return get(0).getModule().getProject();
    }

    /**
     * Gets a representative instance of the service metadata. This is the same across all modules thus safe to get an arbitrary instance.
     */
    @Nullable("If no service in list.")
    public DeveloperServiceMetadata getMetadata() {
      if (size() == 0) {
        return null;
      }
      return get(0).getMetadata();
    }

    /**
     * Convenience method to get the list of all available android modules, inferred from the set of modules discovered across all
     * service instances.
     */
    @NotNull
    public Set<Module> getModules() {
      synchronized (this) {
        if (myModules.isEmpty()) {
          for (DeveloperService service : this) {
            myModules.add(service.getModule());
          }
        }
      }
      return myModules;
    }

  }
}
