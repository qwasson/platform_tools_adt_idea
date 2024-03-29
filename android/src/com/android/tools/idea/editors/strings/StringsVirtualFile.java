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
package com.android.tools.idea.editors.strings;

import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StringsVirtualFile extends LightVirtualFile {
  private static final Key<StringsVirtualFile> KEY = Key.create(StringsVirtualFile.class.getName());
  private final AndroidFacet myFacet;

  private StringsVirtualFile(@NotNull AndroidFacet facet) {
    super("Translations Editor", StringsResourceFileType.INSTANCE, "This feature is in active development. Please file bugs.");
    myFacet = facet;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Nullable
  public static StringsVirtualFile getInstance(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    StringsVirtualFile vfile = facet.getUserData(KEY);
    if (vfile == null) {
      vfile = new StringsVirtualFile(facet);
      facet.putUserData(KEY, vfile);
    }

    return vfile;
  }

  private static class StringsResourceFileType extends FakeFileType {
    public static final StringsResourceFileType INSTANCE = new StringsResourceFileType();

    @Override
    public boolean isMyFileType(VirtualFile file) {
      return file.getFileType() instanceof StringsResourceFileType;
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "";
    }

    @Override
    public Icon getIcon() {
      return AndroidIcons.Globe;
    }
  }
}
