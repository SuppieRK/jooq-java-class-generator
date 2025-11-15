package io.github.suppierk.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class GeneratorTaskClasspathInputsTest {

  @Test
  void locateClasspathResourceRootsIncludesProcessedResources() {
    final Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(JavaPlugin.class);

    final List<File> roots = GeneratorTask.locateClasspathResourceRoots(project);

    final File processed =
        project.getLayout().getBuildDirectory().dir("resources/main").get().getAsFile();
    assertTrue(
        roots.contains(processed),
        "Processed resources directory should be part of the watched locations");

    final File sourceResources = project.file("src/main/resources");
    assertTrue(
        roots.contains(sourceResources),
        "Source resources directory should be part of the watched locations");
  }
}
