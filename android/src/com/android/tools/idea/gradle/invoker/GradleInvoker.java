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
package com.android.tools.idea.gradle.invoker;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleView;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
public class GradleInvoker {
  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);

  @NotNull private final Project myProject;

  @NotNull private Collection<BeforeGradleInvocationTask> myBeforeTasks = Sets.newLinkedHashSet();
  @NotNull private Collection<AfterGradleInvocationTask> myAfterTasks = Sets.newLinkedHashSet();

  public static GradleInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleInvoker.class);
  }

  public GradleInvoker(@NotNull Project project) {
    myProject = project;
  }

  @VisibleForTesting
  void addBeforeGradleInvocationTask(@NotNull BeforeGradleInvocationTask task) {
    myBeforeTasks.add(task);
  }

  public void addAfterGradleInvocationTask(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.add(task);
  }

  public void removeAfterGradleInvocationTask(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.remove(task);
  }

  public void cleanProject() {
    setProjectBuildMode(BuildMode.CLEAN);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    // "Clean" also generates sources.
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), BuildMode.SOURCE_GEN, TestCompileType.NONE);
    tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    executeTasks(tasks);
  }

  public void assembleTranslate() {
    setProjectBuildMode(BuildMode.ASSEMBLE_TRANSLATE);
    executeTasks(Lists.newArrayList(GradleBuilds.ASSEMBLE_TRANSLATE_TASK_NAME));
  }

  public void generateSources() {
    BuildMode buildMode = BuildMode.SOURCE_GEN;
    setProjectBuildMode(buildMode);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);

    executeTasks(tasks);
  }

  public void compileJava(@NotNull Module[] modules) {
    BuildMode buildMode = BuildMode.COMPILE_JAVA;
    setProjectBuildMode(buildMode);
    List<String> tasks = findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    executeTasks(tasks);
  }

  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    BuildMode buildMode = BuildMode.ASSEMBLE;
    setProjectBuildMode(buildMode);
    List<String> tasks = findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks);
  }

  public void rebuild() {
    BuildMode buildMode = BuildMode.REBUILD;
    setProjectBuildMode(buildMode);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    executeTasks(tasks);
  }

  private void setProjectBuildMode(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
  }

  @NotNull
  public static List<String> findTasksToExecute(@NotNull Module[] modules,
                                                @NotNull BuildMode buildMode,
                                                @NotNull TestCompileType testCompileType) {
    List<String> tasks = Lists.newArrayList();

    if (BuildMode.ASSEMBLE == buildMode) {
      Project project = modules[0].getProject();
      if (Projects.lastGradleSyncFailed(project)) {
        // If last Gradle sync failed, just call "assemble" at the top-level. Without a model there are no other tasks we can call.
        return Collections.singletonList(GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME);
      }
    }

    for (Module module : modules) {
      if (GradleBuilds.BUILD_SRC_FOLDER_NAME.equals(module.getName())) {
        // "buildSrc" is a special case handled automatically by Gradle.
        continue;
      }
      findAndAddGradleBuildTasks(module, buildMode, tasks, testCompileType);
    }
    if (buildMode == BuildMode.REBUILD && !tasks.isEmpty()) {
      tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      LOG.info(String.format(format, modules[0].getProject().getName(), buildMode.name()));
    }
    return tasks;
  }

  public void executeTasks(@NotNull final List<String> gradleTasks) {
    executeTasks(gradleTasks, Collections.<String>emptyList());
  }

  public void executeTasks(@NotNull final List<String> gradleTasks, @NotNull final List<String> commandLineArguments) {
    LOG.info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (BeforeGradleInvocationTask listener : myBeforeTasks) {
        listener.execute(gradleTasks);
      }
      return;
    }

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        AfterGradleInvocationTask[] afterGradleInvocationTasks = myAfterTasks.toArray(new AfterGradleInvocationTask[myAfterTasks.size()]);
        GradleTasksExecutor executor = new GradleTasksExecutor(myProject, gradleTasks, commandLineArguments, afterGradleInvocationTasks);
        executor.queue();
      }
    });
  }

  public void clearConsoleAndBuildMessages() {
    GradleConsoleView.getInstance(myProject).clear();
    GradleTasksExecutor.clearMessageView(myProject);
  }

  private static void findAndAddGradleBuildTasks(@NotNull Module module,
                                                 @NotNull BuildMode buildMode,
                                                 @NotNull List<String> tasks,
                                                 @NotNull TestCompileType testCompileType) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return;
    }
    String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (StringUtil.isEmpty(gradlePath)) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String msg = String.format("Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.",
                                 module.getName());
      LOG.info(msg);
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();
      switch (buildMode) {
        case SOURCE_GEN:
          tasks.add(createBuildTask(gradlePath, properties.SOURCE_GEN_TASK_NAME));
          if (StringUtil.isNotEmpty(properties.TEST_SOURCE_GEN_TASK_NAME)) {
            tasks.add(createBuildTask(gradlePath, properties.TEST_SOURCE_GEN_TASK_NAME));
          }
          break;
        case ASSEMBLE:
          tasks.add(createBuildTask(gradlePath, properties.ASSEMBLE_TASK_NAME));
          break;
        default:
          tasks.add(createBuildTask(gradlePath, properties.COMPILE_JAVA_TASK_NAME));
      }

      if (testCompileType == TestCompileType.ANDROID_TESTS) {
        String gradleTaskName = properties.ASSEMBLE_TEST_TASK_NAME;
        if (StringUtil.isNotEmpty(gradleTaskName)) {
          tasks.add(createBuildTask(gradlePath, gradleTaskName));
        }
      }
    }
    else {
      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null) {
        String gradleTaskName = javaFacet.getGradleTaskName(buildMode);
        if (gradleTaskName != null) {
          tasks.add(createBuildTask(gradlePath, gradleTaskName));
        }
        if (testCompileType == TestCompileType.JAVA_TESTS) {
          tasks.add(createBuildTask(gradlePath, JavaGradleFacet.TEST_CLASSES_TASK_NAME));
        }
      }
    }
  }

  @NotNull
  public static String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    if (gradleProjectPath.equals(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      // Prevent double colon when dealing with root module (e.g. "::assemble");
      return gradleProjectPath + taskName;
    }
    return gradleProjectPath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }

  @NotNull
  public static TestCompileType getTestCompileType(@Nullable String runConfigurationId) {
    if (runConfigurationId != null) {
      if (AndroidCommonUtils.isInstrumentationTestConfiguration(runConfigurationId)) {
        return TestCompileType.ANDROID_TESTS;
      }
      if (AndroidCommonUtils.isTestConfiguration(runConfigurationId)) {
        return TestCompileType.JAVA_TESTS;
      }
    }
    return TestCompileType.NONE;
  }


  public enum TestCompileType {
    NONE,            // don't compile any tests
    ANDROID_TESTS,   // compile tests that are part of an Android Module
    JAVA_TESTS       // compile tests that are part of a Java module
  }

  @VisibleForTesting
  interface BeforeGradleInvocationTask {
    void execute(@NotNull List<String> tasks);
  }

  public interface AfterGradleInvocationTask {
    void execute(@NotNull GradleInvocationResult result);
  }
}
