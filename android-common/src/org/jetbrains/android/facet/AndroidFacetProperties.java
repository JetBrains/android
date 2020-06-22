// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet;

import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/**
 * Android-specific information saved in the IML file corresponding to an Android module.
 *
 * <p>These objects are serialized to XML by {@link AndroidFacetConfiguration} using {@link
 * com.intellij.util.xmlb.XmlSerializer}.
 *
 * <p>Avoid using instances of this class if at all possible. This information should be provided by
 * {@link com.android.tools.idea.projectsystem.AndroidProjectSystem} and it is up to the project system used by the project to choose how
 * this information is obtained and persisted.
 */
@SuppressWarnings("deprecation")
public class AndroidFacetProperties extends JpsAndroidModuleProperties {

}
