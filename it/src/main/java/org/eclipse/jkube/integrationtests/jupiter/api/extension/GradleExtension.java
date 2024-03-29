/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests.jupiter.api.extension;

import io.fabric8.junit.jupiter.BaseExtension;
import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.eclipse.jkube.integrationtests.JKubeCase.GRADLE_VERSION_SYSTEM_PROPERTY;

public class GradleExtension implements BaseExtension, BeforeAllCallback, BeforeEachCallback {

  private volatile boolean cleanBuild = false;

  @Override
  public synchronized void beforeAll(ExtensionContext context) throws Exception {
    final var p = withAnnotation().and(f -> Modifier.isStatic(f.getModifiers()));
    for (Field field : extractFields(context, JKubeGradleRunner.class, p)) {
      setFieldValue(field, null,  getJKubeGradleRunner(context));
    }
  }


  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    final var p = withAnnotation().and(f -> !Modifier.isStatic(f.getModifiers()));
    for (Field field : extractFields(context, JKubeGradleRunner.class, p)) {
      setFieldValue(field, context.getRequiredTestInstance(), getJKubeGradleRunner(context));
    }
  }

  private JKubeGradleRunner getJKubeGradleRunner(ExtensionContext context) throws URISyntaxException {
    synchronized (context.getRoot()) {
      var singletonJKubeGradleRunner = getStore(context).get(JKubeGradleRunner.class, JKubeGradleRunner.class);
      if (singletonJKubeGradleRunner != null) {
        return singletonJKubeGradleRunner;
      }
      final var fields = extractFields(context, JKubeGradleRunner.class, withAnnotation());
      if (fields.length != 1) {
        throw new IllegalStateException("Invalid @Gradle annotation: expected 1 but found " + fields.length);
      }
      final var annotation = fields[0].getAnnotation(Gradle.class);
      var rootPath = Path.of("").toAbsolutePath();
      while (!rootPath.resolve(".github").toFile().exists()) {
        rootPath = rootPath.getParent();
      }
      var projectPath = rootPath.resolve("projects-to-be-tested").resolve("gradle");
      var gradleRunner = GradleRunner.create()
        .withGradleDistribution(new URI("https://services.gradle.org/distributions/gradle-" + System.getProperty(GRADLE_VERSION_SYSTEM_PROPERTY) + "-bin.zip"))
        .withProjectDir(projectPath.toFile());
      if (annotation.forwardOutput()) {
        gradleRunner.forwardOutput();
      }
      final var jKubeGradleRunner = new JKubeGradleRunner(
        gradleRunner, String.join(":", annotation.project()), projectPath);
      if (!cleanBuild) {
        final List<String> cleanBuildTasks = new ArrayList<>();
        if (annotation.clean()) {
          cleanBuildTasks.add("clean");
          cleanBuildTasks.add("k8sConfigView");
          cleanBuildTasks.add("ocConfigView");
        }
        if (annotation.build()) {
          cleanBuildTasks.add("build");
        }
        jKubeGradleRunner.tasks(false, false, cleanBuildTasks.toArray(new String[0])).build();
      }
      cleanBuild = true;
      gradleRunner.withArguments(Collections.emptyList());
      getStore(context).put(JKubeGradleRunner.class, jKubeGradleRunner);
      return jKubeGradleRunner;
    }
  }

  private static File getGradleInstallation() throws IOException, InterruptedException {
    final var localGradle = CliUtils.runCommand("readlink -f $(which gradle)").getOutput();
    return new File(localGradle).getAbsoluteFile().getParentFile().getParentFile();
  }

  private static Predicate<Field> withAnnotation() {
    return f -> f.isAnnotationPresent(Gradle.class);
  }
}
