/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Builds a project, regardless of the compiler strategy being used (JPS or "direct Gradle invocation.")
 */
public class ProjectBuilder {
  @NotNull private final Project myProject;

  @NotNull
  public static ProjectBuilder getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectBuilder.class);
  }

  public ProjectBuilder(@NotNull Project project) {
    myProject = project;
  }

  public void assembleTranslate() {
    if (Projects.isGradleProject(myProject)) {
      if (Projects.isDirectGradleInvocationEnabled(myProject)) {
        GradleInvoker.getInstance(myProject).assembleTranslate();
        return;
      }
      buildProjectWithJps(BuildMode.ASSEMBLE_TRANSLATE);
    }
  }

  public void compileJava() {
    if (Projects.isGradleProject(myProject)) {
      if (Projects.isDirectGradleInvocationEnabled(myProject)) {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        GradleInvoker.getInstance(myProject).compileJava(modules);
        return;
      }
      buildProjectWithJps(BuildMode.COMPILE_JAVA);
    }
  }

  public void clean() {
    if (Projects.isGradleProject(myProject)) {
      if (Projects.isDirectGradleInvocationEnabled(myProject)) {
        GradleInvoker.getInstance(myProject).cleanProject();
        return;
      }
      buildProjectWithJps(BuildMode.CLEAN);
    }
  }

  /**
   * Generates source code instead of a full compilation. This method does nothing if the Gradle model does not specify the name of the
   * Gradle task to invoke.
   */
  public void generateSourcesOnly() {
    if (Projects.isGradleProject(myProject)) {
      if (Projects.isDirectGradleInvocationEnabled(myProject)) {
        GradleInvoker.getInstance(myProject).generateSources();
        return;
      }
      buildProjectWithJps(BuildMode.SOURCE_GEN);
    }
  }

  private void buildProjectWithJps(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
    CompilerManager.getInstance(myProject).make(null);
  }

  public void addAfterProjectBuildTask(@NotNull AfterProjectBuildTask task) {
    CompilerManager.getInstance(myProject).addAfterTask(task);
    GradleInvoker.getInstance(myProject).addAfterGradleInvocationTask(task);
  }

  /**
   * Convenient implementation of {@link AfterProjectBuildTask} meant for listeners that do not care of the build result.
   */
  public abstract static class AfterProjectBuildListener implements AfterProjectBuildTask {
    @Override
    public void execute(@NotNull GradleInvocationResult result) {
      buildFinished();
    }

    @Override
    public boolean execute(CompileContext context) {
      buildFinished();
      return true;
    }

    protected abstract void buildFinished();
  }

  public interface AfterProjectBuildTask extends CompileTask, GradleInvoker.AfterGradleInvocationTask {
  }
}
