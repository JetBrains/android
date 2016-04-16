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

import java.util.HashMap;

/**
 * A simple mapping between service identifier and DeveloperService instance. Intent is to provide external content a way to express what
 * service they depend on to complete a given action. For example, a tutorial may include an action to add a given set of dependencies. To
 * do this, it needs the appropriate DeveloperService instance to install the correct dependencies. {@see DeveloperServiceMetadata.getId()}
 * which should couple to your id field in your service.xml.
 *
 * TODO: This class is mostly for documentation at this point but we should likely scale it out with further functionality (and rename at
 * that time). If this doesn't add additional functionality in the near term, consider removing this class in favor of a typed map.
 */
public class DeveloperServiceMap extends HashMap<String, DeveloperService> {


}
