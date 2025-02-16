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

package org.gradle.internal.component.local.model

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.util.TestUtil
import org.gradle.util.internal.WrapUtil
import spock.lang.Specification

class DefaultLocalComponentMetadataTest extends Specification {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)
    def metadata = createMetadata()

    protected DefaultLocalComponentMetadata createMetadata() {
        new DefaultLocalComponentMetadata(id, componentIdentifier, "status", Mock(AttributesSchemaInternal), RootScriptDomainObjectContext.INSTANCE, TestUtil.calculatedValueContainerFactory())
    }

    def "can lookup configuration after it has been added"() {
        when:
        metadata.addConfiguration("super", "description", [] as Set<String>, ImmutableSet.of("super"), false, false, null, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)
        metadata.addConfiguration("conf", "description", ["super"] as Set, ImmutableSet.of("super", "conf"), true, true, null, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)

        then:
        metadata.configurationNames == ['conf', 'super'] as Set

        def conf = metadata.getConfiguration('conf')
        conf != null
        conf.visible
        conf.transitive
        conf.hierarchy == ['conf', 'super'] as Set

        def superConf = metadata.getConfiguration('super')
        superConf != null
        !superConf.visible
        !superConf.transitive
        superConf.hierarchy == ['super'] as Set
    }

    def "configuration has no dependencies or artifacts when none have been added"() {
        when:
        metadata.addConfiguration("super", "description", [] as Set<String>, ImmutableSet.of("super"), false, false, ImmutableAttributes.EMPTY, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)
        metadata.addConfiguration("conf", "description", ["super"] as Set, ImmutableSet.of("super", "conf"), true, true, ImmutableAttributes.EMPTY, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)

        then:
        def conf = metadata.getConfiguration('conf')
        conf.dependencies.empty
        conf.excludes.empty
        conf.files.empty

        when:
        conf.prepareToResolveArtifacts()

        then:
        conf.artifacts.empty
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = addConfiguration("conf")

        when:
        addArtifact(conf, artifact, file)
        conf.prepareToResolveArtifacts()

        then:
        metadata.getConfiguration("conf").artifacts.size() == 1

        def publishArtifact = metadata.getConfiguration("conf").artifacts.first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == metadata.getConfiguration("conf").artifact(artifact)
    }

    def "artifact is attached to child configurations"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def artifact3 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")
        def file3 = new File("artifact-3.zip")

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        def child1 = addConfiguration("child1", ["conf1", "conf2"])
        def child2 = addConfiguration("child2", ["conf1"])

        when:
        addArtifact(conf1, artifact1, file1)
        addArtifact(conf2, artifact2, file2)
        addArtifact(child1, artifact3, file3)
        conf1.prepareToResolveArtifacts()
        child1.prepareToResolveArtifacts()
        child2.prepareToResolveArtifacts()

        then:
        metadata.getConfiguration("conf1").artifacts.size() == 1
        metadata.getConfiguration("child1").artifacts.size() == 3
        metadata.getConfiguration("child2").artifacts.size() == 1
    }

    BuildableLocalConfigurationMetadata addConfiguration(String name, Collection<String> extendsFrom = [], AttributeContainerInternal attributes = ImmutableAttributes.EMPTY) {
        return metadata.addConfiguration(name, "", extendsFrom as Set, ImmutableSet.copyOf(extendsFrom + [name]), true, true, attributes as ImmutableAttributes, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)
    }

    void addArtifact(BuildableLocalConfigurationMetadata configuration, IvyArtifactName name, File file, TaskDependency buildDeps = null) {
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name.name, name.extension, name.type, name.classifier, new Date(), file)
        if (buildDeps != null) {
            publishArtifact.builtBy(buildDeps)
        }
        addArtifact(configuration, publishArtifact)
    }

    void addArtifact(BuildableLocalConfigurationMetadata configuration, PublishArtifact publishArtifact) {
        configuration.addArtifacts(
            new DefaultPublishArtifactSet("arts", WrapUtil.toDomainObjectSet(PublishArtifact, publishArtifact), TestFiles.fileCollectionFactory(), TestFiles.taskDependencyFactory())
        )
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")

        when:
        def publishArtifact = new DefaultPublishArtifact(artifact.name, artifact.extension, artifact.type, artifact.classifier, new Date(), file)
        conf1.addArtifacts([publishArtifact])
        conf2.addArtifacts([publishArtifact])
        conf1.prepareToResolveArtifacts()
        conf2.prepareToResolveArtifacts()

        then:
        metadata.getConfiguration("conf1").artifacts.size() == 1
        metadata.getConfiguration("conf1").artifacts == metadata.getConfiguration("conf2").artifacts
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = addConfiguration("conf")

        and:
        addArtifact(conf, artifact, file)
        conf.prepareToResolveArtifacts()

        expect:
        def ivyArtifact = artifactName()
        def resolveArtifact = metadata.getConfiguration("conf").artifact(ivyArtifact)
        resolveArtifact.file == file
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifactName()

        given:
        def conf = addConfiguration("conf")
        conf.prepareToResolveArtifacts()

        expect:
        def resolveArtifact = metadata.getConfiguration("conf").artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        when:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        addArtifact(conf1, artifact1, file1)
        addArtifact(conf2, artifact2, file2)
        conf1.prepareToResolveArtifacts()
        conf2.prepareToResolveArtifacts()

        then:
        def conf1Artifacts = metadata.getConfiguration("conf1").artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = metadata.getConfiguration("conf2").artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        metadata.getConfiguration("conf1").artifacts == [artifactMetadata1]
        metadata.getConfiguration("conf2").artifacts == [artifactMetadata2]
    }

    def "variants are attached to configuration but not its children"() {
        def variant1Attrs = attributes()
        def variant1Artifacts = ([Stub(PublishArtifact)] as Set)
        def variant2Attrs = attributes()
        def variant2Artifacts = ([Stub(PublishArtifact)] as Set)

        when:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2", ["conf1"])
        conf1.addVariant("variant1", Stub(VariantResolveMetadata.Identifier), Stub(DisplayName), variant1Attrs, ImmutableCapabilities.EMPTY, variant1Artifacts)
        conf2.addVariant("variant2", Stub(VariantResolveMetadata.Identifier), Stub(DisplayName), variant2Attrs, ImmutableCapabilities.EMPTY, variant2Artifacts)

        then:
        def config1 = metadata.getConfiguration("conf1")
        config1.variants.size() == 1
        config1.variants.first().name == "variant1"
        config1.variants.first().attributes == variant1Attrs

        def config2 = metadata.getConfiguration("conf2")
        config2.variants.size() == 1
        config2.variants.first().name == "variant2"
        config2.variants.first().attributes == variant2Attrs

        when:
        config1.prepareToResolveArtifacts()
        config2.prepareToResolveArtifacts()

        then:
        config1.variants.first().artifacts.size() == 1
        config2.variants.first().artifacts.size() == 1
    }

    private ImmutableAttributes attributes() {
        return Stub(ImmutableAttributes)
    }

    def "files attached to configuration and its children"() {
        def files1 = Stub(LocalFileDependencyMetadata)
        def files2 = Stub(LocalFileDependencyMetadata)
        def files3 = Stub(LocalFileDependencyMetadata)

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        def child1 = addConfiguration("child1", ["conf1", "conf2"])
        addConfiguration("child2", ["conf1"])

        when:
        conf1.addFiles(files1)
        conf2.addFiles(files2)
        child1.addFiles(files3)

        then:
        metadata.getConfiguration("conf1").files == [files1] as Set
        metadata.getConfiguration("conf2").files == [files2] as Set
        metadata.getConfiguration("child1").files == [files1, files2, files3] as Set
        metadata.getConfiguration("child2").files == [files1] as Set
    }

    def "dependency is attached to configuration and its children"() {
        def dependency1 = Mock(LocalOriginDependencyMetadata)
        dependency1.moduleConfiguration >> "conf1"
        def dependency2 = Mock(LocalOriginDependencyMetadata)
        dependency2.moduleConfiguration >> "conf2"
        def dependency3 = Mock(LocalOriginDependencyMetadata)
        dependency3.moduleConfiguration >> "child1"

        when:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        def child1 = addConfiguration("child1", ["conf1", "conf2"])
        addConfiguration("child2", ["conf1"])
        addConfiguration("other")

        conf1.addDependency(dependency1)
        conf2.addDependency(dependency2)
        child1.addDependency(dependency3)

        then:
        metadata.getConfiguration("conf1").dependencies == [dependency1]
        metadata.getConfiguration("conf2").dependencies == [dependency2]
        metadata.getConfiguration("child1").dependencies == [dependency1, dependency2, dependency3]
        metadata.getConfiguration("child2").dependencies == [dependency1]
        metadata.getConfiguration("other").dependencies.isEmpty()
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        def compile = metadata.addConfiguration("compile", null, [] as Set<String>, ImmutableSet.of("compile"), true, true, null, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)
        def runtime = metadata.addConfiguration("runtime", null, ["compile"] as Set, ImmutableSet.of("compile", "runtime"), true, true, null, true, null, true, ImmutableCapabilities.EMPTY, Collections.&emptyList)

        def rule1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "module1"))
        def rule2 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "module1"))

        compile.addExclude(rule1)
        runtime.addExclude(rule2)

        expect:
        def config = metadata.getConfiguration("runtime")
        def excludes = config.excludes
        excludes == [rule1, rule2]
        config.excludes.is(excludes)
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }
}
