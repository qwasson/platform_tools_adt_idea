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
package com.android.tools.idea.gradle.customizer.android;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

/**
 * Tests for {@link CompilerOutputModuleCustomizer}.
 */
public class CompilerOutputModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub androidProject;
  private CompilerOutputModuleCustomizer customizer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    androidProject = TestProjects.createBasicProject();
    customizer = new CompilerOutputModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    if (androidProject != null) {
      androidProject.dispose();
    }
    super.tearDown();
  }

  public void testCustomizeModule() {
    File rootDir = androidProject.getRootDir();
    IdeaAndroidProject ideaAndroidProject = new IdeaAndroidProject(myModule.getName(), rootDir, androidProject, "debug");
    customizer.customizeModule(myModule, myProject, ideaAndroidProject);

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel moduleSettings = moduleRootManager.getModifiableModel();
    CompilerModuleExtension compilerSettings = moduleSettings.getModuleExtension(CompilerModuleExtension.class);
    String compilerOutputPath = compilerSettings.getCompilerOutputUrl();
    moduleSettings.commit();

    File classesFolder = ideaAndroidProject.getSelectedVariant().getMainArtifact().getClassesFolder();
    String path = FileUtil.toSystemIndependentName(classesFolder.getPath());
    String expected = VfsUtilCore.pathToUrl(ExternalSystemApiUtil.toCanonicalPath(path));
    assertEquals(expected, compilerOutputPath);
  }
}
