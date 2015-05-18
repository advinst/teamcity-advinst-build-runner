package jetbrains.buildServer.advinst.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.advinst.common.AdvinstAipReader;
import jetbrains.buildServer.advinst.common.AdvinstConstants;
import jetbrains.buildServer.advinst.common.AdvinstException;
import jetbrains.buildServer.agent.BuildFinishedStatus;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import jetbrains.buildServer.agent.inspections.InspectionReporter;
import jetbrains.buildServer.agent.runner.BuildServiceAdapter;
import jetbrains.buildServer.agent.runner.ProgramCommandLine;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class AdvinstBuildService extends BuildServiceAdapter
{

  private final ArtifactsWatcher mArtifactsWatcher;
  private final InspectionReporter mInspectionReporter;
  private File mOutputDirectory;
  private File mXmlReportFile;
  private File mHtmlReportFile;
  private List<File> mTempFiles = new ArrayList<File>();

  public AdvinstBuildService(final ArtifactsWatcher artifactsWatcher, final InspectionReporter inspectionReporter)
  {
    mArtifactsWatcher = artifactsWatcher;
    mInspectionReporter = inspectionReporter;
  }

  @Override
  public void afterInitialized() throws RunBuildException
  {
    super.afterInitialized();
  }

  @Override
  public void beforeProcessStarted() throws RunBuildException
  {
    getLogger().progressMessage("Running Advanced Installer");
  }

  @Override
  public void afterProcessFinished() throws RunBuildException
  {
    super.afterProcessFinished();
    for (File file : mTempFiles)
    {
      FileUtil.delete(file);
    }
    mTempFiles.clear();
  }

  @NotNull
  @Override
  public BuildFinishedStatus getRunResult(final int exitCode)
  {
    if (exitCode != 0)
    {
      return BuildFinishedStatus.FINISHED_FAILED;
    }

    return BuildFinishedStatus.FINISHED_SUCCESS;
  }

  @NotNull
  @Override
  public ProgramCommandLine makeProgramCommandLine() throws RunBuildException
  {
    return new ProgramCommandLine()
    {
      @NotNull
      @Override
      public String getExecutablePath() throws RunBuildException
      {
        return getAdvinstComPath();
      }

      @NotNull
      @Override
      public String getWorkingDirectory() throws RunBuildException
      {
        return getCheckoutDirectory().getPath();
      }

      @NotNull
      @Override
      public List<String> getArguments() throws RunBuildException
      {
        return getAdvinstArguments();
      }

      @NotNull
      @Override
      public Map<String, String> getEnvironment() throws RunBuildException
      {
        return getBuildParameters().getEnvironmentVariables();
      }
    };
  }

  @NotNull
  private String getAdvinstComPath() throws RunBuildException
  {
    String advinstRoot = getRunnerParameters().get(AdvinstConstants.SETTINGS_ADVINST_ROOT);
    if (StringUtil.isEmpty(advinstRoot))
    {
      throw new RunBuildException("Advanced Installer root was not specified in build settings");
    }

    if (Files.notExists(Paths.get(advinstRoot), LinkOption.NOFOLLOW_LINKS))
    {
      throw new RunBuildException("An invalid Advanced Installer root was specified in build settings. Path: " + advinstRoot);
    }

    File advinstComPath = new File(advinstRoot, AdvinstConstants.ADVINST_BIN_FOLDER + AdvinstConstants.ADVINST_BINARY);
    if (Files.notExists(Paths.get(advinstComPath.getPath()), LinkOption.NOFOLLOW_LINKS))
    {
      throw new RunBuildException("Cannot detect the Advanced Installer command line tool. Path: " + advinstComPath.getPath());
    }

    return advinstComPath.getPath();
  }

  @NotNull
  public List<String> getAdvinstArguments() throws RunBuildException
  {
    List<String> arguments = new ArrayList<String>();
    List<String> commands = new ArrayList<String>();
    arguments.add("/execute");

    String absoluteAipPath;
    String buildName;
    String packageName;
    String packageFolder;
    boolean resetSig = false;
    //------------------------------------------------------------------------
    // Compute and validate AIP project path. It can be either an absolute path
    // or relative to the checkout folder.
    {
      absoluteAipPath = getRunnerParameters().get(AdvinstConstants.SETTINGS_ADVINST_AIP_PATH);
      if (StringUtil.isEmpty(absoluteAipPath))
      {
        throw new RunBuildException("Advanced Installer project path (.AIP) was not specified in build settings");
      }
      File aipPath = new File(absoluteAipPath);
      if (!aipPath.isAbsolute())
      {
        absoluteAipPath = new File(getCheckoutDirectory(), aipPath.getPath()).getAbsolutePath();
      }

      if (Files.notExists(Paths.get(absoluteAipPath)))
      {
        throw new RunBuildException(String.format("Advanced Installer project file not found. Path: %s", absoluteAipPath));
      }

      arguments.add(absoluteAipPath);
    }

    //------------------------------------------------------------------------
    // compute and validate build name.
    {
      buildName = getRunnerParameters().get(AdvinstConstants.SETTINGS_ADVINST_AIP_BUILD);
      if (StringUtil.isNotEmpty(buildName))
      {
        AdvinstAipReader aipReader = new AdvinstAipReader(absoluteAipPath);
        try
        {
          if (!buildName.isEmpty() && !aipReader.getBuilds().contains(buildName))
          {
            throw new RunBuildException("The specified build is not present in the project file");
          }
        }
        catch (AdvinstException ex)
        {
          throw new RunBuildException(ex.getMessage());
        }
      }
    }

    //------------------------------------------------------------------------
    //compute and validate the output package name
    {
      packageName = getRunnerParameters().get(AdvinstConstants.SETTINGS_ADVINST_AIP_SETUP_FILE);
    }

    //------------------------------------------------------------------------
    // Compute and validate output folder path. It can be either an absolute path
    // or relative to the build workspace folder.
    {
      packageFolder = getRunnerParameters().get(AdvinstConstants.SETTINGS_ADVINST_AIP_SETUP_FOLDER);
      if (StringUtil.isNotEmpty(buildName))
      {
        File dir = new File(packageFolder);
        if (!dir.isAbsolute())
        {
          packageFolder = new File(getCheckoutDirectory(), dir.getPath()).getAbsolutePath();
        }
      }

    }

    {
      resetSig = getRunnerParameters().containsKey(AdvinstConstants.SETTINGS_ADVINST_AIP_DONOTSIGN)
              && getRunnerParameters().get(AdvinstConstants.SETTINGS_ADVINST_AIP_DONOTSIGN).equals(Boolean.TRUE.toString());
    }

    if (StringUtil.isNotEmpty(packageName))
    {
      commands.add(String.format("SetPackageName \"%s\" -buildname \"%s\"", packageName, buildName));
    }

    if (StringUtil.isNotEmpty(packageFolder))
    {
      commands.add(String.format("SetOutputLocation -buildname \"%s\" -path \"%s\"", buildName, packageFolder));
    }

    if (resetSig)
    {
      commands.add("ResetSig");
    }

    commands.add(String.format(StringUtil.isEmpty(buildName) ? "Build" : "Build -buildslist \"%s\"", buildName));

    File commandsFile;
    try
    {
      commandsFile = createAicFile(commands);
      arguments.add(commandsFile.getPath());
      mTempFiles.add(commandsFile);
    }
    catch (IOException ex)
    {
      throw RunBuildException.create(null, ex.getMessage());
    }

    return arguments;
  }

  @NotNull
  private static File createAicFile(final List<String> aCommands) throws IOException
  {

    File aicFile = File.createTempFile("aic", null);
    FileOutputStream outStream = new FileOutputStream(aicFile);
    OutputStreamWriter writer = null;
    try
    {
      writer = new OutputStreamWriter(outStream, "UTF-16");
      writer.write(AdvinstConstants.ADVINST_AIC_HEADER + "\r\n");
      for (String command : aCommands)
      {
        writer.write(command + "\r\n");
      }
    }
    finally
    {
      try
      {
        if (null != writer)
        {
          writer.close();
        }
      }
      catch (IOException ex)
      {
      }
    }
    return aicFile;
  }
}
