/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.android.builder.model.*;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.ChooseGradleHomeDialog;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import icons.AndroidIcons;
import org.gradle.StartParameter;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.startup.AndroidStudioSpecificInitializer.GRADLE_DAEMON_TIMEOUT_MS;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getLastUsedGradleHome;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  private static final Pattern ANDROID_GRADLE_PLUGIN_DEPENDENCY_PATTERN = Pattern.compile("['\"]com.android.tools.build:gradle:(.+)['\"]");

  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";

  /** The name of the gradle wrapper executable associated with the current OS. */
  @NonNls public static final String GRADLE_WRAPPER_EXECUTABLE_NAME =
    SystemInfo.isWindows ? SdkConstants.FN_GRADLE_WRAPPER_WIN : SdkConstants.FN_GRADLE_WRAPPER_UNIX;

  @NonNls private static final String GRADLE_EXECUTABLE_NAME =
    SystemInfo.isWindows ? SdkConstants.FN_GRADLE_WIN : SdkConstants.FN_GRADLE_UNIX;

  @NonNls public static final String GRADLEW_PROPERTIES_PATH =
    FileUtil.join(SdkConstants.FD_GRADLE_WRAPPER, SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES);

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);
  private static final Pattern GRADLE_JAR_NAME_PATTERN = Pattern.compile("gradle-(.*)-(.*)\\.jar");
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  /**
   * Finds characters that shouldn't be used in the Gradle path.
   *
   * I was unable to find any specification for Gradle paths. In my
   * experiments, Gradle only failed with slashes. This list may grow if
   * we find any other unsupported characters.
   */
  public static final CharMatcher ILLEGAL_GRADLE_PATH_CHARS_MATCHER = CharMatcher.anyOf("\\/");
  public static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN = Pattern.compile(".*-([^-]+)-([^.]+).zip");

  private GradleUtil() {
  }

  /**
   * This is temporary, until the model returns more outputs per artifact.
   * Deprecating since the model 0.13 provides multiple outputs per artifact if split apks are enabled.
   */
  @Deprecated
  @NotNull
  public static AndroidArtifactOutput getOutput(@NotNull AndroidArtifact artifact) {
    Collection<AndroidArtifactOutput> outputs = artifact.getOutputs();
    assert !outputs.isEmpty();
    AndroidArtifactOutput output = ContainerUtil.getFirstItem(outputs);
    assert output != null;
    return output;
  }

  @NotNull
  public static Icon getModuleIcon(@NotNull Module module) {
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject != null) {
      return androidProject.isLibrary() ? AndroidIcons.LibraryModule : AndroidIcons.AppModule;
    }
    return Projects.isGradleProject(module.getProject()) ? AllIcons.Nodes.PpJdk : AllIcons.Nodes.Module;
  }

  @Nullable
  public static AndroidProject getAndroidProject(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      if (androidProject != null) {
        return androidProject.getDelegate();
      }
    }
    return null;
  }

  /**
   * Returns the Gradle "logical" path (using colons as separators) if the given module represents a Gradle project or sub-project.
   *
   * @param module the given module.
   * @return the Gradle path for the given module, or {@code null} if the module does not represent a Gradle project or sub-project.
   */
  @Nullable
  public static String getGradlePath(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    return facet != null ? facet.getConfiguration().GRADLE_PROJECT_PATH : null;
  }

  /**
   * Returns the library dependencies in the given variant. This method checks dependencies in the "main" and "instrumentation tests"
   * artifacts. The dependency lookup is not transitive (only direct dependencies are returned.)
   *
   * @param variant the given variant.
   * @return the library dependencies in the given variant.
   */
  @NotNull
  public static List<AndroidLibrary> getDirectLibraryDependencies(@NotNull Variant variant) {
    List<AndroidLibrary> libraries = Lists.newArrayList();
    libraries.addAll(variant.getMainArtifact().getDependencies().getLibraries());
    AndroidArtifact testArtifact = IdeaAndroidProject.findInstrumentationTestArtifact(variant);
    if (testArtifact != null) {
      libraries.addAll(testArtifact.getDependencies().getLibraries());
    }
    return libraries;
  }

  @Nullable
  public static Module findModuleByGradlePath(@NotNull Project project, @NotNull String gradlePath) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        if (gradlePath.equals(gradleFacet.getConfiguration().GRADLE_PROJECT_PATH)) {
          return module;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return Lists.newArrayList(Splitter.on(SdkConstants.GRADLE_PATH_SEPARATOR).omitEmptyStrings().split(gradlePath));
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null && gradleFacet.getGradleProject() != null) {
      return gradleFacet.getGradleProject().getBuildFile();
    }
    // At the time we're called, module.getModuleFile() may be null, but getModuleFilePath returns the path where it will be created.
    File moduleFilePath = new File(module.getModuleFilePath());
    return getGradleBuildFile(moduleFilePath.getParentFile());
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File rootDir) {
    File gradleBuildFilePath = getGradleBuildFilePath(rootDir);
    return VfsUtil.findFileByIoFile(gradleBuildFilePath, true);
  }

  @NotNull
  public static File getGradleBuildFilePath(@NotNull File rootDir) {
    return new File(rootDir, SdkConstants.FN_BUILD_GRADLE);
  }

  @Nullable
  public static VirtualFile getGradleSettingsFile(@NotNull File rootDir) {
    File gradleSettingsFilePath = getGradleSettingsFilePath(rootDir);
    return VfsUtil.findFileByIoFile(gradleSettingsFilePath, true);
  }

  @NotNull
  public static File getGradleSettingsFilePath(@NotNull File rootDir) {
    return new File(rootDir, SdkConstants.FN_SETTINGS_GRADLE);
  }

  @NotNull
  public static File getGradleWrapperPropertiesFilePath(@NotNull File projectRootDir) {
    return new File(projectRootDir, GRADLEW_PROPERTIES_PATH);
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file.
   *
   * @param gradleVersion  the Gradle version to update the property to.
   * @param propertiesFile the given Gradle wrapper properties file.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   * @throws IOException if something goes wrong when saving the file.
   */
  public static boolean updateGradleDistributionUrl(@NotNull String gradleVersion, @NotNull File propertiesFile) throws IOException {
    Properties properties = PropertiesUtil.getProperties(propertiesFile);
    String gradleDistributionUrl = getGradleDistributionUrl(gradleVersion, false);
    String property = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (property != null && (property.equals(gradleDistributionUrl) || property.equals(getGradleDistributionUrl(gradleVersion, true)))) {
      return false;
    }
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, gradleDistributionUrl);
    PropertiesUtil.savePropertiesToFile(properties, propertiesFile, null);
    return true;
  }

  @Nullable
  public static String getGradleWrapperVersion(@NotNull File propertiesFile) throws IOException {
    Properties properties = PropertiesUtil.getProperties(propertiesFile);
    String url = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (url == null) {
      return null;
    }
    Matcher m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url);
    if (m.matches()) {
      return m.group(1);
    }
    return null;
  }

  @NotNull
  private static String getGradleDistributionUrl(@NotNull String gradleVersion, boolean binOnly) {
    String suffix = binOnly ? "bin" : "all";
    return String.format("http://services.gradle.org/distributions/gradle-%1$s-" + suffix + ".zip", gradleVersion);
  }

  @Nullable
  public static GradleExecutionSettings getGradleExecutionSettings(@NotNull Project project) {
    GradleProjectSettings projectSettings = getGradleProjectSettings(project);
    if (projectSettings == null) {
      String format = "Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'";
      String msg = String.format(format, project.getName(), FileUtil.toSystemDependentName(project.getBasePath()));
      LOG.info(msg);
      return null;
    }
    try {
      GradleExecutionSettings settings =
        ExternalSystemApiUtil.getExecutionSettings(project, projectSettings.getExternalProjectPath(), SYSTEM_ID);
      if (settings != null) {
        // By setting the Gradle daemon timeout to -1, we don't allow IDEA to set it to 1 minute. Gradle daemons need to be reused as
        // much as possible. The default timeout is 3 hours.
        settings.setRemoteProcessIdleTtlInMs(GRADLE_DAEMON_TIMEOUT_MS);
      }
      return settings;
    }
    catch (IllegalArgumentException e) {
      LOG.info("Failed to obtain Gradle execution settings", e);
      return null;
    }
  }

  @Nullable
  public static File findWrapperPropertiesFile(@NotNull Project project) {
    File baseDir = new File(project.getBasePath());
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(baseDir);
    return wrapperPropertiesFile.isFile() ? wrapperPropertiesFile : null;
  }

  @Nullable
  public static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    GradleSettings settings = (GradleSettings)ExternalSystemApiUtil.getSettings(project, SYSTEM_ID);

    GradleSettings.MyState state = settings.getState();
    assert state != null;
    Set<GradleProjectSettings> allProjectsSettings = state.getLinkedExternalProjectsSettings();

    return getFirstNotNull(allProjectsSettings);
  }

  @Nullable
  private static GradleProjectSettings getFirstNotNull(@Nullable Set<GradleProjectSettings> allProjectSettings) {
    if (allProjectSettings != null) {
      for (GradleProjectSettings settings : allProjectSettings) {
        if (settings != null) {
          return settings;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getGradleInvocationJvmArgs(@NotNull File projectDir, @Nullable BuildMode buildMode) {
    if (ExternalSystemApiUtil.isInProcessMode(SYSTEM_ID)) {
      List<String> args = Lists.newArrayList();
      if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(projectDir)) {
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData != null) {
          String arg = AndroidGradleSettings.createAndroidHomeJvmArg(sdkData.getLocation().getPath());
          args.add(arg);
        }
      }
      List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
      for (KeyValue<String, String> proxyProperty : proxyProperties) {
        String arg = AndroidGradleSettings.createJvmArg(proxyProperty.getKey(), proxyProperty.getValue());
        args.add(arg);
      }
      String arg = getGradleInvocationJvmArg(buildMode);
      if (arg != null) {
        args.add(arg);
      }
      return args;
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  @Nullable
  static String getGradleInvocationJvmArg(@Nullable BuildMode buildMode) {
    if (BuildMode.ASSEMBLE_TRANSLATE == buildMode) {
      return AndroidGradleSettings.createJvmArg(GradleBuilds.ENABLE_TRANSLATION_JVM_ARG, true);
    }
    return null;
  }

  public static void stopAllGradleDaemons(boolean interactive) throws IOException {
    File gradleHome = findAnyGradleHome(interactive);
    if (gradleHome == null) {
      throw new FileNotFoundException("Unable to find path to Gradle home directory");
    }
    File gradleExecutable = new File(gradleHome, "bin" + File.separatorChar + GRADLE_EXECUTABLE_NAME);
    if (!gradleExecutable.isFile()) {
      throw new FileNotFoundException("Unable to find Gradle executable: " + gradleExecutable.getPath());
    }
    new ProcessBuilder(gradleExecutable.getPath(), "--stop").start();
  }

  @Nullable
  public static File findAnyGradleHome(boolean interactive) {
    // Try cheapest option first:
    String lastUsedGradleHome = getLastUsedGradleHome();
    if (!lastUsedGradleHome.isEmpty()) {
      File path = new File(lastUsedGradleHome);
      if (isValidGradleHome(path)) {
        return path;
      }
    }

    ProjectManager projectManager = ProjectManager.getInstance();
    for (Project project : projectManager.getOpenProjects()) {
      File gradleHome = findGradleHome(project);
      if (gradleHome != null) {
        return gradleHome;
      }
    }

    if (interactive) {
      ChooseGradleHomeDialog chooseGradleHomeDialog = new ChooseGradleHomeDialog();
      chooseGradleHomeDialog.setTitle("Choose Gradle Installation");
      String description = "A Gradle installation is necessary to stop all daemons.\n" +
                           "Please select the home directory of a Gradle installation, otherwise the project won't be closed.";
      chooseGradleHomeDialog.setDescription(description);
      if (!chooseGradleHomeDialog.showAndGet()) {
        return null;
      }
      String enteredPath = chooseGradleHomeDialog.getEnteredGradleHomePath();
      File gradleHomePath = new File(enteredPath);
      if (isValidGradleHome(gradleHomePath)) {
        chooseGradleHomeDialog.storeLastUsedGradleHome();
        return gradleHomePath;
      }
    }

    return null;
  }

  @Nullable
  private static File findGradleHome(@NotNull Project project) {
    GradleExecutionSettings settings = getGradleExecutionSettings(project);
    if (settings != null) {
      String gradleHome = settings.getGradleHome();
      if (!Strings.isNullOrEmpty(gradleHome)) {
        File path = new File(gradleHome);
        if (isValidGradleHome(path)) {
          return path;
        }
      }
    }

    File wrapperPropertiesFile = findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile != null) {
      WrapperExecutor wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(wrapperPropertiesFile, new StringBuilder());
      WrapperConfiguration configuration = wrapperExecutor.getConfiguration();
      File gradleHome = getGradleHome(project, configuration);
      if (gradleHome != null) {
        return gradleHome;
      }
    }

    return null;
  }

  @Nullable
  private static File getGradleHome(@NotNull Project project, @NotNull WrapperConfiguration configuration) {
    File systemHomePath = StartParameter.DEFAULT_GRADLE_USER_HOME;
    if ("PROJECT".equals(configuration.getDistributionBase())) {
      systemHomePath = new File(project.getBasePath(), SdkConstants.DOT_GRADLE);
    }
    if (!systemHomePath.isDirectory()) {
      return null;
    }
    PathAssembler.LocalDistribution localDistribution = new PathAssembler(systemHomePath).getDistribution(configuration);
    File distributionPath = localDistribution.getDistributionDir();
    if (distributionPath != null) {
      File[] children = FileUtil.notNullize(distributionPath.listFiles());
      for (File child : children) {
        if (child.isDirectory() && child.getName().startsWith("gradle-") && isValidGradleHome(child)) {
          return child;
        }
      }
    }
    return null;
  }

  private static boolean isValidGradleHome(@NotNull File path) {
    return path.isDirectory() && ServiceManager.getService(GradleInstallationManager.class).isGradleSdkHome(path);
  }

  /**
   * Convert a Gradle project name into a system dependent path relative to root project. Please note this is the default mapping from a
   * Gradle "logical" path to a physical path. Users can override this mapping in settings.gradle and this mapping may not always be
   * accurate.
   * <p/>
   * E.g. ":module" becomes "module" and ":directory:module" is converted to "directory/module"
   */
  @NotNull
  public static String getDefaultPhysicalPathFromGradlePath(@NotNull String name) {
    List<String> segments = getPathSegments(name);
    return FileUtil.join(segments.toArray(new String[segments.size()]));
  }

  /**
   * Obtain default path for the Gradle subproject with the given name in the project.
   */
  @NotNull
  public static File getDefaultSubprojectLocation(@NotNull VirtualFile project, @NotNull String gradlePath) {
    assert gradlePath.length() > 0;
    String relativePath = getDefaultPhysicalPathFromGradlePath(gradlePath);
    return new File(VfsUtilCore.virtualToIoFile(project), relativePath);
  }

  /**
   * Prefixes string with colon if there isn't one already there.
   */
  @Nullable
  @Contract("null -> null;!null -> !null")
  public static String makeAbsolute(String string) {
    if (string == null) {
      return null;
    }
    else if (string.trim().length() == 0) {
      return ":";
    }
    else if (!string.startsWith(":")) {
      return ":" + string.trim();
    }
    else {
      return string.trim();
    }
  }

  /**
   * Tests if the Gradle path is valid and return index of the offending
   * character or -1 if none.
   * <p/>
   */
  public static int isValidGradlePath(@NotNull String gradlePath) {
    return ILLEGAL_GRADLE_PATH_CHARS_MATCHER.indexIn(gradlePath);
  }

  /**
   * Checks if the project already has a module with given Gradle path.
   */
  public static boolean hasModule(@Nullable Project project, @NotNull String gradlePath, boolean checkProjectFolder) {
    if (project == null) {
      return false;
    }
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (gradlePath.equals(getGradlePath(module))) {
        return true;
      }
    }
    if (checkProjectFolder) {
      File location = getDefaultSubprojectLocation(project.getBaseDir(), gradlePath);
      if (location.isFile()) {
        return true;
      }
      else if (location.isDirectory()) {
        File[] children = location.listFiles();
        return children == null || children.length > 0;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
  }

  public static void cleanUpPreferences(@NotNull ExtensionPoint<ConfigurableEP<Configurable>> preferences,
                                        @NotNull List<String> bundlesToRemove) {
    List<ConfigurableEP<Configurable>> nonStudioExtensions = Lists.newArrayList();

    ConfigurableEP<Configurable>[] extensions = preferences.getExtensions();
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (bundlesToRemove.contains(extension.instanceClass)) {
        nonStudioExtensions.add(extension);
      }
    }

    for (ConfigurableEP<Configurable> toRemove : nonStudioExtensions) {
      preferences.unregisterExtension(toRemove);
    }
  }

  /**
   * Attempts to figure out the Gradle version of the given distribution.
   *
   * @param gradleHomePath the path of the directory containing the Gradle distribution.
   * @return the Gradle version of the given distribution, or {@code null} if it was not possible to obtain the version.
   */
  @Nullable
  public static FullRevision getGradleVersion(@NotNull File gradleHomePath) {
    File libDirPath = new File(gradleHomePath, "lib");

    for (File child : FileUtil.notNullize(libDirPath.listFiles())) {
      FullRevision version = getGradleVersionFromJar(child);
      if (version != null) {
        return version;
      }
    }

    return null;
  }

  @VisibleForTesting
  @Nullable
  static FullRevision getGradleVersionFromJar(@NotNull File libraryJarFile) {
    String fileName = libraryJarFile.getName();
    Matcher matcher = GRADLE_JAR_NAME_PATTERN.matcher(fileName);
    if (matcher.matches()) {
      // Obtain the version of Gradle from a library name (e.g. "gradle-core-2.0.jar")
      String version = matcher.group(2);
      try {
        return FullRevision.parseRevision(version);
      }
      catch (NumberFormatException e) {
        LOG.warn(String.format("Unable to parse version '%1$s' (obtained from file '%2$s')", version, fileName));
      }
    }
    return null;
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectDirPath the project's root directory.
   * @param gradleVersion the version of Gradle to use. If not specified, this method will use the latest supported version of Gradle.
   * @return {@code true} if the project already has the wrapper or the wrapper was successfully created; {@code false} if the wrapper was
   * not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   *
   * @see com.android.SdkConstants#GRADLE_LATEST_VERSION
   */
  public static boolean createGradleWrapper(@NotNull File projectDirPath, @Nullable String gradleVersion) throws IOException {
    File projectWrapperDirPath = new File(projectDirPath, FD_GRADLE_WRAPPER);
    if (projectWrapperDirPath.isDirectory()) {
      // already exists. Nothing to do.
      return true;
    }
    File wrapperSrcDirPath = new File(TemplateManager.getTemplateRootFolder(), FD_GRADLE_WRAPPER);
    if (!wrapperSrcDirPath.exists()) {
      for (File root : TemplateManager.getExtraTemplateRootFolders()) {
        wrapperSrcDirPath = new File(root, FD_GRADLE_WRAPPER);
        if (wrapperSrcDirPath.exists()) {
          break;
        }
        else {
          wrapperSrcDirPath = null;
        }
      }
    }
    if (wrapperSrcDirPath == null) {
      return false;
    }
    FileUtil.copyDirContent(wrapperSrcDirPath, projectDirPath);
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(projectDirPath);
    String version = gradleVersion != null ? gradleVersion : GRADLE_LATEST_VERSION;
    updateGradleDistributionUrl(version, wrapperPropertiesFile);
    return true;
  }

  @Nullable
  public static String getAndroidGradleModelVersion(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // This is default project.
      return null;
    }
    final AtomicReference<String> modelVersionRef = new AtomicReference<String>();
    VfsUtil.processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (SdkConstants.FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          File fileToCheck = VfsUtilCore.virtualToIoFile(virtualFile);
          try {
            String contents = Files.toString(fileToCheck, Charsets.UTF_8);
            Matcher matcher = ANDROID_GRADLE_PLUGIN_DEPENDENCY_PATTERN.matcher(contents);
            if (matcher.find()) {
              String modelVersion = matcher.group(1);
              if (!StringUtil.isEmpty(modelVersion)) {
                modelVersionRef.set(modelVersion);
                return false; // we found the model version. Stop.
              }
            }
          }
          catch (IOException e) {
            LOG.warn("Failed to read contents of " + fileToCheck.getPath());
          }
        }
        return true;
      }
    });

    return modelVersionRef.get();
  }
}
