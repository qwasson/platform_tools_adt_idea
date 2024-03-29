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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.ContentRootSourcePaths;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link ContentRootModuleCustomizer}.
 */
public class ContentRootModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private IdeaAndroidProject myIdeaAndroidProject;

  private ContentRootModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    File baseDir = new File(FileUtil.toSystemDependentName(myProject.getBasePath()));
    myAndroidProject = TestProjects.createBasicProject(baseDir, myProject.getName());

    Collection<Variant> variants = myAndroidProject.getVariants();
    Variant selectedVariant = ContainerUtil.getFirstItem(variants);
    assertNotNull(selectedVariant);
    myIdeaAndroidProject = new IdeaAndroidProject(myAndroidProject.getName(), baseDir, myAndroidProject, selectedVariant.getName());

    addContentEntry();
    myCustomizer = new ContentRootModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  private void addContentEntry() {
    VirtualFile moduleFile = myModule.getModuleFile();
    assertNotNull(moduleFile);
    final VirtualFile moduleDir = moduleFile.getParent();

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
        ModifiableRootModel model = moduleRootManager.getModifiableModel();
        model.addContentEntry(moduleDir);
        model.commit();
      }
    });
  }

  public void testCustomizeModule() throws Exception {
    myCustomizer.customizeModule(myModule, myProject, myIdeaAndroidProject);

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ContentEntry contentEntry = moduleRootManager.getContentEntries()[0];

    SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
    List<String> sourcePaths = Lists.newArrayListWithExpectedSize(sourceFolders.length);

    for (SourceFolder folder : sourceFolders) {
      if (!folder.isTestSource()) {
        VirtualFile file = folder.getFile();
        final String path = VfsUtilCore.urlToPath(folder.getUrl());
        System.out.println("path: " + path + "; " + new File(path).exists());
        assertNotNull(file);
        sourcePaths.add(file.getPath());
      }
    }

    ContentRootSourcePaths expectedPaths = new ContentRootSourcePaths();
    expectedPaths.storeExpectedSourcePaths(myAndroidProject);


    List<String> allExpectedPaths = Lists.newArrayList();
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.SOURCE));
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.SOURCE_GENERATED));
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.RESOURCE));
    Collections.sort(allExpectedPaths);

    Collections.sort(sourcePaths);

    assertEquals(allExpectedPaths, sourcePaths);
  }
}
