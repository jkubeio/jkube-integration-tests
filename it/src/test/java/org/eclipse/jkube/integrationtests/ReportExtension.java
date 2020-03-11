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
package org.eclipse.jkube.integrationtests;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.APPEND;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class ReportExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback, CloseableResource {

  private volatile boolean testsStarted = false;
  private AtomicInteger testCount = new AtomicInteger(0);
  private AtomicBoolean hasException = new AtomicBoolean(false);
  private File report;

  @Override
  public synchronized void beforeAll(ExtensionContext context) throws Exception {
    if (!testsStarted) {
      report = new File("target", "jkube-test-report.txt");
      if (report.exists()) {
        report.delete();
      }
      report.createNewFile();
      context.getRoot().getStore(GLOBAL).put("Report Extension finalize callback", this);
      testsStarted = true;
    }
  }

  @Override
  public synchronized void afterEach(ExtensionContext context) throws Exception {
    testCount.incrementAndGet();
    reportTestResult(context);
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    if (context.getExecutionException().isPresent()) {
      hasException.set(true);
      writeToReport(String.format("[X] %s Some tests did not pass",
        context.getTestClass().map(Class::getSimpleName).orElse("ERROR")
      ));
    }
  }

  @Override
  public void close() throws Exception {
    if (hasException.get()) {
      writeToReport(String.format("[X] Some tests did not pass (Passed tests %s)", testCount.get()));
    } else {
      writeToReport(String.format("[\u2713] All tests (%s) passed successfully!!!", testCount.get()));
    }
  }

  private void reportTestResult(ExtensionContext context) throws IOException {
    writeToReport(String.format("[%s] %s - %s - %s",
      context.getExecutionException().isPresent() ? "X" : "\u2713",
      context.getTestClass().map(Class::getSimpleName).orElse("ERROR"),
      context.getTestMethod().map(Method::getName).orElse("ERROR"),
      context.getDisplayName()
    ));
  }

  private synchronized void writeToReport(String line) throws IOException {
    Files.write(report.toPath(), line.concat("\n").getBytes(StandardCharsets.UTF_8), APPEND);
  }
}
