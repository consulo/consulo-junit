package consulo.junit.maven.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.CantRunException;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.junit.external.JUnit5RuntimeAppender;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.module.content.ProjectFileIndex;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.util.PathsList;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author VISTALL
 * @since 2025-04-12
 */
@ExtensionImpl
public class MavenJUnit5RuntimeAppender implements JUnit5RuntimeAppender {
    private final Project myProject;

    @Inject
    public MavenJUnit5RuntimeAppender(Project project) {
        myProject = project;
    }

    @Override
    public boolean canHandle(OwnJavaParameters javaParameters, GlobalSearchScope globalSearchScope) {
        return true;
    }

    @Override
    public void appendRuntime(OwnJavaParameters javaParameters, GlobalSearchScope globalSearchScope) throws CantRunException {
        appendJUnit5LauncherClasses(javaParameters, myProject, globalSearchScope);
    }

    public static void appendJUnit5LauncherClasses(OwnJavaParameters javaParameters, Project project, GlobalSearchScope globalSearchScope) throws CantRunException {
        final PathsList classPath = javaParameters.getClassPath();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiClass classFromCommon = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> psiFacade.findClass("org.junit.platform.commons.JUnitException", globalSearchScope));
        String launcherVersion = ObjectUtil.notNull(getVersion(classFromCommon), "1.0.0");

        if (!hasPackageWithDirectories(psiFacade, "org.junit.platform.launcher", globalSearchScope)) {
            attachFileFromLocalRepository(project, classPath, new MavenId("org.junit.platform", "junit-platform-launcher", launcherVersion));
        }

        if (!hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope)) {
            attachFileFromLocalRepository(project, classPath, new MavenId("org.junit.platform", "junit-platform-engine", launcherVersion));
        }

        //add standard engines only if no engine api is present
        if (!hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope)) {
            if (!hasPackageWithDirectories(psiFacade, "org.junit.jupiter.engine", globalSearchScope) && hasPackageWithDirectories(psiFacade, "org.junit.jupiter.api", globalSearchScope)) {
                PsiClass testAnnotation = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> psiFacade.findClass("org.junit.jupiter.api.Test", globalSearchScope));
                String version = ObjectUtil.notNull(getVersion(testAnnotation), "5.0.0");
                attachFileFromLocalRepository(project, classPath, new MavenId("org.junit.jupiter", "junit-jupiter-engine", version));
            }

            if (!hasPackageWithDirectories(psiFacade, "org.junit.vintage", globalSearchScope) && hasPackageWithDirectories(psiFacade, "junit.framework", globalSearchScope)) {
                String version = "4.12." + StringUtil.getShortName(launcherVersion);
                attachFileFromLocalRepository(project, classPath, new MavenId("org.junit.vintage", "junit-vintage-engine", version));
            }
        }
    }

    private static boolean hasPackageWithDirectories(JavaPsiFacade psiFacade, String packageQName, GlobalSearchScope globalSearchScope) {
        PsiPackage aPackage = psiFacade.findPackage(packageQName);
        return aPackage != null && aPackage.getDirectories(globalSearchScope).length > 0;
    }

    private static String getVersion(PsiClass classFromCommon) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(classFromCommon);
        if (virtualFile == null) {
            return null;
        }
        ProjectFileIndex index = ProjectFileIndex.getInstance(classFromCommon.getProject());
        VirtualFile root = index.getClassRootForFile(virtualFile);
        if (root != null && root.getFileSystem() instanceof ArchiveFileSystem) {
            VirtualFile manifestFile = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
            if (manifestFile == null) {
                return null;
            }

            try (final InputStream inputStream = manifestFile.getInputStream()) {
                return new Manifest(inputStream).getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            }
            catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean attachFileFromLocalRepository(Project project, PathsList classPath, MavenId mavenId) throws CantRunException {
        File repoDir = new File(MavenUtil.resolveM2Dir(), "repository");
        if (!repoDir.exists()) {
            return false;
        }

        String groupId = Objects.requireNonNull(mavenId.getGroupId());
        String artifactId = Objects.requireNonNull(mavenId.getArtifactId());
        String version = Objects.requireNonNull(mavenId.getVersion());

        File artifactDir = new File(repoDir, groupId.replace('.', '/') + "/" + artifactId + "/" + version);

        if (!artifactDir.exists()) {
            return false;
        }

        File jarFile = new File(artifactDir, artifactId + "-" + version + ".jar");
        if (!jarFile.exists()) {
            return false;
        }

        classPath.add(jarFile.getAbsoluteFile());
        return true;
    }
}
