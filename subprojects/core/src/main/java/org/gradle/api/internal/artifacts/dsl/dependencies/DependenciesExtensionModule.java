/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SecondParam;
import groovy.transform.stc.SimpleType;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyAdder;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.dsl.DependencyModifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.Transformers;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * This file is used to add <a href="https://groovy-lang.org/metaprogramming.html#_extension_modules">Groovy Extension Module</a> to {@link DependencyAdder} and {@link DependencyModifier} to make the Groovy DSL more idiomatic.
 * <p>
 * These extension methods allow an interface to implement a dependencies block in the Groovy DSL by
 * <ul>
 * <li>exposing an instance of {@link DependencyAdder} to add dependencies without explicitly calling {@link DependencyAdder#add(Dependency)}</li>
 * <li>exposing an instance of {@link DependencyModifier} to modify dependencies without explicitly calling {@link DependencyModifier#modify(ModuleDependency)}</li>
 * </ul>
 * </p>
 *
 * <p>
 * There are {@code call(...)} equivalents for all the {@code add(...)} methods in {@link DependencyAdder}.
 * </p>
 *
 * <p>
 * There are {@code call(...)} equivalents for all the {@code modify(...)} methods in {@link DependencyModifier}.
 * </p>
 *
 * @since 7.6
 *
 * @see Dependencies
 * @see DependencyAdder
 * @see DependencyModifier
 * @see DependencyFactory
 *
 * See DependenciesExtension for Kotlin DSL version of this.
 */
@SuppressWarnings("unused")
public class DependenciesExtensionModule {
    private static final String GROUP = "group";
    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final Set<String> MODULE_LEGAL_MAP_KEYS = ImmutableSet.of(GROUP, NAME, VERSION);

    /**
     * Creates an {@link ExternalModuleDependency} from the given Map notation. This emulates named parameters in Groovy DSL.
     * <p>
     * The map may contain:
     * <ul>
     *   <li>{@code group}</li>
     *   <li>{@code version}</li>
     * </ul>
     *
     * It must contain at least the following keys:
     * <ul>
     *   <li>{@code name}</li>
     * </ul>
     *
     * @param map a map of configuration parameters for the dependency
     *
     * @return the dependency
     */
    public static ExternalModuleDependency module(Dependencies self, Map<String, CharSequence> map) {
        if (!MODULE_LEGAL_MAP_KEYS.containsAll(map.keySet())) {
            CollectionUtils.SetDiff<String> diff = CollectionUtils.diffSetsBy(MODULE_LEGAL_MAP_KEYS, map.keySet(), Transformers.noOpTransformer());
            throw new IllegalArgumentException("The map must not contain the following keys: " + diff.rightOnly);
        }
        if (!map.containsKey(NAME)) {
            throw new IllegalArgumentException("The map must contain a name key.");
        }
        String group = extract(map, GROUP);
        String name = extract(map, NAME);
        String version = extract(map, VERSION);
        assert name != null;
        return self.module(group, name, version);
    }

    @Nullable
    private static String extract(Map<String, CharSequence> map, String key) {
        return (map.containsKey(key)) ? map.get(key).toString() : null;
    }

    /**
     * Modifies a dependency.
     *
     * @param dependencyNotation dependency to modify
     * @return the modified dependency
     *
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     *
     * @since 8.0
     */
    public static ExternalModuleDependency call(DependencyModifier self, CharSequence dependencyNotation) {
        return self.modify(dependencyNotation);
    }

    /**
     * Modifies a dependency.
     *
     * @param providerConvertibleToDependency dependency to modify
     * @return the modified dependency
     *
     * @since 8.0
     */
    public static Provider<? extends MinimalExternalModuleDependency> call(DependencyModifier self, ProviderConvertible<? extends MinimalExternalModuleDependency> providerConvertibleToDependency) {
        return self.modify(providerConvertibleToDependency);
    }

    /**
     * Modifies a dependency.
     *
     * @param providerToDependency dependency to modify
     * @return the modified dependency
     *
     * @since 8.0
     */
    public static <D extends ModuleDependency> Provider<D> call(DependencyModifier self, Provider<D> providerToDependency) {
        return self.modify(providerToDependency);
    }

    /**
     * Modifies a dependency.
     *
     * @param dependency dependency to modify
     * @return the modified dependency
     *
     * @since 8.0
     */
    public static <D extends ModuleDependency> D call(DependencyModifier self, D dependency) {
        return self.modify(dependency);
    }

    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, CharSequence dependencyNotation) {
        self.add(dependencyNotation);
    }

    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @param configuration an action to configure the dependency
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    public static void call(DependencyAdder self, CharSequence dependencyNotation, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency") Closure<?> configuration) {
        self.add(dependencyNotation, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param files files to add as a dependency
     */
    public static void call(DependencyAdder self, FileCollection files) {
        self.add(files);
    }

    /**
     * Add a dependency.
     *
     * @param files files to add as a dependency
     * @param configuration an action to configure the dependency
     */
    public static void call(DependencyAdder self, FileCollection files, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.FileCollectionDependency") Closure<?> configuration) {
        self.add(files, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param externalModule external module to add as a dependency
     */
    public static void call(DependencyAdder self, ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule) {
        self.add(externalModule);
    }

    /**
     * Add a dependency.
     *
     * @param externalModule external module to add as a dependency
     * @param configuration an action to configure the dependency
     */
    public static void call(DependencyAdder self, ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule, @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency") Closure<?> configuration) {
        self.add(externalModule, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     */
    public static void call(DependencyAdder self, Dependency dependency) {
        self.add(dependency);
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     * @param configuration an action to configure the dependency
     */
    public static <D extends Dependency> void call(DependencyAdder self, D dependency, @ClosureParams(SecondParam.class) Closure<?> configuration) {
        self.add(dependency, ConfigureUtil.configureUsing(configuration));
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     */
    public static void call(DependencyAdder self, Provider<? extends Dependency> dependency) {
        self.add(dependency);
    }

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     * @param configuration an action to configure the dependency
     */
    public static <D extends Dependency> void call(DependencyAdder self, Provider<? extends D> dependency, @ClosureParams(SecondParam.FirstGenericType.class) Closure<?> configuration) {
        self.add(dependency, ConfigureUtil.configureUsing(configuration));
    }
}
