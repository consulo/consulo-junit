/**
 * @author VISTALL
 * @since 2025-04-12
 */
module com.intellij.junit.api {
    requires transitive consulo.execution.api;
    requires consulo.java.execution.api;

    exports consulo.junit;
    exports consulo.junit.external;
}