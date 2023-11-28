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

/**
 * Defines different ways problems can be reported.
 *
 * @since 8.6
 */
@Incubating
public interface ProblemReporter {

    /**
     * Configures a new problem with error severity, reports it and uses it to throw a new exception.
     * <p>
     *
     * @return nothing, the method throws an exception
     */
    RuntimeException throwing(ProblemBuilderSpec action);

    /**
     * Configures a new problem with error severity using an existing exception as input, reports it and uses it to throw a new exception.
     * <p>
     *
     * @return nothing, the method throws an exception
     */
    RuntimeException rethrowing(RuntimeException e, ProblemBuilderSpec action);

    /**
     * Configures a new problem.
     * <p>
     * If all required fields are provided, the method creates and returns a new problem.
     * Problems should be reported separately with {@link #report(Problem)}.
     * TODO (donat) this method should return a simple Problem and ReportableProblem should go away.
     *
     * @param action the configuration object
     * @return a new problem
     */
    Problem create(ProblemBuilderSpec action);

    /**
     * Reports the target problem.
     * TODO (donat) this should probably be part of the Problems service as it is independent of the namespace.
     * @param problem The problem to report.
     */
    void report(Problem problem);
}
