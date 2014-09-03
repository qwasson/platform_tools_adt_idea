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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.FilePaths;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getLastUsedGradleHome;
import static org.jetbrains.plugins.gradle.util.GradleUtil.isGradleDefaultWrapperFilesExist;

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
public class GradleProjectImporter {
  private static final Logger LOG = Logger.getInstance(GradleProjectImporter.class);
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  private final ImporterDelegate myDelegate;

  /**
   * Flag used by unit tests to selectively disable code which requires an open project or UI updates; this is used
   * by unit tests that do not run all of IntelliJ (e.g. do not extend the IdeaTestCase base)
   */
  public static boolean ourSkipSetupFromTest;

  @NotNull
  public static GradleProjectImporter getInstance() {
    return ServiceManager.getService(GradleProjectImporter.class);
  }

  public GradleProjectImporter() {
    myDelegate = new ImporterDelegate();
  }

  @VisibleForTesting
  GradleProjectImporter(ImporterDelegate delegate) {
    myDelegate = delegate;
  }

  /**
   * Imports the given Gradle project.
   *
   * @param selectedFile the selected build.gradle or the project's root directory.
   */
  public void importProject(@NotNull VirtualFile selectedFile) {
    VirtualFile projectDir = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
    File projectDirPath = new File(FileUtil.toSystemDependentName(projectDir.getPath()));

    // Sync Android SDKs paths *before* importing project. Studio will freeze if the project has a local.properties file pointing to a SDK
    // path that does not exist. The cause is that having 2 dialogs: one modal (the "Project Import" one) and another from
    // Messages.showErrorDialog (indicating the Android SDK path does not exist) produce a deadlock.
    try {
      LocalProperties localProperties = new LocalProperties(projectDirPath);
      if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
        SdkSync.syncIdeAndProjectAndroidHomes(localProperties);
      }
    }
    catch (IOException e) {
      LOG.info("Failed to sync SDKs", e);
      Messages.showErrorDialog(e.getMessage(), "Project Import");
      return;
    }

    // Set up Gradle settings. Otherwise we get an "already disposed project" error.
    new GradleSettings(ProjectManager.getInstance().getDefaultProject());

    // If we have Gradle wrapper go ahead and import the project, without showing the "Project Import" wizard.
    boolean hasGradleWrapper = isGradleDefaultWrapperFilesExist(projectDirPath.getPath());

