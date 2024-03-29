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
package org.eclipse.jkube.integrationtests.maven;

import org.apache.commons.io.output.TeeOutputStream;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.Project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.eclipse.jkube.integrationtests.AsyncUtil.executorService;

public interface MavenCase extends Project {

  default List<String> getProfiles() {
    return new ArrayList<>();
  }

  default Properties properties(Map<String, String> propertyMap) {
    final Properties ret = new Properties();
    ret.putAll(propertyMap);
    return ret;
  }

  default Properties properties(String key, String value) {
    return properties(Collections.singletonMap(key, value));
  }

  default Properties properties(String... keyValuePairs) {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("Properties must be even number of arguments");
    }
    final Map<String, String> map = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      map.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return properties(map);
  }

  default MavenInvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  default MavenInvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, properties, null, null);
  }

  default MavenInvocationResult maven(
    String goal, Properties properties, MavenUtils.InvocationRequestCustomizer chainedCustomizer)
    throws IOException, InterruptedException, MavenInvocationException {
    return maven(goal, properties, null, chainedCustomizer);
  }

  default MavenInvocationResult maven(String goal, Properties properties, OutputStream out)
    throws IOException, InterruptedException, MavenInvocationException {
    return maven(goal, properties, out, null);
  }

  default MavenInvocationResult maven(
    String goal, Properties properties, OutputStream out, MavenUtils.InvocationRequestCustomizer chainedCustomizer)
    throws IOException, InterruptedException, MavenInvocationException {
    try {
      return mavenAsync(goal, properties, out, chainedCustomizer).get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MavenInvocationException) {
        throw (MavenInvocationException) e.getCause();
      } else if (e.getCause() instanceof  IOException) {
        throw (IOException) e.getCause();
      }
      throw new IllegalStateException(e);
    }
  }

  default CompletableFuture<MavenInvocationResult> mavenAsync(
    String goal, Properties properties, OutputStream out, MavenUtils.InvocationRequestCustomizer chainedCustomizer) {
    final CompletableFuture<MavenInvocationResult> future = new CompletableFuture<>();
    final var asyncRun = CompletableFuture.runAsync(() -> {
      try (
        final var baos = new ByteArrayOutputStream();
        final var tee = new TeeOutputStream(baos, out == null ? OutputStream.nullOutputStream() : out);
        final var printStream = new PrintStream(tee, true)
      ) {
        final MavenUtils.InvocationRequestCustomizer recordStdOutCustomizer = invocationRequest ->
          invocationRequest.setOutputHandler(new PrintStreamHandler(printStream, true));
        final InvocationResult mavenResult = MavenUtils.execute(i -> {
          i.setBaseDirectory(new File("../"));
          i.setProjects(Collections.singletonList(getProject()));
          i.setGoals(Collections.singletonList(goal));
          i.setProfiles(getProfiles());
          i.setProperties(properties);
          recordStdOutCustomizer.customize(i);
          Optional.ofNullable(chainedCustomizer).ifPresent(cc -> cc.customize(i));
        });
        printStream.flush();
        baos.flush();
        future.complete(new MavenInvocationResult(mavenResult, baos.toString(StandardCharsets.UTF_8)));
      } catch (IOException | MavenInvocationException ex) {
        future.completeExceptionally(ex);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        future.completeExceptionally(ex);
      }
    }, executorService());
    future.whenCompleteAsync((result, throwable) -> {
      if (!asyncRun.isDone()) {
        asyncRun.cancel(true);
      }
    });
    return future;
  }
}
