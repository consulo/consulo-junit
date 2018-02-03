package conuslo.junit;

import consulo.platform.Platform;

/**
 * @author VISTALL
 * @since 2018-01-25
 */
public interface JUnitProperties
{
	boolean JUNIT4_SEARCH_4_TESTS_IN_CLASSPATH = Boolean.valueOf(Platform.current().getEnvironmentVariable("junit4.search.4.tests.in.classpath"));
}
