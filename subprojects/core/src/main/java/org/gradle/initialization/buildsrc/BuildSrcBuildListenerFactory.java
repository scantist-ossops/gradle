/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.ExternalDependencyInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.ProjectDependencyInstrumentingArtifactTransform;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collections;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.HIERARCHY_COLLECTED_ATTRIBUTE;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.INSTRUMENTED_ATTRIBUTE;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.INSTRUMENTED_EXTERNAL_DEPENDENCY_ATTRIBUTE;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.INSTRUMENTED_PROJECT_DEPENDENCY_ATTRIBUTE;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.NOT_INSTRUMENTED_ATTRIBUTE;
import static org.gradle.api.internal.tasks.TaskDependencyUtil.getDependenciesForInternalUse;

@ServiceScope(Scopes.Build.class)
public class BuildSrcBuildListenerFactory {
    private final Action<ProjectInternal> buildSrcRootProjectConfiguration;
    private final AgentStatus agentStatus;
    private ScriptClassPathResolver resolver;

    public BuildSrcBuildListenerFactory(Action<ProjectInternal> buildSrcRootProjectConfiguration, ScriptClassPathResolver resolver, AgentStatus agentStatus) {
        this.buildSrcRootProjectConfiguration = buildSrcRootProjectConfiguration;
        this.resolver = resolver;
        this.agentStatus = agentStatus;
    }

    Listener create() {
        return new Listener(buildSrcRootProjectConfiguration, resolver, agentStatus);
    }

    /**
     * Inspects the build when configured, and adds the appropriate task to build the "main" `buildSrc` component.
     * On build completion, makes the runtime classpath of the main `buildSrc` component available.
     */
    public static class Listener extends InternalBuildAdapter implements EntryTaskSelector {
        private final AgentStatus agentStatus;
        private Configuration classpathConfiguration;
        private ProjectState rootProjectState;
        private final Action<ProjectInternal> rootProjectConfiguration;
        private final ScriptClassPathResolver resolver;

        private Listener(Action<ProjectInternal> rootProjectConfiguration, ScriptClassPathResolver resolver, AgentStatus agentStatus) {
            this.rootProjectConfiguration = rootProjectConfiguration;
            this.resolver = resolver;
            this.agentStatus = agentStatus;
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
            GradleInternal gradleInternal = (GradleInternal) gradle;
            // Run only those tasks scheduled by this selector and not the default tasks
            gradleInternal.getStartParameter().setTaskRequests(Collections.emptyList());
            ProjectInternal rootProject = gradleInternal.getRootProject();
            rootProjectState = rootProject.getOwner();
            rootProjectConfiguration.execute(rootProject);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void applyTasksTo(Context context, ExecutionPlan plan) {
            rootProjectState.applyToMutableState(rootProject -> {
                classpathConfiguration = rootProject.getConfigurations().resolvableDependencyScopeUnlocked("buildScriptClasspath");
                DependencyHandler dependencyHandler = rootProject.getDependencies();
                if (!dependencyHandler.getArtifactTypes().getByName(JAR_TYPE).getAttributes().contains(INSTRUMENTED_ATTRIBUTE)) {
                    dependencyHandler.getArtifactTypes().getByName(JAR_TYPE).getAttributes()
                        .attribute(INSTRUMENTED_ATTRIBUTE, NOT_INSTRUMENTED_ATTRIBUTE)
                        .attribute(HIERARCHY_COLLECTED_ATTRIBUTE, false);

                    // Register instrumentation transforms
                    registerTransform(dependencyHandler, ExternalDependencyInstrumentingArtifactTransform.class, INSTRUMENTED_EXTERNAL_DEPENDENCY_ATTRIBUTE);
                    registerTransform(dependencyHandler, ProjectDependencyInstrumentingArtifactTransform.class, INSTRUMENTED_PROJECT_DEPENDENCY_ATTRIBUTE);
                }
                resolver.prepareClassPath(classpathConfiguration, rootProject.getDependencies());
                classpathConfiguration.getDependencies().add(rootProject.getDependencies().create(rootProject));
                plan.addEntryTasks(getDependenciesForInternalUse(classpathConfiguration));
            });
        }

        public void registerTransform(DependencyHandler dependencyHandler, Class<? extends BaseInstrumentingArtifactTransform> transform, String instrumentedAttribute) {
            dependencyHandler.registerTransform(
                transform,
                spec -> {
                    spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, NOT_INSTRUMENTED_ATTRIBUTE);
                    spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, instrumentedAttribute);
                    spec.parameters(parameters -> {
                        parameters.getAgentSupported().set(agentStatus.isAgentInstrumentationEnabled());
                        parameters.getMaxSupportedJavaVersion().set(AsmConstants.MAX_SUPPORTED_JAVA_VERSION);
//                        parameters.getUpgradedPropertiesHash().set(gradleCoreInstrumentingTypeRegistry.getUpgradedPropertiesHash().map(Object::toString).orElse(null));
                    });
                }
            );
        }

        public ClassPath getRuntimeClasspath() {
            return rootProjectState.fromMutableState(project -> resolver.resolveClassPath(classpathConfiguration, project.getRootProject().getDependencies(), project.getRootProject().getConfigurations()));
        }
    }
}
