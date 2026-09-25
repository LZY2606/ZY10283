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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.maven.plugins.shade.DefaultShaderPlanTest.JarSpec;
import org.apache.maven.plugins.shade.ShadePlanEntry.Compression;
import org.apache.maven.plugins.shade.ShadePlanEntry.Disposition;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.relocation.SimpleRelocator;
import org.apache.maven.plugins.shade.resource.ManifestResourceTransformer;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.apache.maven.plugins.shade.resource.ServicesResourceTransformer;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration fixture for the dry-run shade plan: runs the dry-run and the real shading over the
 * same inputs, reverse-engineers the entry list of the produced JAR and checks that it corresponds
 * to the plan entry by entry. Also permutes the input artifact order to verify the documented
 * "first artifact wins" rule and the stability of all fields that are promised to be stable. The
 * compressed bytes of the produced JAR are never compared: they are not part of the contract.
 */
public class ShadePlanConsistencyTest {

    @TempDir
    File temporaryFolder;

    private File jarA;

    private File jarB;

    private byte[] classBytes;

    @BeforeEach
    public void buildInputJars() throws IOException {
        classBytes = readClassBytes();
        jarA = new File(temporaryFolder, "a.jar");
        jarB = new File(temporaryFolder, "b.jar");
        byte[] nestedZip = new byte[] {0x50, 0x4b, 0x03, 0x04, 9, 9, 9, 9};
        DefaultShaderPlanTest.writeJar(
                jarA,
                DefaultShaderPlanTest.entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"),
                DefaultShaderPlanTest.dir("shared/"),
                new JarSpec("shared/Shared.class", classBytes, false),
                DefaultShaderPlanTest.entry("shared/config.properties", "origin=A\n"),
                DefaultShaderPlanTest.entry("META-INF/services/com.example.Service", "com.example.ImplA\n"),
                DefaultShaderPlanTest.entry("META-INF/TEST.SF", "Signature-Version: 1.0\r\n\r\n"),
                DefaultShaderPlanTest.entry("module-info.class", "\u00CA\u00FE\u00BA\u00BE"),
                new JarSpec("META-INF/versions/9/shared/Versioned.class", classBytes, false),
                DefaultShaderPlanTest.stored("lib/nested.zip", nestedZip),
                DefaultShaderPlanTest.entry("a/OnlyA.txt", "only in A\n"));
        DefaultShaderPlanTest.writeJar(
                jarB,
                DefaultShaderPlanTest.entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"),
                new JarSpec("shared/Shared.class", classBytes, false),
                DefaultShaderPlanTest.entry("shared/config.properties", "origin=B\n"),
                DefaultShaderPlanTest.entry("META-INF/services/com.example.Service", "com.example.ImplB\n"),
                DefaultShaderPlanTest.entry("b/OnlyB.txt", "only in B\n"));
    }

    @Test
    public void planMatchesProducedJarEntryByEntry() throws Exception {
        List<ResourceTransformer> transformers = newTransformers();
        ShadeRequest request = request(jarA, jarB, transformers);
        DefaultShader shader = new DefaultShader();

        ShadePlan plan = shader.shadePlan(request);

        assertEquals(ShadePlan.Status.COMPLETE, plan.getStatus());
        assertFalse(request.getUberJar().exists(), "dry-run must not create the output JAR");
        assertFalse(transformers.get(0).hasTransformedResource(), "dry-run must not feed the services transformer");

        // real shading with the very same transformer instances: only possible without pollution
        shader.shade(request);

        Map<String, Integer> actualEntries = entriesOf(request.getUberJar());

        Set<String> expectedEntries = new TreeSet<>();
        for (ShadePlanEntry entry : plan.getEntries()) {
            if (entry.getDisposition() == Disposition.WRITTEN) {
                expectedEntries.add(entry.getOutput());
            }
        }
        expectedEntries.addAll(plan.getOutputDirectories());
        expectedEntries.addAll(plan.getTransformerOutputs());

        assertEquals(expectedEntries, new TreeSet<>(actualEntries.keySet()));

        // the promised compression policy holds for every directly written entry
        for (ShadePlanEntry entry : plan.getEntries()) {
            if (entry.getDisposition() == Disposition.WRITTEN) {
                int expectedMethod = entry.getCompression() == Compression.STORED ? ZipEntry.STORED : ZipEntry.DEFLATED;
                assertEquals(
                        expectedMethod,
                        actualEntries.get(entry.getOutput()).intValue(),
                        "compression of " + entry.getOutput());
            }
        }

        // documented winner: the first artifact in processing order wins a duplicate group
        assertEquals("origin=A\n", readEntry(request.getUberJar(), "hidden/config.properties"));

        // transformer outputs declared by the plan are really produced
        String services = readEntry(request.getUberJar(), "META-INF/services/com.example.Service");
        assertTrue(services.contains("com.example.ImplA"));
        assertTrue(services.contains("com.example.ImplB"));
        assertTrue(actualEntries.containsKey(JarFile.MANIFEST_NAME));
    }

