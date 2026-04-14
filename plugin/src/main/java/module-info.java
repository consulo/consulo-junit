/**
 * @author VISTALL
 * @since 2023-01-27
 */
open module com.intellij.junit
{
    // platform
    requires consulo.execution.test.api;
    requires consulo.execution.test.sm.api;
    requires consulo.language.editor.refactoring.api;
    requires consulo.compiler.api;
    requires consulo.configurable.api;
    requires consulo.file.editor.api;
    requires consulo.navigation.api;
    requires consulo.project.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.datacontext.api;
    requires consulo.version.control.system.api;
    requires consulo.file.template.api;
    requires consulo.module.ui.api;

    // java
    requires consulo.java.indexing.api;
    requires consulo.java.analysis.impl;
    requires consulo.java.analysis.api;
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;

    requires com.intellij.junit.api;

    requires com.intellij.junit5.rt;
    requires com.intellij.junit.rt;

    // TODO remove in future
    requires java.desktop;
    requires forms.rt;
}