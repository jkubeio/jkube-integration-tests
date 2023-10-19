/*
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
package org.eclipse.jkube.integrationtests.quarkus.rest;

import jakarta.inject.Singleton;

@Singleton
public class CoolApplicationWithTraceEnabledService {

  CoolApplicationWithTraceEnabled getCoolApplication() {
    return new CoolApplicationWithTraceEnabled("JKube", "Subatomic JKube really whips the llama's ass!");
  }
}
