/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.initialization;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.classpath.types.GradleCoreInstrumentingTypeRegistry;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.util.GradleVersion;

import java.util.EnumSet;
import java.util.Set;

public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {

    public static final Set<DependencyFactoryInternal.ClassPathNotation> NO_GRADLE_API = EnumSet.copyOf(ImmutableSet.of(
        DependencyFactoryInternal.ClassPathNotation.GRADLE_API,
        DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY
    ));

    public static final Attribute<Boolean> HIERARCHY_COLLECTED_ATTRIBUTE = Attribute.of("org.gradle.internal.hierarchy-collected", Boolean.class);
    public static final Attribute<String> INSTRUMENTED_ATTRIBUTE = Attribute.of("org.gradle.internal.instrumented", String.class);
    public static final String NOT_INSTRUMENTED_ATTRIBUTE = "not-instrumented";
    public static final String INSTRUMENTED_EXTERNAL_DEPENDENCY_ATTRIBUTE = "instrumented-external-dependency";
    public static final String INSTRUMENTED_PROJECT_DEPENDENCY_ATTRIBUTE = "instrumented-project-dependency";
    public final NamedObjectInstantiator instantiator;
    public final AgentStatus agentStatus;
    public final GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry;

    public DefaultScriptClassPathResolver(
        NamedObjectInstantiator instantiator,
        AgentStatus agentStatus,
        GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry
    ) {
        this.instantiator = instantiator;
        this.agentStatus = agentStatus;
        this.gradleCoreInstrumentingTypeRegistry = gradleCoreInstrumentingTypeRegistry;
    }

    @Override
    public void prepareClassPath(Configuration configuration, DependencyHandler dependencyHandler) {
        // should ideally reuse the `JvmPluginServices` but this code is too low level
        // and this service is therefore not available!
        AttributeContainer attributes = configuration.getAttributes();
        attributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, instantiator.named(Category.class, Category.LIBRARY));
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, instantiator.named(LibraryElements.class, LibraryElements.JAR));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, instantiator.named(Bundling.class, Bundling.EXTERNAL));
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()));
        attributes.attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, instantiator.named(GradlePluginApiVersion.class, GradleVersion.current().getVersion()));

        configuration.getDependencyConstraints().add(dependencyHandler.getConstraints().create(Log4jBannedVersion.LOG4J2_CORE_COORDINATES, constraint -> constraint.version(version -> {
            version.require(Log4jBannedVersion.LOG4J2_CORE_REQUIRED_VERSION);
            version.reject(Log4jBannedVersion.LOG4J2_CORE_VULNERABLE_VERSION_RANGE);
        })));
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
                    parameters.getUpgradedPropertiesHash().set(gradleCoreInstrumentingTypeRegistry.getUpgradedPropertiesHash().map(Object::toString).orElse(null));
                });
            }
        );
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration, DependencyHandler dependencyHandler, ConfigurationContainer configContainer) {
        FileCollection instrumentedExternalDependencies = getInstrumentedExternalDependencies(classpathConfiguration);
        FileCollection instrumentedProjectDependencies = getInstrumentedProjectDependencies(classpathConfiguration);
        return TransformedClassPath.handleInstrumentingArtifactTransform(DefaultClassPath.of(instrumentedExternalDependencies.plus(instrumentedProjectDependencies)));
    }

    public static FileCollection getInstrumentedExternalDependencies(Configuration classpathConfiguration) {
        return classpathConfiguration.getIncoming().artifactView((Action<? super ArtifactView.ViewConfiguration>) config -> {
            config.attributes(it -> it.attribute(INSTRUMENTED_ATTRIBUTE, INSTRUMENTED_EXTERNAL_DEPENDENCY_ATTRIBUTE));
            config.componentFilter(componentId -> {
                if (componentId instanceof OpaqueComponentIdentifier) {
                    DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
                    return !DefaultScriptClassPathResolver.NO_GRADLE_API.contains(classPathNotation);
                }
                return !(componentId instanceof ProjectComponentIdentifier);
            });
        }).getFiles();
    }

    public static FileCollection getInstrumentedProjectDependencies(Configuration classpathConfiguration) {
        return classpathConfiguration.getIncoming().artifactView((Action<? super ArtifactView.ViewConfiguration>) config -> {
            config.attributes(it -> it.attribute(INSTRUMENTED_ATTRIBUTE, INSTRUMENTED_PROJECT_DEPENDENCY_ATTRIBUTE));
            config.componentFilter(componentId -> componentId instanceof ProjectComponentIdentifier);
        }).getFiles();
    }
}
