package org.jetbrains.android.converter;

import com.intellij.conversion.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.Processor;
import org.jdom.Element;
import org.jetbrains.android.compiler.artifact.AndroidArtifactPropertiesProvider;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProguardOptionsConverterProvider extends ConverterProvider {

  protected AndroidProguardOptionsConverterProvider() {
    super("android-proguard-options");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Android ProGuard options will be converted to new format";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new MyProjectConverter();
  }

  @Override
  public boolean canDetermineIfConversionAlreadyPerformedByProjectFiles() {
    return false;
  }

  private static class MyProjectConverter extends ProjectConverter {
    @Nullable
    @Override
    public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
      return new MyModuleFileConverter();
    }

    @Nullable
    @Override
    public ConversionProcessor<ArtifactsSettings> createArtifactsConverter() {
      return new MyArtifactsConverter();
    }
  }

  private static class MyModuleFileConverter extends ConversionProcessor<ModuleSettings> {
    private static final String PROGUARD_CFG_PATH_OPTION = "PROGUARD_CFG_PATH";

    @Override
    public boolean isConversionNeeded(ModuleSettings settings) {
      final Element confElement = AndroidConversionUtil.findAndroidFacetConfigurationElement(settings);
      return confElement != null && Boolean.parseBoolean(AndroidConversionUtil.getOptionValue(confElement, "RUN_PROGUARD"));
    }

    @Override
    public void process(ModuleSettings settings) throws CannotConvertException {
      final Element confElement = AndroidConversionUtil.findAndroidFacetConfigurationElement(settings);

      if (confElement == null) {
        return;
      }
      final Element proguardCfgOptionElement = AndroidConversionUtil.getOptionElement(confElement, PROGUARD_CFG_PATH_OPTION);
      String proguardCfgRelPath = proguardCfgOptionElement != null
                                  ? proguardCfgOptionElement.getAttributeValue(AndroidConversionUtil.OPTION_VALUE_ATTRIBUTE)
                                  : null;

      if (proguardCfgRelPath == null || proguardCfgRelPath.isEmpty()) {
        proguardCfgRelPath = "/" + AndroidCommonUtils.PROGUARD_CFG_FILE_NAME;
      }
      if (proguardCfgOptionElement != null) {
        confElement.removeContent(proguardCfgOptionElement);
      }
      final String proguardCfgFileUrl = VfsUtilCore.pathToUrl('$' + PathMacroUtil.MODULE_DIR_MACRO_NAME + '$' + proguardCfgRelPath);
      final Element includeSystemCfgElement = confElement.getChild("includeSystemProguardFile");
      final String includeSystemCfgStr = includeSystemCfgElement != null ? includeSystemCfgElement.getText() : null;

      if (includeSystemCfgElement != null) {
        confElement.removeContent(includeSystemCfgElement);
      }
      final List<String> proguardCfgUrls = new ArrayList<String>();

      if (!Boolean.FALSE.toString().equals(includeSystemCfgStr)) {
        proguardCfgUrls.add(AndroidCommonUtils.PROGUARD_SYSTEM_CFG_FILE_URL);
      }
      proguardCfgUrls.add(proguardCfgFileUrl);
      final Element newElement = new Element("proGuardCfgFiles");

      for (String url : proguardCfgUrls) {
        final Element fileElement = new Element("file");
        fileElement.setText(url);
        newElement.addContent(fileElement);
      }
      confElement.addContent(newElement);
    }
  }

  private static class MyArtifactsConverter extends ConversionProcessor<ArtifactsSettings> {

    private static final String RUN_PROGUARD_OPTION_NAME = "runProGuard";

    @Override
    public boolean isConversionNeeded(ArtifactsSettings settings) {
      return processAndroidPropertiesElements(settings, new Processor<Element>() {
        @Override
        public boolean process(Element element) {
          return Boolean.parseBoolean(AndroidConversionUtil.getOptionValue(element, RUN_PROGUARD_OPTION_NAME));
        }
      });
    }

    @Override
    public void process(ArtifactsSettings settings) throws CannotConvertException {
      processAndroidPropertiesElements(settings, new Processor<Element>() {
        @Override
        public boolean process(Element element) {
          if (Boolean.parseBoolean(AndroidConversionUtil.getOptionValue(element, RUN_PROGUARD_OPTION_NAME))) {
            doConvertArtifact(element);
          }
          return false;
        }
      });
    }

    private static void doConvertArtifact(@NotNull Element element) {
      final Element proguardCfgOptionElement = AndroidConversionUtil.getOptionElement(element, "proGuardCfgFileUrl");

      final String proguardCfgFileUrl = proguardCfgOptionElement != null
                                        ? proguardCfgOptionElement.getAttributeValue(AndroidConversionUtil.OPTION_VALUE_ATTRIBUTE)
                                        : null;
      final Element includeSystemCfgElement = AndroidConversionUtil.getOptionElement(element, "includeSystemProGuardCfgFile");
      final String includeSystemCfgStr = includeSystemCfgElement != null
                                         ? includeSystemCfgElement.getAttributeValue(AndroidConversionUtil.OPTION_VALUE_ATTRIBUTE)
                                         : null;
      element.removeContent(proguardCfgOptionElement);

      if (includeSystemCfgElement != null) {
        element.removeContent(includeSystemCfgElement);
      }
      final List<String> proguardCfgUrls = new ArrayList<String>();

      if (Boolean.parseBoolean(includeSystemCfgStr)) {
        proguardCfgUrls.add(AndroidCommonUtils.PROGUARD_SYSTEM_CFG_FILE_URL);
      }
      if (proguardCfgFileUrl != null && !proguardCfgFileUrl.isEmpty()) {
        proguardCfgUrls.add(proguardCfgFileUrl);
      }

      if (proguardCfgUrls.isEmpty()) {
        return;
      }
      final Element listElement = new Element("list");

      for (String url : proguardCfgUrls) {
        final Element fileElement = new Element("option");
        fileElement.setAttribute("value", url);
        listElement.addContent(fileElement);
      }
      final Element newElement = new Element("option");
      newElement.setAttribute("name", "proGuardCfgFiles");
      newElement.addContent(listElement);
      element.addContent(newElement);
    }

    private static boolean processAndroidPropertiesElements(ArtifactsSettings settings, Processor<Element> processor) {
      for (Element artifactElement : settings.getArtifacts()) {
        for (Element propertiesElement : artifactElement.getChildren("properties")) {
          final String propertiesId = propertiesElement.getAttributeValue("id");

          if (AndroidArtifactPropertiesProvider.ANDROID_PROPERTIES_ID.equals(propertiesId)) {
            final Element optionsElement = propertiesElement.getChild("options");

            if (optionsElement != null) {
              if (processor.process(optionsElement)) {
                return true;
              }
            }
          }
        }
      }
      return false;
    }
  }
}
