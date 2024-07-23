// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.java.execution.impl.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.java.execution.impl.junit2.info.LocationUtil;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.configuration.ConfigurationType;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;

public abstract class AbstractAllInDirectoryConfigurationProducer extends JUnitConfigurationProducer
{
	protected AbstractAllInDirectoryConfigurationProducer(ConfigurationType configurationType)
	{
		super(configurationType);
	}

	@Override
	protected boolean setupConfigurationFromContext(JUnitConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement)
	{
		final Project project = configuration.getProject();
		final PsiElement element = context.getPsiLocation();
		if(!(element instanceof PsiDirectory))
		{
			return false;
		}
		final PsiJavaPackage aPackage = JavaRuntimeConfigurationProducerBase.checkPackage(element);
		if(aPackage == null)
		{
			return false;
		}
		final VirtualFile virtualFile = ((PsiDirectory) element).getVirtualFile();
		final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
		if(module == null)
		{
			return false;
		}
		if(!ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(virtualFile))
		{
			return false;
		}
		int testRootCount = ModuleRootManager.getInstance(module).getContentFolders(LanguageContentFolderScopes.onlyTest()).length;
		if(testRootCount < 2)
		{
			return false;
		}
		if(!LocationUtil.isJarAttached(context.getLocation(), aPackage, JUnitUtil.TEST_CASE_CLASS))
		{
			return false;
		}
		setupConfigurationModule(context, configuration);
		final JUnitConfiguration.Data data = configuration.getPersistentData();
		data.setDirName(virtualFile.getPath());
		data.TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY;
		configuration.setGeneratedName();
		return true;
	}
}
