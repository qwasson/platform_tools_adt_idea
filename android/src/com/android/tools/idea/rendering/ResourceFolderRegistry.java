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
package com.android.tools.idea.rendering;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ResourceFolderRegistry {
  private final static Map<VirtualFile, ResourceFolderRepository> ourDirMap = new HashMap<VirtualFile, ResourceFolderRepository>();

  public static void reset() {
    for (Map.Entry<VirtualFile, ResourceFolderRepository> entry : ourDirMap.entrySet()) {
      VirtualFile dir = entry.getKey();
      ResourceFolderRepository repository = entry.getValue();
      Project project = repository.getFacet().getModule().getProject();
      PsiProjectListener.removeRoot(project, dir, repository);

    }
    ourDirMap.clear();
  }

  @NotNull
  public static ResourceFolderRepository get(@NotNull final AndroidFacet facet, @NotNull VirtualFile dir) {
    ResourceFolderRepository repository = ourDirMap.get(dir);
    if (repository == null) {
      repository = ResourceFolderRepository.create(facet, dir);
      PsiProjectListener.addRoot(facet.getModule().getProject(), dir, repository);

      ourDirMap.put(dir, repository);
    }

    return repository;
  }
}
