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
package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.eclipse.jkube.integrationtests.cli.CliUtils.runCommand;

public class KubernetesClientAssertion<T extends KubernetesResource> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesClientAssertion.class);
  static final long DEFAULT_AWAIT_TIME_SECONDS = 45L;
  static final Duration CONNECT_TIMEOUT = Duration.of(10L, ChronoUnit.SECONDS);
  static final Duration READ_TIMEOUT = Duration.of(30L, ChronoUnit.SECONDS);

  private static class OkHttpClientHolder {
    private static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
      .retryOnConnectionFailure(true)
      .connectTimeout(CONNECT_TIMEOUT)
      .readTimeout(READ_TIMEOUT)
      .build();
  }
  private static OkHttpClient httpClient() {
    return OkHttpClientHolder.INSTANCE;
  }

  static CompletableFuture<Response> getWithRetry(ExecutorService executor, String url) {
    final var response = new CompletableFuture<Response>();
    executor.submit(() -> {
      while (true) {
        try {
          response.complete(httpClient().newCall(new Request.Builder().get().url(url).build()).execute());
          break;
        } catch (IOException e) {
          log.warn("Connection to {} failed, retrying", url);
          Thread.sleep(3000L);
          // IGNORE
        }
      }
      return null;
    });
    return response;
  }

  private final JKubeCase jKubeCase;
  private final T kubernetesResource;

  KubernetesClientAssertion(JKubeCase jKubeCase, T kubernetesResource) {
    this.jKubeCase = jKubeCase;
    this.kubernetesResource = kubernetesResource;
  }

  final String getClusterHost() throws MalformedURLException {
    return new URL(jKubeCase.getKubernetesClient().getConfiguration().getMasterUrl()).getHost();
  }

  public T getKubernetesResource() {
    return kubernetesResource;
  }

  public KubernetesClient getKubernetesClient() {
    return jKubeCase.getKubernetesClient();
  }

  public OpenShiftClient getOpenShiftClient() {
    return getKubernetesClient().adapt(OpenShiftClient.class);
  }

  public boolean isOpenShiftClient() {
    return getKubernetesClient().isAdaptable(OpenShiftClient.class);
  }

  public String getApplication() {
    return jKubeCase.getApplication();
  }

  static void printDiagnosis(JKubeCase jKubeCase) throws IOException, InterruptedException {
    System.err.println("\n\n===========================");
    System.err.println("\nCurrent PODs:");
    jKubeCase.getKubernetesClient().pods().list().getItems().forEach(pod -> {
      System.err.println("\n---------------------------");
      System.err.println(pod.getMetadata().getName());
      System.err.println("\nMetadata:");
      System.err.println(pod.getMetadata());
      System.err.println("\nStatus:");
      System.err.println(pod.getStatus());
      System.err.println("---------------------------");
    });
    if (jKubeCase.getKubernetesClient().isAdaptable(OpenShiftClient.class)) {
      final OpenShiftClient oc = jKubeCase.getKubernetesClient().adapt(OpenShiftClient.class);
      System.err.println("\n\n===========================");
      System.err.println("\nDeployment:");
      System.err.println("\n\nStatus:");
      System.err.println(oc.deploymentConfigs().withName(jKubeCase.getApplication()).get().getStatus());
      System.err.println("\n\nStatus Details:");
      System.err.println(oc.deploymentConfigs().withName(jKubeCase.getApplication()).get().getStatus().getDetails());
      System.err.println("\n\nStatus Conditions:");
      oc.deploymentConfigs().withName(jKubeCase.getApplication()).get().getStatus().getConditions().forEach(
        condition -> System.err.println(condition.toString())
      );
    }
    System.err.println("\n\n===========================");
    System.err.println("\n\nDisk Space:");
    System.err.println(runCommand("df -h").getOutput());
    System.err.println("\n\n===========================");
    System.err.println("\n\nDisk inodes:");
    System.err.println(runCommand("df -hi").getOutput());
  }
}
