/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.converter;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.ModuleSettings;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.OrderEntryFactory;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModuleConverter1 extends ConversionProcessor<ModuleSettings> {
  private static final String PLATFORM_NAME_ATTRIBUTE = "PLATFORM_NAME";

  @Override
  public boolean isConversionNeeded(ModuleSettings moduleSettings) {
    Element confElement = AndroidConversionUtil.findAndroidFacetConfigurationElement(moduleSettings);
    return confElement != null && AndroidConversionUtil.getOptionValue(confElement, PLATFORM_NAME_ATTRIBUTE) != null;
  }

  @Override
  public void process(ModuleSettings moduleSettings) throws CannotConvertException {
    Element confElement = AndroidConversionUtil.findAndroidFacetConfigurationElement(moduleSettings);
    assert confElement != null;

    Element platformNameElement = AndroidConversionUtil.getOptionElement(confElement, PLATFORM_NAME_ATTRIBUTE);
    String platformName = platformNameElement != null ? platformNameElement.getAttributeValue(AndroidConversionUtil.OPTION_VALUE_ATTRIBUTE) : null;

    if (platformName == null) return;

    removeOldDependencies(moduleSettings, platformName);
    confElement.removeContent(platformNameElement);

    Library androidLibrary = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryByName(platformName);
    if (androidLibrary != null) {

      AndroidPlatform androidPlatform = AndroidPlatform.parse(androidLibrary, null, null);

      if (androidPlatform != null) {

        Sdk androidSdk = AndroidSdkUtils.findAppropriateAndroidPlatform(androidPlatform.getTarget(), androidPlatform.getSdkData(), false);

        if (androidSdk == null) {
          androidSdk = AndroidSdkUtils.createNewAndroidPlatform(androidPlatform.getTarget(), androidPlatform.getSdkData().getPath(), false);

          if (androidSdk != null) {
            final SdkModificator modificator = androidSdk.getSdkModificator();

            for (OrderRootType type : OrderRootType.getAllTypes()) {
              for (VirtualFile root : androidLibrary.getFiles(type)) {
                modificator.addRoot(root, type);
              }
            }
            modificator.commitChanges();
          }
        }

        if (androidSdk != null) {
          addNewDependency(moduleSettings, androidSdk.getName());
        }
      }
    }
  }

  private static void addNewDependency(ModuleSettings moduleSettings, @NotNull String jdkName) {
    Element moduleManagerElement = moduleSettings.getComponentElement(ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT);
    if (moduleManagerElement != null) {
      Element newEntryElement = new Element(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
      newEntryElement.setAttribute("type", "jdk");
      newEntryElement.setAttribute("jdkName", jdkName);
      newEntryElement.setAttribute("jdkType", AndroidSdkType.SDK_NAME);
      moduleManagerElement.addContent(newEntryElement);
    }
  }

  private static void removeOldDependencies(ModuleSettings moduleSettings, @NotNull String libName) {
    Element moduleManagerElement = moduleSettings.getComponentElement(ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT);
    if (moduleManagerElement != null) {
      for (Element entryElement : moduleManagerElement.getChildren(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {

        if (libName.equals(entryElement.getAttributeValue("name")) &&
            "library".equals(entryElement.getAttributeValue("type")) &&
            "application".equals(entryElement.getAttributeValue("level"))) {
          moduleManagerElement.removeContent(entryElement);
        }

        if ("jdk".equals(entryElement.getAttributeValue("type")) || "inheritedJdk".equals(entryElement.getAttributeValue("type"))) {
          moduleManagerElement.removeContent(entryElement);
        }
      }
    }
  }
}
