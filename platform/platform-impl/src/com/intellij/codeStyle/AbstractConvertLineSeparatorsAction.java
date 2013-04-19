/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeStyle;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * @author Nikolai Matveev
 */
public abstract class AbstractConvertLineSeparatorsAction extends AnAction {

  private static Logger LOG = Logger.getInstance("#com.strintec.intellij.webmaster.lineSeparator.ConvertLineSeparatorsAction");

  @NotNull
  private final String mySeparator;

  protected AbstractConvertLineSeparatorsAction(@Nullable String text, @NotNull LineSeparator separator) {
    this(separator.toString() + " - " + text, separator.getSeparatorString());
  }
  
  protected AbstractConvertLineSeparatorsAction(@Nullable String text, @NotNull String separator) {
    super(text);
    mySeparator = separator;
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      final Presentation presentation = e.getPresentation();
      if (virtualFiles != null) {
        if (virtualFiles.length == 1) {
          presentation.setEnabled(!mySeparator.equals(LoadTextUtil.detectLineSeparator(virtualFiles[0], false)));
        }
        else {
          presentation.setEnabled(true);
        }
      }
      else {
        presentation.setEnabled(false);
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    final VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (virtualFiles == null) {
      return;
    }

    final VirtualFile projectVirtualDirectory;
    VirtualFile projectBaseDir = project.getBaseDir();
    if (projectBaseDir != null && projectBaseDir.isDirectory()) {
      projectVirtualDirectory = projectBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
    }
    else {
      projectVirtualDirectory = null;
    }
    final Set<VirtualFile> excludedFiles = getIgnoredFiles(project);

    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (VirtualFile file : virtualFiles) {
      VfsUtil.processFilesRecursively(
        file,
        new Processor<VirtualFile>() {
          @Override
          public boolean process(VirtualFile file) {
            if (shouldProcess(file, excludedFiles)) {
              changeLineSeparators(project, file, mySeparator);
            }
            return true;
          }
        },
        new Convertor<VirtualFile, Boolean>() {
          @Override
          public Boolean convert(VirtualFile dir) {
            return !dir.equals(projectVirtualDirectory)
                   && !fileTypeManager.isFileIgnored(dir.getName()); // Exclude files like '.git'
          }
        }
      );
    }
  }

  @NotNull
  private static Set<VirtualFile> getIgnoredFiles(@NotNull Project project) {
    final Set<VirtualFile> result = ContainerUtilRt.newHashSet();

    VirtualFile projectFile = project.getProjectFile();
    if (projectFile != null) {
      result.add(projectFile);
    }

    VirtualFile workspaceFile = project.getWorkspaceFile();
    if (workspaceFile != null) {
      result.add(workspaceFile);
    }

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      VirtualFile moduleFile = m.getModuleFile();
      if (moduleFile != null) {
        result.add(moduleFile);
      }
    }
    return result;
  }
  
  public static boolean shouldProcess(@NotNull VirtualFile file, @NotNull Project project) {
    return shouldProcess(file, getIgnoredFiles(project));
  }
  
  public static boolean shouldProcess(@NotNull VirtualFile file, @NotNull Set<VirtualFile> toIgnore) {
    return !file.isDirectory() && file.isWritable() && !toIgnore.contains(file)
           && !FileTypeManager.getInstance().isFileIgnored(file.getName()) && !file.getFileType().isBinary();
  }

  public static void changeLineSeparators(@NotNull final Project project,
                                          @NotNull final VirtualFile virtualFile,
                                          @NotNull final String newSeparator)
  {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getCachedDocument(virtualFile);
    if (document != null) {
      fileDocumentManager.saveDocument(document);
    }

    String currentSeparator = LoadTextUtil.detectLineSeparator(virtualFile, false);
    final String commandText;
    if (StringUtil.isEmpty(currentSeparator)) {
      commandText = "Changed line separators to " + LineSeparator.fromString(newSeparator);
    }
    else {
      assert currentSeparator != null;
      commandText = String.format("Changed line separators from %s to %s",
                                  LineSeparator.fromString(currentSeparator), LineSeparator.fromString(newSeparator));
    }

    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(project, new Runnable() {
      @Override
      public void run() {
        try {
          LoadTextUtil.changeLineSeparators(project, virtualFile, newSeparator, this);
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    }, commandText, null);
  }
}