    if (!hasGradleWrapper) {
      // If we don't have a Gradle wrapper, but we have the location of GRADLE_HOME, we import the project without showing the "Project
      // Import" wizard.
      boolean validGradleHome = false;
      String gradleHome = getLastUsedGradleHome();
      if (!gradleHome.isEmpty()) {
        GradleInstallationManager gradleInstallationManager = ServiceManager.getService(GradleInstallationManager.class);
        File gradleHomePath = new File(FileUtil.toSystemDependentName(gradleHome));
        validGradleHome = gradleInstallationManager.isGradleSdkHome(gradleHomePath);
      }
      if (!validGradleHome) {
        ChooseGradleHomeDialog chooseGradleHomeDialog = new ChooseGradleHomeDialog();
        if (!chooseGradleHomeDialog.showAndGet()) {
          return;
        }
        chooseGradleHomeDialog.storeLastUsedGradleHome();
      }
    }
    createProjectFileForGradleProject(selectedFile, null);
  }


  /**
   * Creates IntelliJ project file in the root of the project directory.
   *
   * @param selectedFile  <code>build.gradle</code> in the module folder.
   * @param parentProject existing parent project or <code>null</code> if a new one should be created.
   */
  private void createProjectFileForGradleProject(@NotNull VirtualFile selectedFile, @Nullable Project parentProject) {
    VirtualFile projectDir = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
    File projectDirPath = VfsUtilCore.virtualToIoFile(projectDir);
    try {
      importProject(projectDir.getName(), projectDirPath, true, new NewProjectImportGradleSyncListener() {
        @Override
        public void syncSucceeded(@NotNull Project project) {
          activateProjectView(project);
        }
      }, parentProject, null);
    }
    catch (Exception e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
      }
      Messages.showErrorDialog(e.getMessage(), "Project Import");
      LOG.error(e);
    }
  }

  /**
   * Requests a project sync with Gradle. If the project import is successful,
   * {@link com.android.tools.idea.gradle.util.ProjectBuilder#generateSourcesOnly()} will be invoked at the end.
   *
   * @param project  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param listener called after the project has been imported.
   */
  public void requestProjectSync(@NotNull final Project project, @Nullable GradleSyncListener listener) {
    requestProjectSync(project, true, listener);
  }

  /**
   * Requests a project sync with Gradle.
   *
   * @param project                  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param generateSourcesOnSuccess indicates whether the IDE should invoke Gradle to generate Java sources after a successful project
   *                                 import.
   * @param listener                 called after the project has been imported.
   */
  public void requestProjectSync(@NotNull final Project project,
                                 final boolean generateSourcesOnSuccess,
                                 @Nullable final GradleSyncListener listener) {
    Runnable syncRequest = new Runnable() {
      @Override
      public void run() {
        try {
          doRequestSync(project, ProgressExecutionMode.IN_BACKGROUND_ASYNC, generateSourcesOnSuccess, listener);
        }
        catch (ConfigurationException e) {
          Messages.showErrorDialog(project, e.getMessage(), e.getTitle());
        }
      }
    };
    AppUIUtil.invokeLaterIfProjectAlive(project, syncRequest);
  }

  public void syncProjectSynchronously(@NotNull final Project project,
                                       final boolean generateSourcesOnSuccess,
                                       @Nullable final GradleSyncListener listener) {
    Runnable syncRequest = new Runnable() {
      @Override
      public void run() {
        try {
          doRequestSync(project, MODAL_SYNC, generateSourcesOnSuccess, listener);
        }
        catch (ConfigurationException e) {
          Messages.showErrorDialog(project, e.getMessage(), e.getTitle());
        }
      }
    };
    UIUtil.invokeAndWaitIfNeeded(syncRequest);
  }

  private void doRequestSync(@NotNull final Project project,
                             @NotNull ProgressExecutionMode progressExecutionMode,
                             boolean generateSourcesOnSuccess,
                             @Nullable final GradleSyncListener listener) throws ConfigurationException {
    if (Projects.isGradleProject(project) || hasTopLevelGradleBuildFile(project)) {
      FileDocumentManager.getInstance().saveAllDocuments();
      setUpGradleSettings(project);
      resetProject(project);
      doImport(project, false /* existing project */, progressExecutionMode, generateSourcesOnSuccess, listener);
    }
    else {
      Runnable notificationTask = new Runnable() {
        @Override
        public void run() {
          String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
          AndroidGradleNotification.getInstance(project).showBalloon("Project Sync", msg, ERROR, new OpenMigrationToGradleUrlHyperlink());

          if (listener != null) {
            listener.syncFailed(project, msg);
          }
        }
      };
      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        notificationTask.run();
      }
      else {
        application.invokeLater(notificationTask);
      }
    }
  }

  private static boolean hasTopLevelGradleBuildFile(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile gradleBuildFile = baseDir.findChild(SdkConstants.FN_BUILD_GRADLE);
    return gradleBuildFile != null && gradleBuildFile.exists() && !gradleBuildFile.isDirectory();
  }

  // See issue: https://code.google.com/p/android/issues/detail?id=64508
  private static void resetProject(@NotNull final Project project) {
    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
        LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
        try {
          for (Library library : model.getLibraries()) {
            model.removeLibrary(library);
          }
        }
        finally {
          model.commit();
        }

        // Remove all AndroidProjects from module. Otherwise, if re-import/sync fails, editors will not show the proper notification of
        // the failure.
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null) {
            facet.setIdeaAndroidProject(null);
          }
        }
      }
    });
  }

  /**
   * Imports and opens an Android project that has been created with the "New Project" wizard. This method does not perform any project
   * validation before importing the project (assuming that the wizard properly created the new project.)
   *
   * @param projectName          name of the project.
   * @param projectRootDir       root directory of the project.
   * @param listener             called after the project has been imported.
   * @param project              the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param initialLanguageLevel when creating a new project, sets the language level to the given version early on (this is because you
   *                             cannot set a language level later on in the process without telling the user that the language level
   *                             has changed and to re-open the project)
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void importNewlyCreatedProject(@NotNull String projectName,
                                        @NotNull File projectRootDir,
                                        @Nullable GradleSyncListener listener,
                                        @Nullable Project project,
                                        @Nullable LanguageLevel initialLanguageLevel) throws IOException, ConfigurationException {
    doImport(projectName, projectRootDir, true, listener, project, initialLanguageLevel);
  }

  /**
   * Imports and opens an Android project.
   *
   * @param projectName              name of the project.
   * @param projectRootDir           root directory of the project.
   * @param generateSourcesOnSuccess whether to generate sources after sync.
   * @param listener                 called after the project has been imported.
   * @param project                  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param initialLanguageLevel     when creating a new project, sets the language level to the given version early on (this is because you
   *                                 cannot set a language level later on in the process without telling the user that the language level
   *                                 has changed and to re-open the project)
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void importProject(@NotNull String projectName,
                            @NotNull File projectRootDir,
                            boolean generateSourcesOnSuccess,
                            @Nullable GradleSyncListener listener,
                            @Nullable Project project,
                            @Nullable LanguageLevel initialLanguageLevel) throws IOException, ConfigurationException {
    doImport(projectName, projectRootDir, generateSourcesOnSuccess, listener, project, initialLanguageLevel);
  }

  private void doImport(@NotNull String projectName,
                        @NotNull File projectRootDir,
                        boolean generateSourcesOnSuccess,
                        @Nullable GradleSyncListener listener,
                        @Nullable Project project,
                        @Nullable LanguageLevel initialLanguageLevel) throws IOException, ConfigurationException {
    createTopLevelBuildFileIfNotExisting(projectRootDir);
    createIdeaProjectDir(projectRootDir);

    Project newProject = project == null ? createProject(projectName, projectRootDir.getPath()) : project;
    setUpProject(newProject, initialLanguageLevel);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    doImport(newProject, true /* new project */, MODAL_SYNC /* synchronous import */, generateSourcesOnSuccess, listener);
  }

  private static void createTopLevelBuildFileIfNotExisting(@NotNull File projectRootDir) throws IOException {
    File projectFile = GradleUtil.getGradleBuildFilePath(projectRootDir);
    if (projectFile.isFile()) {
      return;
    }
    FileUtilRt.createIfNotExists(projectFile);
    String contents = "// Top-level build file where you can add configuration options common to all sub-projects/modules." +
                      SystemProperties.getLineSeparator();
    FileUtil.writeToFile(projectFile, contents);
  }

  private static void createIdeaProjectDir(@NotNull File projectRootDir) throws IOException {
    File ideaDir = new File(projectRootDir, Project.DIRECTORY_STORE_FOLDER);
    if (ideaDir.isDirectory()) {
      // "libraries" is hard-coded in com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
      File librariesDir = new File(ideaDir, "libraries");
      if (librariesDir.exists()) {
        // remove contents of libraries. This is useful when importing existing projects that may have invalid library entries (e.g.
        // created with Studio 0.4.3 or earlier.)
        boolean librariesDirDeleted = FileUtil.delete(librariesDir);
        if (!librariesDirDeleted) {
          LOG.info(String.format("Failed to delete %1$s'", librariesDir.getPath()));
        }
      }
    }
    else {
      FileUtil.ensureExists(ideaDir);
    }
  }

  @NotNull
  private static Project createProject(@NotNull String projectName, @NotNull String projectPath) throws ConfigurationException {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project newProject = projectManager.createProject(projectName, projectPath);
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new IDEA project");
    }
    return newProject;
  }

  private static void setUpProject(@NotNull final Project newProject, @Nullable final LanguageLevel initialLanguageLevel) {
    CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (initialLanguageLevel != null) {
              final LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(newProject);
              if (extension != null) {
                extension.setLanguageLevel(initialLanguageLevel);
              }
            }

            // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
            // IDEA.
            File compilerOutputDir = new File(newProject.getBasePath(), FileUtil.join(GradleUtil.BUILD_DIR_DEFAULT_NAME, "classes"));
            String compilerOutputDirUrl = FilePaths.pathToIdeaUrl(compilerOutputDir);
            CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(newProject);
            assert compilerProjectExt != null;
            compilerProjectExt.setCompilerOutputUrl(compilerOutputDirUrl);
            setUpGradleSettings(newProject);
          }
        });
      }
    }, null, null);
  }

  private static void setUpGradleSettings(@NotNull Project project) {
    GradleProjectSettings projectSettings = GradleUtil.getGradleProjectSettings(project);
    if (projectSettings == null) {
      projectSettings = new GradleProjectSettings();
    }
    projectSettings.setUseAutoImport(false);
    setUpGradleProjectSettings(project, projectSettings);
    GradleSettings gradleSettings = GradleSettings.getInstance(project);
    gradleSettings.setLinkedProjectsSettings(ImmutableList.of(projectSettings));
  }

  private static void setUpGradleProjectSettings(@NotNull Project project, @NotNull GradleProjectSettings settings) {
    settings.setExternalProjectPath(FileUtil.toCanonicalPath(project.getBasePath()));

    DistributionType distributionType = settings.getDistributionType();

    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile == null) {
      if (DistributionType.LOCAL != distributionType) {
        settings.setDistributionType(DistributionType.LOCAL);
      }
      if (StringUtil.isEmpty(settings.getGradleHome())) {
        settings.setGradleHome(getLastUsedGradleHome());
      }
    }
    else if (distributionType == null) {
      settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    }
  }

  private void doImport(@NotNull final Project project,
                        final boolean newProject,
                        @NotNull final ProgressExecutionMode progressExecutionMode,
                        boolean generateSourcesOnSuccess,
                        @Nullable final GradleSyncListener listener) throws ConfigurationException {
    PreSyncChecks.check(project);

    if (AndroidStudioSpecificInitializer.isAndroidStudio() && Projects.isDirectGradleInvocationEnabled(project)) {
      // We cannot do the same when using JPS. We don't have access to the contents of the Message view used by JPS.
      // For now, we can only improve the user experience in Android Studio.
      GradleInvoker.getInstance(project).clearConsoleAndBuildMessages();
    }

    // Prevent IDEA from syncing with Gradle. We want to have full control of syncing.
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true);

    project.putUserData(Projects.HAS_UNRESOLVED_DEPENDENCIES, false);
    project.putUserData(Projects.HAS_WRONG_JDK, false);

    final Application application = ApplicationManager.getApplication();
    final boolean isTest = application.isUnitTestMode();

    PostProjectSetupTasksExecutor.getInstance(project).setGenerateSourcesAfterSync(generateSourcesOnSuccess);

    // We only update UI on sync when re-importing projects. By "updating UI" we mean updating the "Build Variants" tool window and editor
    // notifications.  It is not safe to do this for new projects because the new project has not been opened yet.
    GradleSyncState.getInstance(project).syncStarted(!newProject);

    myDelegate.importProject(project, new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> projectInfo) {
        assert projectInfo != null;
        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            populateProject(project, projectInfo);
            if (!isTest || !ourSkipSetupFromTest) {
              if (newProject) {
                Projects.open(project);
              }
              if (!isTest) {
                project.save();
              }
            }
            if (newProject) {
              // We need to do this because AndroidGradleProjectComponent#projectOpened is being called when the project is created, instead
              // of when the project is opened. When 'projectOpened' is called, the project is not fully configured, and it does not look
              // like it is Gradle-based, resulting in listeners (e.g. modules added events) not being registered. Here we force the
              // listeners to be registered.
              AndroidGradleProjectComponent projectComponent = ServiceManager.getService(project, AndroidGradleProjectComponent.class);
              projectComponent.configureGradleProject(false);
            }
            if (listener != null) {
              listener.syncSucceeded(project);
            }
          }
        };
        if (application.isUnitTestMode()) {
          runnable.run();
        }
        else {
          application.invokeLater(runnable);
        }
      }

      @Override
      public void onFailure(@NotNull final String errorMessage, @Nullable String errorDetails) {
        if (errorDetails != null) {
          LOG.warn(errorDetails);
        }
        String newMessage = ExternalSystemBundle.message("error.resolve.with.reason", errorMessage);
        LOG.info(newMessage);

        GradleSyncState.getInstance(project).syncFailed(newMessage);

        if (listener != null) {
          listener.syncFailed(project, newMessage);
        }
      }
    }, progressExecutionMode);
  }

  private static void populateProject(@NotNull final Project newProject, @NotNull final DataNode<ProjectData> projectInfo) {
    StartupManager.getInstance(newProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(newProject) {
          @Override
          public void execute() {
            ProjectRootManagerEx.getInstanceEx(newProject).mergeRootsChangesDuring(new Runnable() {
              @Override
              public void run() {
                ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
                Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.findAll(projectInfo, ProjectKeys.MODULE);
                dataManager.importData(ProjectKeys.MODULE, modules, newProject, true /* synchronous */);
              }
            });
          }
        });
      }
    });
  }


  // Makes it possible to mock invocations to the Gradle Tooling API.
  static class ImporterDelegate {
    void importProject(@NotNull Project project,
                       @NotNull ExternalProjectRefreshCallback callback,
                       @NotNull final ProgressExecutionMode progressExecutionMode) throws ConfigurationException {
      try {
        String externalProjectPath = FileUtil.toCanonicalPath(project.getBasePath());
        ExternalSystemUtil
          .refreshProject(project, SYSTEM_ID, externalProjectPath, callback, false /* resolve dependencies */, progressExecutionMode, true /* always report import errors */);
      }
      catch (RuntimeException e) {
        String externalSystemName = SYSTEM_ID.getReadableName();
        throw new ConfigurationException(e.getMessage(), ExternalSystemBundle.message("error.cannot.parse.project", externalSystemName));
      }
    }
  }
}
