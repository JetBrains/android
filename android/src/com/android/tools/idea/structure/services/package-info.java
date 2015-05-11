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

/**
 * A developer service is a collection of code, dependencies, and resources which can be installed
 * into a module. It aims to replace the manual steps of adding libraries, copying code from get
 * started docs, and initializing values like feature keys and auth credentials, automating as much
 * of the service creation as possible.
 * <p/>
 * Additionally, we collect a list of developer services and present them to the user in a single
 * location, preventing a unified experience and grouping disparate APIs by category.
 *
 * A service is defined by a service.xml file, whose content lays out a UI view that can be bound
 * to various variables, and its context, which is responsible for storing those variables.
 * TODO: Link to service.xml documentation here.
 *
 * Services are provided by plugins, which should implement the
 * {@link com.android.tools.idea.structure.services.DeveloperServiceCreators}
 * interface and return a list of service initializers that they expose.
 */
package com.android.tools.idea.structure.services;
