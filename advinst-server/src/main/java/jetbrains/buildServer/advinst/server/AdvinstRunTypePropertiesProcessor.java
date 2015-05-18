package jetbrains.buildServer.advinst.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.advinst.common.AdvinstConstants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.StringUtil;

public class AdvinstRunTypePropertiesProcessor implements PropertiesProcessor
{

  public Collection<InvalidProperty> process(Map<String, String> properties)
  {
    List<InvalidProperty> result = new ArrayList<InvalidProperty>();

    final Path advinstRoot = Paths.get(properties.get(AdvinstConstants.SETTINGS_ADVINST_ROOT));
    if (PropertiesUtil.isEmptyOrNull(advinstRoot.toString()) || Files.notExists(advinstRoot))
    {
      result.add(new InvalidProperty(AdvinstConstants.SETTINGS_ADVINST_ROOT,
              "Advanced Installer installation root must be specified."));
    }

    final String advinstAipPath = properties.get(AdvinstConstants.SETTINGS_ADVINST_AIP_PATH);
    if (PropertiesUtil.isEmptyOrNull(advinstAipPath))
    {
      result.add(new InvalidProperty(AdvinstConstants.SETTINGS_ADVINST_AIP_PATH,
              "Advanced Installer project path must be specified."));
    }

    final String advinstBuild = properties.get(AdvinstConstants.SETTINGS_ADVINST_AIP_BUILD);
    final String outputPackage = properties.get(AdvinstConstants.SETTINGS_ADVINST_AIP_SETUP_FILE);
    final String outputFolder = properties.get(AdvinstConstants.SETTINGS_ADVINST_AIP_SETUP_FOLDER);
    if (StringUtil.isEmpty(advinstBuild) && StringUtil.isNotEmpty(outputPackage))
    {
      result.add(new InvalidProperty(AdvinstConstants.SETTINGS_ADVINST_AIP_SETUP_FILE,
              "This options can be used only if the build is specified. Leave the field blank otherwise."));
    }

    if (StringUtil.isEmpty(advinstBuild) && StringUtil.isNotEmpty(outputFolder))
    {
      result.add(new InvalidProperty(AdvinstConstants.SETTINGS_ADVINST_AIP_SETUP_FOLDER,
              "This options can be used only if the build is specified. Leave the field blank otherwise."));
    }

    return result;
  }
}
