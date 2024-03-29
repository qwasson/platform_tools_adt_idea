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

import com.android.tools.lint.detector.api.ClassContext;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.tools.idea.rendering.ClassConverter.isValidClassFile;

/**
 * Class loader which can load classes for rendering and if necessary
 * convert the class file format down from unknown versions to a known version.
 */
public abstract class RenderClassLoader extends ClassLoader {
  protected static final Logger LOG = Logger.getInstance(RenderClassLoader.class);

  private ClassLoader myJarClassLoader;
  protected boolean myInsideJarClassLoader;

  public RenderClassLoader(@Nullable ClassLoader parent) {
    super(parent);
  }

  protected abstract URL[] getExternalJars();

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (myInsideJarClassLoader) {
      throw new ClassNotFoundException(name);
    }

    return load(name);
  }

  @NotNull
  protected Class<?> load(String name) throws ClassNotFoundException {
    Class<?> clz = loadClassFromJar(name);
    if (clz != null) {
      return clz;
    }

    throw new ClassNotFoundException(name);
  }

  @Nullable
  protected Class<?> loadClassFromJar(@NotNull String name) {
    if (myJarClassLoader == null) {
      final URL[] externalJars = getExternalJars();
      myJarClassLoader = createClassLoader(externalJars);
    }

    try {
      myInsideJarClassLoader = true;
      return myJarClassLoader.loadClass(name);
    }
    catch (UnsupportedClassVersionError e) {
      try {
        String relative = ClassContext.getInternalName(name) + DOT_CLASS;
        InputStream is = myJarClassLoader.getResourceAsStream(relative);
        if (is != null) {
          byte[] data = ByteStreams.toByteArray(is);
          is.close();
          if (!isValidClassFile(data)) {
            throw e;
          }

          byte[] rewritten = ClassConverter.rewriteClass(data);
          try {
            return defineClass(null, rewritten, 0, rewritten.length);
          }
          catch (UnsupportedClassVersionError inner) {
            // Wrap the UnsupportedClassVersionError as a InconvertibleClassError
            // such that clients can look up the actual bytecode version required.
            // Note that we wrap the original error, not the one from the attempted
            // class rewrite.
            throw InconvertibleClassError.wrap(e, name, data);
          }
        }
        throw e;
      } catch (Exception ex) {
        throw e;
      }
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      return null;
    }
    finally {
      myInsideJarClassLoader = false;
    }
  }

  private ClassLoader createClassLoader(URL[] externalJars) {
    return UrlClassLoader.build().parent(this).urls(externalJars).allowUnescaped().noPreload().get();
  }

  @Nullable
  protected Class<?> loadClassFromClassPath(String fqcn, File classPathFolder) {
    File classFile = findClassFile(classPathFolder, fqcn);
    if (classFile == null || !classFile.exists()) {
      return null;
    }

    return loadClassFile(fqcn, classFile);
  }

  @Nullable
  protected Class<?> loadClassFile(String fqcn, File classFile) {
    try {
      byte[] data = Files.toByteArray(classFile);
      return loadClass(fqcn, data);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  @Nullable
  protected Class<?> loadClass(String fqcn, @Nullable byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return defineClass(null, data, 0, data.length);
    } catch (UnsupportedClassVersionError e) {
      if (!isValidClassFile(data)) {
        throw e;
      }

      byte[] rewritten = ClassConverter.rewriteClass(data);
      try {
        return defineClass(null, rewritten, 0, rewritten.length);
      } catch (UnsupportedClassVersionError inner) {
        // Wrap the UnsupportedClassVersionError as a InconvertibleClassError
        // such that clients can look up the actual bytecode version required.
        // Note that we wrap the original error, not the one from the attempted
        // class rewrite.
        throw InconvertibleClassError.wrap(e, fqcn, data);
      }
    }
  }

  @Nullable
  private static File findClassFile(File parent, String className) {
    if (!parent.exists()) {
      return null;
    }

    String path = ClassContext.getInternalName(className).replace('/', File.separatorChar);
    File file = new File(parent, path + DOT_CLASS);
    if (file.exists()) {
      return file;
    }

    if (className.indexOf('$') != -1) {
      // The class name does not contain an ambiguous inner class name (inner classes
      // have already been separated by $ instead of .) so no need to do a search.
      return null;
    }

    // Inner classes? Dots are ambiguous (e.g. foo.bar.Foo.Bar), so try all valid combinations
    // (fully qualified names usually use upper case for class names and lower case for package names, which
    // is what the above getInternalName will use to decide between packages and classes, but
    // it's not a language requirement, which is why we fall back to this)
    //
    // The following loop will for foo.bar.Foo.Bar try the relative paths
    //    foo/bar/Foo/Baz.class
    //    foo/bar/Foo$Baz.class
    //    foo/bar$Foo$Baz.class
    //    foo$bar$Foo$Baz.class
    path = className.replace('.', File.separatorChar);
    while (true) {
      file = new File(parent, path + DOT_CLASS);
      if (file.exists()) {
        return file;
      }

      int last = path.lastIndexOf(File.separatorChar);
      if (last == -1) {
        return null;
      }
      path = path.substring(0, last) + '$' + path.substring(last + 1);
    }
  }
}