    @Test
    public void permutedInputOrderKeepsDocumentedWinnerAndStableFields() {
        ShadePlan planAB = new DefaultShader().shadePlan(request(jarA, jarB, newTransformers()));
        ShadePlan planBA = new DefaultShader().shadePlan(request(jarB, jarA, newTransformers()));

        assertTrue(planAB.isComplete());
        assertTrue(planBA.isComplete());

        // input order is part of the plan and drives the winner of every duplicate group
        assertEquals(asList(jarA.getPath(), jarB.getPath()), planAB.getJars());
        assertEquals(asList(jarB.getPath(), jarA.getPath()), planBA.getJars());

        ShadePlanEntry configAB = entry(planAB, jarA, "shared/config.properties");
        ShadePlanEntry configBA = entry(planBA, jarB, "shared/config.properties");
        assertEquals(Boolean.TRUE, configAB.getWinner());
        assertEquals(Boolean.TRUE, configBA.getWinner());
        assertEquals("hidden/config.properties", configAB.getDuplicateGroup());
        assertEquals("hidden/config.properties", configBA.getDuplicateGroup());
        assertEquals(
                Boolean.FALSE, entry(planAB, jarB, "shared/config.properties").getWinner());
        assertEquals(
                Boolean.FALSE, entry(planBA, jarA, "shared/config.properties").getWinner());

        // fields that do not depend on processing order are stable across permutations
        Map<String, String> stableAB = stableFieldsByEntry(planAB);
        Map<String, String> stableBA = stableFieldsByEntry(planBA);
        assertEquals(stableAB.keySet(), stableBA.keySet());
        for (Map.Entry<String, String> stable : stableAB.entrySet()) {
            assertEquals(stable.getValue(), stableBA.get(stable.getKey()), "stable fields of " + stable.getKey());
        }

        // the JSON rendering is stable as well: only jars order, winners and duplicate groups move
        assertEquals(planAB.getTransformerOutputs(), planBA.getTransformerOutputs());
        assertEquals(planAB.getOutputDirectories(), planBA.getOutputDirectories());
    }

    private Map<String, String> stableFieldsByEntry(ShadePlan plan) {
        Map<String, String> stable = new TreeMap<>();
        for (ShadePlanEntry entry : plan.getEntries()) {
            if (entry.getDuplicateGroup() != null) {
                continue; // winner and group membership depend on processing order
            }
            String key = entry.getJar() + '\0' + entry.getSource();
            stable.put(
                    key,
                    entry.getKind() + "|" + entry.getDisposition() + "|" + entry.getFilteredBy() + "|"
                            + entry.getExcludedReason() + "|" + entry.getOutput() + "|" + entry.getTransformer() + "|"
                            + entry.getTransformerDetail() + "|" + entry.getCompression() + "|"
                            + entry.getTimestampPolicy());
        }
        return stable;
    }

    private ShadeRequest request(File first, File second, List<ResourceTransformer> transformers) {
        ShadeRequest request = new ShadeRequest();
        Set<File> jars = new LinkedHashSet<>();
        jars.add(first);
        jars.add(second);
        request.setJars(jars);
        request.setUberJar(new File(temporaryFolder, "uber-" + first.getName() + '-' + second.getName() + ".jar"));
        request.setFilters(Collections.emptyList());
        request.setRelocators(relocators());
        request.setResourceTransformers(transformers);
        return request;
    }

    private static List<Relocator> relocators() {
        return Collections.singletonList(
                new SimpleRelocator("shared", "hidden", Collections.emptyList(), Collections.emptyList()));
    }

    private static List<ResourceTransformer> newTransformers() {
        List<ResourceTransformer> transformers = new ArrayList<>();
        transformers.add(new ServicesResourceTransformer());
        transformers.add(new ManifestResourceTransformer());
        return transformers;
    }

    private static ShadePlanEntry entry(ShadePlan plan, File jar, String source) {
        for (ShadePlanEntry entry : plan.getEntries()) {
            if (entry.getJar().equals(jar.getPath()) && entry.getSource().equals(source)) {
                return entry;
            }
        }
        throw new AssertionError("no plan entry for " + jar + " " + source);
    }

    private static byte[] readClassBytes() throws IOException {
        try (JarFile jarFile = new JarFile("src/test/jars/test-project-1.0-SNAPSHOT.jar")) {
            return IOUtil.toByteArray(jarFile.getInputStream(jarFile.getEntry("org/codehaus/mojo/shade/App.class")));
        }
    }

    private static Map<String, Integer> entriesOf(File jar) throws IOException {
        Map<String, Integer> entries = new LinkedHashMap<>();
        try (JarFile jarFile = new JarFile(jar)) {
            for (Enumeration<JarEntry> en = jarFile.entries(); en.hasMoreElements(); ) {
                JarEntry entry = en.nextElement();
                entries.put(entry.getName(), entry.getMethod());
            }
        }
        return entries;
    }

    private static String readEntry(File jar, String name) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            JarEntry entry = jarFile.getJarEntry(name);
            assertNotNull(entry, "missing entry " + name);
            return IOUtil.toString(jarFile.getInputStream(entry), StandardCharsets.UTF_8.name());
        }
    }
}
