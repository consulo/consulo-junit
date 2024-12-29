package com.intellij.execution.junit;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.ui.console.ConsoleFoldingContributor;
import consulo.execution.ui.console.ConsoleFoldingRegistrator;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 27/01/2023
 */
@ExtensionImpl
public class JUnitConsoleFoldingContributor implements ConsoleFoldingContributor
{
	@Override
	public void register(@Nonnull ConsoleFoldingRegistrator registrator)
	{
		registrator.addFolding("at org.junit.internal.runners.");
		registrator.addFolding("at org.junit.runners.");
		registrator.addFolding("at org.junit.runner.JUnitCore.");
		registrator.addFolding("at org.junit.Assert.fail(");
		registrator.addFolding("at org.junit.Assert.failNotSame(");
		registrator.addFolding("at org.junit.Assert.failSame(");
		registrator.addFolding("at junit.framework.Assert.assert");
		registrator.addFolding("at junit.framework.Assert.fail(");
		registrator.addFolding("at junit.framework.Assert.failNotSame(");
		registrator.addFolding("at junit.framework.Assert.failSame(");
		registrator.addFolding("at org.junit.Assert.internalArrayEquals(");
		registrator.addFolding("at org.junit.internal.ComparisonCriteria.arrayEquals(");
		registrator.addFolding("at org.junit.Assert.assert");
		registrator.addFolding("at com.intellij.junit3.");
		registrator.addFolding("at com.intellij.junit4.");
		registrator.addFolding("at com.intellij.junit5.");
		registrator.addFolding("at junit.framework.TestSuite.run");
		registrator.addFolding("at junit.framework.TestCase.run");
		registrator.addFolding("at junit.framework.TestResult");
		registrator.addFolding("at org.junit.jupiter.api.AssertionUtils.fail(");
		registrator.addFolding("at org.junit.jupiter.api.AssertEquals.failNotEqual(");
		registrator.addFolding("at org.junit.jupiter.api.AssertEquals.assertEquals(");
		registrator.addFolding("at org.junit.jupiter.api.Assertions.assertEquals(");
		registrator.addFolding("at org.junit.platform.");
		registrator.addFolding("at org.junit.jupiter.");
		registrator.addFolding("at org.junit.vintage.");
	}
}
