/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestTreeViewStructure;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class TestContext
{
	public static final com.intellij.openapi.util.Key<TestContext> DATA_KEY = com.intellij.openapi.util.Key.create("JUNIT_CONTEXT");

	private final TestFrameworkRunningModel myModel;
	private final AbstractTestProxy mySelection;

	public TestContext(final TestFrameworkRunningModel model, final AbstractTestProxy selection)
	{
		myModel = model;
		mySelection = selection;
	}

	public TestFrameworkRunningModel getModel()
	{
		return myModel;
	}

	public AbstractTestProxy getSelection()
	{
		return mySelection;
	}

	public boolean hasSelection()
	{
		return getSelection() != null && getModel() != null;
	}

	public boolean treeContainsSelection()
	{
		final AbstractTreeStructure structure = getModel().getTreeBuilder().getTreeStructure();
		return structure instanceof TestTreeViewStructure && ((TestTreeViewStructure) structure).getFilter().shouldAccept(getSelection());
	}

	public static TestContext from(final AnActionEvent event)
	{
		return event.getData(DATA_KEY);
	}
}
