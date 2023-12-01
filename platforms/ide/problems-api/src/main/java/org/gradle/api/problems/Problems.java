/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Problems API service.
 * <p>
 * The main purpose of this API is to allow clients to create, configure, and report problems in a centralized way.
 * <p>
 * Reported problems are exposed via build operation progress events, which then are converted to Tooling API progress events.
 *
 * @since 8.4
 */
@Incubating
@ServiceScope(Scopes.BuildTree.class)
public interface Problems {

    /**
     * Return a problem reporter for a particular namespace.
     * <p>
     * The recommendation is to use the plugin ID as the namespace.
     * The namespace will be part of the category of the reported problems.
     *
     * @return The problem reporter.
     * @see ProblemCategory
     * @since 8.6
     */
    ProblemReporter forNamespace(String namespace);
}
