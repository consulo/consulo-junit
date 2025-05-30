/**
 * @author VISTALL
 * @since 2023-01-27
 */
open module com.intellij.junit
{
    requires consulo.ide.api;

    requires consulo.java.indexing.api;
    requires consulo.java.analysis.impl;
    requires consulo.java.analysis.api;
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;
    requires consulo.java;

    requires com.intellij.junit.api;

    requires com.intellij.xml;

    requires com.intellij.junit5.rt;
    requires com.intellij.junit.rt;

    // TODO remove in future
    requires java.desktop;
    requires forms.rt;
}