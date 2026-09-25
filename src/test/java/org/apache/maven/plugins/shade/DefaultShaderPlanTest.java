/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.shade;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import org.apache.maven.plugins.shade.ShadePlanEntry.Compression;
import org.apache.maven.plugins.shade.ShadePlanEntry.Disposition;
import org.apache.maven.plugins.shade.ShadePlanEntry.Kind;
import org.apache.maven.plugins.shade.ShadePlanEntry.TimestampPolicy;
import org.apache.maven.plugins.shade.filter.Filter;
import org.apache.maven.plugins.shade.filter.SimpleFilter;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.relocation.SimpleRelocator;
import org.apache.maven.plugins.shade.resource.AppendingTransformer;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.apache.maven.plugins.shade.resource.ServicesResourceTransformer;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the dry-run {@link Shader#shadePlan(ShadeRequest)} support of {@link DefaultShader}.
 */
public class DefaultShaderPlanTest {

    private static final File TEST_PROJECT_JAR = new File("src/test/jars/test-project-1.0-SNAPSHOT.jar");

    private static final File TEST_ARTIFACT_JAR = new File("src/test/jars/test-artifact-1.0-SNAPSHOT.jar");

    @TempDir
    File temporaryFolder;

    @Test
    public void planRecordsRelocationDuplicatesAndPolicies() {
        Set<File> jars = new LinkedHashSet<>(asList(TEST_PROJECT_JAR, TEST_ARTIFACT_JAR));
        ShadeRequest request = request(
                jars,
                Collections.emptyList(),
                singletonList(new SimpleRelocator(
                        "org.codehaus.mojo.shade", "hidden.shade", Collections.emptyList(), Collections.emptyList())),
                Collections.emptyList());

        ShadePlan plan = newShader().shadePlan(request);

        assertEquals(ShadePlan.Status.COMPLETE, plan.getStatus());
        assertNull(plan.getFailure());
        assertFalse(request.getUberJar().exists(), "dry-run must not create the output JAR");
        assertEquals(asList(TEST_PROJECT_JAR.getPath(), TEST_ARTIFACT_JAR.getPath()), plan.getJars());

        ShadePlanEntry appClass = entry(plan, TEST_PROJECT_JAR, "org/codehaus/mojo/shade/App.class");
        assertNotNull(appClass);
        assertEquals(Kind.CLASS, appClass.getKind());
        assertEquals(Disposition.WRITTEN, appClass.getDisposition());
        assertEquals("hidden/shade/App.class", appClass.getOutput());
        assertEquals(Compression.DEFLATED, appClass.getCompression());
        assertEquals(TimestampPolicy.SOURCE_ENTRY_TIME, appClass.getTimestampPolicy());
        assertNull(appClass.getDuplicateGroup());
        assertNull(appClass.getWinner());

        ShadePlanEntry manifest = entry(plan, TEST_PROJECT_JAR, "META-INF/MANIFEST.MF");
        assertNotNull(manifest);
        assertEquals(Kind.MANIFEST, manifest.getKind());
        assertEquals(Disposition.WRITTEN, manifest.getDisposition());
        assertEquals("META-INF/MANIFEST.MF", manifest.getDuplicateGroup());
        assertEquals(Boolean.TRUE, manifest.getWinner());

        ShadePlanEntry lostManifest = entry(plan, TEST_ARTIFACT_JAR, "META-INF/MANIFEST.MF");
        assertNotNull(lostManifest);
        assertEquals(Disposition.DUPLICATE, lostManifest.getDisposition());
        assertEquals("META-INF/MANIFEST.MF", lostManifest.getDuplicateGroup());
        assertEquals(Boolean.FALSE, lostManifest.getWinner());
        assertNull(lostManifest.getCompression());

        ShadePlanEntry directory = entry(plan, TEST_PROJECT_JAR, "org/");
        assertNotNull(directory);
        assertEquals(Kind.DIRECTORY, directory.getKind());
        assertEquals(Disposition.SKIPPED, directory.getDisposition());

        assertTrue(plan.getOutputDirectories().contains("META-INF/"));
        assertTrue(plan.getOutputDirectories().contains("org/"));
        assertTrue(plan.getOutputDirectories().contains("hidden/"));
        assertTrue(plan.getTransformerOutputs().isEmpty());
    }

    @Test
    public void planRecordsFilterHitsAndTransformerConsumption() throws Exception {
        Filter filter =
                new SimpleFilter(singleton(TEST_PROJECT_JAR), Collections.emptySet(), singleton("META-INF/plexus/**"));
        AppendingTransformer transformer = new AppendingTransformer();
        java.lang.reflect.Field resource = AppendingTransformer.class.getDeclaredField("resource");
        resource.setAccessible(true);
        resource.set(transformer, "META-INF/maven/org.codehaus.mojo.shade/test-project/pom.properties");

        ShadeRequest request = request(
                new LinkedHashSet<>(singleton(TEST_PROJECT_JAR)),
                singletonList(filter),
                Collections.emptyList(),
                singletonList(transformer));

        ShadePlan plan = newShader().shadePlan(request);

        assertTrue(plan.isComplete());

        ShadePlanEntry filtered = entry(plan, TEST_PROJECT_JAR, "META-INF/plexus/components.xml");
        assertNotNull(filtered);
        assertEquals(Disposition.FILTERED, filtered.getDisposition());
        assertEquals(SimpleFilter.class.getName(), filtered.getFilteredBy());
        assertNull(filtered.getOutput());

        ShadePlanEntry transformed =
                entry(plan, TEST_PROJECT_JAR, "META-INF/maven/org.codehaus.mojo.shade/test-project/pom.properties");
        assertNotNull(transformed);
        assertEquals(Disposition.TRANSFORMED, transformed.getDisposition());
        assertEquals(AppendingTransformer.class.getName(), transformed.getTransformer());
        assertEquals(Compression.TRANSFORMER_DEFINED, transformed.getCompression());
        assertEquals(TimestampPolicy.TRANSFORMER_DEFINED, transformed.getTimestampPolicy());
    }

    @Test
    public void dryRunDoesNotPolluteTransformersOfTheNextRealShade() throws Exception {
        File jarA = new File(temporaryFolder, "a.jar");
        File jarB = new File(temporaryFolder, "b.jar");
        writeJar(jarA, entry("META-INF/services/com.example.Service", "com.example.ImplA\n"));
        writeJar(jarB, entry("META-INF/services/com.example.Service", "com.example.ImplB\n"));

        ServicesResourceTransformer transformer = new ServicesResourceTransformer();
        ShadeRequest request = request(
                new LinkedHashSet<>(asList(jarA, jarB)),
                Collections.emptyList(),
                Collections.emptyList(),
                singletonList(transformer));

        ShadePlan plan = newShader().shadePlan(request);

        assertTrue(plan.isComplete());
        assertFalse(request.getUberJar().exists());
        assertFalse(transformer.hasTransformedResource(), "dry-run must not feed the transformer");
        ShadePlanEntry service = entry(plan, jarA, "META-INF/services/com.example.Service");
        assertNotNull(service);
        assertEquals(Kind.SERVICE, service.getKind());
        assertEquals(Disposition.TRANSFORMED, service.getDisposition());
        assertEquals(
                "META-INF/services/com.example.Service",
                service.getTransformerDetail().get("output"));
        assertEquals(singletonList("META-INF/services/com.example.Service"), plan.getTransformerOutputs());

        // the very same transformer instance must see the resources exactly once in the real shading
        newShader().shade(request);

        try (JarFile jarFile = new JarFile(request.getUberJar())) {
            String content = IOUtil.toString(
                    jarFile.getInputStream(jarFile.getEntry("META-INF/services/com.example.Service")),
                    StandardCharsets.UTF_8.name());
            List<String> lines = new ArrayList<>(asList(content.split("\n")));
            assertEquals(asList("com.example.ImplA", "com.example.ImplB"), lines);
        }
    }

    @Test
    public void incompletePlanReportsUnreadableJar() throws IOException {
        File corrupt = new File(temporaryFolder, "corrupt.jar");
        try (OutputStream out = new FileOutputStream(corrupt)) {
            out.write("this is not a zip file".getBytes(StandardCharsets.UTF_8));
        }

        ShadePlan plan = newShader()
                .shadePlan(request(
                        new LinkedHashSet<>(singleton(corrupt)),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()));

        assertEquals(ShadePlan.Status.INCOMPLETE, plan.getStatus());
        assertNotNull(plan.getFailure());
        assertEquals(corrupt.getPath(), plan.getFailure().getJar());
        assertNull(plan.getFailure().getEntry());
        assertNotNull(plan.getFailure().getError());

        String json = ShadePlanJsonWriter.toJson(plan);
        assertTrue(json.contains("\"status\": \"INCOMPLETE\""));
        assertTrue(json.contains("\"failure\": {"));
    }

    @Test
    public void incompletePlanReportsFailingTransformer() throws IOException {
        File jar = new File(temporaryFolder, "input.jar");
        writeJar(jar, entry("data.txt", "hello"));

        ResourceTransformer broken = new ResourceTransformer() {
            @Override
            public boolean canTransformResource(String resource) {
                return resource.endsWith(".txt");
            }

            @Override
            public void processResource(String resource, InputStream is, List<Relocator> relocators) {}

            @Override
            public boolean hasTransformedResource() {
                return false;
            }

            @Override
            public void modifyOutputStream(java.util.jar.JarOutputStream os) {}

            @Override
            public Map<String, String> describePlanContribution(String resource, List<Relocator> relocators) {
                throw new IllegalStateException("boom");
            }
        };

        ShadePlan plan = newShader()
                .shadePlan(request(
                        new LinkedHashSet<>(singleton(jar)),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        singletonList(broken)));

        assertEquals(ShadePlan.Status.INCOMPLETE, plan.getStatus());
        assertNotNull(plan.getFailure());
        assertEquals(jar.getPath(), plan.getFailure().getJar());
        assertEquals("data.txt", plan.getFailure().getEntry());
        assertTrue(plan.getFailure().getError().contains("boom"));
    }

    @Test
    public void planClassifiesSpecialEntries() throws IOException {
        File jar = new File(temporaryFolder, "special.jar");
        byte[] zipContent = new byte[] {0x50, 0x4b, 0x03, 0x04, 1, 2, 3, 4};
        writeJar(
                jar,
                dir("META-INF/"),
                entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"),
                entry("META-INF/TEST.SF", "Signature-Version: 1.0\r\n\r\n"),
                entry("module-info.class", "\u00CA\u00FE\u00BA\u00BE"),
                entry("META-INF/versions/9/foo/Bar.class", "\u00CA\u00FE\u00BA\u00BE"),
                entry("META-INF/services/com.example.Service", "com.example.Impl\n"),
                entry("plain.txt", "plain"),
                stored("lib/nested.zip", zipContent));

        ShadePlan plan = newShader()
                .shadePlan(request(
                        new LinkedHashSet<>(singleton(jar)),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        singletonList(new ServicesResourceTransformer())));

        assertTrue(plan.isComplete());

        ShadePlanEntry signature = entry(plan, jar, "META-INF/TEST.SF");
        assertEquals(Kind.SIGNATURE, signature.getKind());
        assertEquals(Disposition.WRITTEN, signature.getDisposition());
        assertEquals(Compression.DEFLATED, signature.getCompression());

        ShadePlanEntry moduleInfo = entry(plan, jar, "module-info.class");
        assertEquals(Kind.MODULE_INFO, moduleInfo.getKind());
        assertEquals(Disposition.EXCLUDED, moduleInfo.getDisposition());
        assertNotNull(moduleInfo.getExcludedReason());

        ShadePlanEntry multiRelease = entry(plan, jar, "META-INF/versions/9/foo/Bar.class");
        assertEquals(Kind.MULTI_RELEASE, multiRelease.getKind());
        assertEquals(Disposition.WRITTEN, multiRelease.getDisposition());

        ShadePlanEntry service = entry(plan, jar, "META-INF/services/com.example.Service");
        assertEquals(Kind.SERVICE, service.getKind());
        assertEquals(Disposition.TRANSFORMED, service.getDisposition());

        ShadePlanEntry nestedZip = entry(plan, jar, "lib/nested.zip");
        assertEquals(Kind.RESOURCE, nestedZip.getKind());
        assertEquals(Compression.STORED, nestedZip.getCompression());

        ShadePlanEntry plain = entry(plan, jar, "plain.txt");
        assertEquals(Kind.RESOURCE, plain.getKind());
        assertEquals(Compression.DEFLATED, plain.getCompression());

        ShadePlanEntry dir = entry(plan, jar, "META-INF/");
        assertEquals(Kind.DIRECTORY, dir.getKind());
        assertEquals(Disposition.SKIPPED, dir.getDisposition());
    }

    @Test
    public void jsonHasFixedSchemaOrderingAndIsDeterministic() {
        Set<File> jars = new LinkedHashSet<>(asList(TEST_PROJECT_JAR, TEST_ARTIFACT_JAR));
        ShadeRequest request = request(jars, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        String first = ShadePlanJsonWriter.toJson(newShader().shadePlan(request));
        String second = ShadePlanJsonWriter.toJson(newShader().shadePlan(request));

        assertEquals(first, second, "same request must produce the same JSON");
        assertTrue(first.contains("\"schemaVersion\": 1"));
        assertTrue(first.contains("\"status\": \"COMPLETE\""));
        assertTrue(first.indexOf("\"jars\"") < first.indexOf("\"entries\""));

        ShadePlan plan = newShader().shadePlan(request);
        List<String> keys = new ArrayList<>();
        for (ShadePlanEntry entry : plan.getEntries()) {
            keys.add(entry.getJar() + '\0' + entry.getSource());
        }
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        assertEquals(sorted, keys, "entries must be sorted by jar and source path");
    }

    private DefaultShader newShader() {
        return new DefaultShader();
    }

    private ShadeRequest request(
            Set<File> jars, List<Filter> filters, List<Relocator> relocators, List<ResourceTransformer> transformers) {
        ShadeRequest request = new ShadeRequest();
        request.setJars(jars);
        request.setUberJar(new File(temporaryFolder, "uber-" + jars.hashCode() + ".jar"));
        request.setFilters(filters);
        request.setRelocators(relocators);
        request.setResourceTransformers(transformers);
        return request;
    }

    private static ShadePlanEntry entry(ShadePlan plan, File jar, String source) {
        for (ShadePlanEntry entry : plan.getEntries()) {
            if (entry.getJar().equals(jar.getPath()) && entry.getSource().equals(source)) {
                return entry;
            }
        }
        return null;
    }

    static JarSpec entry(String name, String content) {
        return new JarSpec(name, content.getBytes(StandardCharsets.UTF_8), false);
    }

    static JarSpec stored(String name, byte[] content) {
        return new JarSpec(name, content, true);
    }

    static JarSpec dir(String name) {
        return new JarSpec(name, new byte[0], false);
    }

    static final class JarSpec {
        final String name;

        final byte[] content;

        final boolean stored;

        JarSpec(String name, byte[] content, boolean stored) {
            this.name = name;
            this.content = content;
            this.stored = stored;
        }
    }

    static void writeJar(File file, JarSpec... specs) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file))) {
            for (JarSpec spec : specs) {
                JarEntry entry = new JarEntry(spec.name);
                if (spec.stored) {
                    CRC32 crc = new CRC32();
                    crc.update(spec.content);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(spec.content.length);
                    entry.setCompressedSize(spec.content.length);
                    entry.setCrc(crc.getValue());
                }
                jos.putNextEntry(entry);
                jos.write(spec.content);
                jos.closeEntry();
            }
        }
    }
}
