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

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.google.common.collect.Lists;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sets the content roots of an IDEA module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<IdeaAndroidProject> {
  // TODO: Retrieve this information from Gradle.
  private static final String[] EXCLUDED_OUTPUT_DIR_NAMES =
    // Note that build/exploded-bundles and build/exploded-aar should *not* be excluded
    {"apk", "assets", "bundles", "classes", "dependency-cache", "incremental", "libs", "manifests", "symbols", "tmp", "res"};

  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel model,
                                                                @NotNull IdeaAndroidProject androidProject) {
    VirtualFile rootDir = androidProject.getRootDir();
    File rootDirPath = VfsUtilCore.virtualToIoFile(rootDir);

    List<ContentEntry> contentEntries = Lists.newArrayList(model.addContentEntry(rootDir));
    File buildFolderPath = androidProject.getDelegate().getBuildFolder();
    if (!FileUtil.isAncestor(rootDirPath, buildFolderPath, false)) {
      contentEntries.add(model.addContentEntry(pathToUrl(buildFolderPath)));
    }

    return contentEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull IdeaAndroidProject androidProject,
                                     @NotNull List<RootSourceFolder> orphans) {
    Variant selectedVariant = androidProject.getSelectedVariant();

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(contentEntries, mainArtifact, false, orphans);

    AndroidArtifact testArtifact = androidProject.findInstrumentationTestArtifactInSelectedVariant();
    if (testArtifact != null) {
      addSourceFolders(contentEntries, testArtifact, true, orphans);
    }

    AndroidProject delegate = androidProject.getDelegate();

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidProject.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(contentEntries, flavor, orphans);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidProject.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(contentEntries, buildTypeContainer.getSourceProvider(), false, orphans);
    }

    ProductFlavorContainer defaultConfig = delegate.getDefaultConfig();
    addSourceFolder(contentEntries, defaultConfig, orphans);

    addExcludedOutputFolders(contentEntries, delegate);
  }

  private void addSourceFolders(@NotNull Collection<ContentEntry> contentEntry,
                                @NotNull AndroidArtifact androidArtifact,
                                boolean isTest,
                                @NotNull List<RootSourceFolder> orphans) {
    addGeneratedSourceFolder(contentEntry, androidArtifact, isTest, orphans);

    SourceProvider variantSourceProvider = androidArtifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(contentEntry, variantSourceProvider, isTest, orphans);
    }

    SourceProvider multiFlavorSourceProvider = androidArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(contentEntry, multiFlavorSourceProvider, isTest, orphans);
    }
  }

  private void addGeneratedSourceFolder(@NotNull Collection<ContentEntry> contentEntries,
                                        @NotNull AndroidArtifact androidArtifact,
                                        boolean isTest,
                                        @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);
    addSourceFolders(contentEntries, androidArtifact.getGeneratedSourceFolders(), sourceType, true, orphans);

    sourceType = getResourceSourceType(isTest);
    addSourceFolders(contentEntries, androidArtifact.getGeneratedResourceFolders(), sourceType, true, orphans);
  }

  private void addSourceFolder(@NotNull Collection<ContentEntry> contentEntries,
                               @NotNull ProductFlavorContainer flavor,
                               @NotNull List<RootSourceFolder> orphans) {
    addSourceFolder(contentEntries, flavor.getSourceProvider(), false, orphans);

    Collection<SourceProviderContainer> extraArtifactSourceProviders = flavor.getExtraSourceProviders();
    for (SourceProviderContainer sourceProviders : extraArtifactSourceProviders) {
      String artifactName = sourceProviders.getArtifactName();
      if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)) {
        addSourceFolder(contentEntries, sourceProviders.getSourceProvider(), true, orphans);
        break;
      }
    }
  }

  private void addSourceFolder(@NotNull Collection<ContentEntry> contentEntries,
                               @NotNull SourceProvider sourceProvider,
                               boolean isTest,
                               @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);
    addSourceFolders(contentEntries, sourceProvider.getResDirectories(), sourceType, false, orphans);
    addSourceFolders(contentEntries, sourceProvider.getResourcesDirectories(), sourceType, false, orphans);

    sourceType = getSourceType(isTest);
    addSourceFolders(contentEntries, sourceProvider.getAidlDirectories(), sourceType, false, orphans);
    addSourceFolders(contentEntries, sourceProvider.getAssetsDirectories(), sourceType, false, orphans);
    addSourceFolders(contentEntries, sourceProvider.getJavaDirectories(), sourceType, false, orphans);
    addSourceFolders(contentEntries, sourceProvider.getJniDirectories(), sourceType, false, orphans);
    addSourceFolders(contentEntries, sourceProvider.getRenderscriptDirectories(), sourceType, false, orphans);
  }

  @NotNull
  private static JpsModuleSourceRootType getResourceSourceType(boolean isTest) {
    return isTest ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
  }

  @NotNull
  private static JpsModuleSourceRootType getSourceType(boolean isTest) {
    return isTest ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
  }

  private void addSourceFolders(@NotNull Collection<ContentEntry> contentEntries,
                                @NotNull Collection<File> folderPaths,
                                @NotNull JpsModuleSourceRootType type,
                                boolean generated,
                                @NotNull List<RootSourceFolder> orphans) {
    for (File folderPath : folderPaths) {
      addSourceFolder(contentEntries, folderPath, type, generated, orphans);
    }
  }

  private void addExcludedOutputFolders(@NotNull Collection<ContentEntry> contentEntries, @NotNull AndroidProject androidProject) {
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile file = contentEntry.getFile();
      if (file != null) {
        File rootDirPath = VfsUtilCore.virtualToIoFile(file);
        for (File child : FileUtil.notNullize(rootDirPath.listFiles())) {
          if (child.isDirectory() && child.getName().startsWith(".")) {
            addExcludedFolder(contentEntry, child);
          }
        }
      }
      File buildFolder = androidProject.getBuildFolder();
      for (String childName : EXCLUDED_OUTPUT_DIR_NAMES) {
        File child = new File(buildFolder, childName);
        addExcludedFolder(contentEntry, child);
      }
    }
  }
}
