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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The result of a dry-run shading: for every entry of every input JAR it records which filter, relocation
 * and resource transformer applies, which entries overlap and which occurrence wins, without writing the
 * output JAR.
 * <p>
 * The plan has a fixed JSON schema (see {@link #toJson()}): keys are emitted in a fixed order and entries
 * are sorted by input JAR path and original entry path, so two dry-runs of the same request produce the
 * same document.
 *
 * @since 3.6.3
 */
public final class ShadePlan {

    /**
     * Version of the JSON schema emitted by {@link #toJson()}.
     */
    public static final int SCHEMA_VERSION = 1;

    private static final Comparator<ShadePlanEntry> ENTRY_ORDER = Comparator.comparing(ShadePlanEntry::getJar)
            .thenComparing(ShadePlanEntry::getPath)
            .thenComparing(entry -> entry.getKind().name());

    private final List<String> jars;

    private final List<ShadePlanEntry> entries;

    private final boolean complete;

    private final String failureJar;

    private final String failureEntry;

    private final String failureMessage;

    private ShadePlan(Builder builder) {
        this.jars = Collections.unmodifiableList(new ArrayList<>(builder.jars));
        this.complete = builder.failureMessage == null;
        this.failureJar = builder.failureJar;
        this.failureEntry = builder.failureEntry;
        this.failureMessage = builder.failureMessage;

        Map<String, List<ShadePlanEntry>> candidatesByPath = new LinkedHashMap<>();
        for (ShadePlanEntry entry : builder.entries) {
            if (entry.getKind() != ShadePlanEntry.Kind.DIRECTORY
                    && (entry.getOutcome() == ShadePlanEntry.Outcome.WRITTEN
                            || entry.getOutcome() == ShadePlanEntry.Outcome.DUPLICATE)) {
                candidatesByPath
                        .computeIfAbsent(entry.getPath(), k -> new ArrayList<>())
                        .add(entry);
            }
        }
        Set<String> groups = new LinkedHashSet<>();
        for (Map.Entry<String, List<ShadePlanEntry>> candidates : candidatesByPath.entrySet()) {
            Set<String> jarsOfGroup = new LinkedHashSet<>();
            for (ShadePlanEntry candidate : candidates.getValue()) {
                jarsOfGroup.add(candidate.getJar());
            }
            if (jarsOfGroup.size() > 1) {
                groups.add(candidates.getKey());
            }
        }

        List<ShadePlanEntry> resolved = new ArrayList<>(builder.entries.size());
        for (ShadePlanEntry entry : builder.entries) {
            resolved.add(groups.contains(entry.getPath()) ? entry.withDuplicateGroup(entry.getPath()) : entry);
        }
        resolved.sort(ENTRY_ORDER);
        this.entries = Collections.unmodifiableList(resolved);
    }

    /**
     * @return paths of the input JARs in the order they are processed
     */
    public List<String> getJars() {
        return jars;
    }

    /**
     * @return the planned entries, sorted by input JAR path and original entry path
     */
    public List<ShadePlanEntry> getEntries() {
        return entries;
    }

    /**
     * @return {@code true} when every input entry could be planned, {@code false} when the plan aborted
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * @return the input JAR whose processing failed, or {@code null} when the plan is complete
     */
    public String getFailureJar() {
        return failureJar;
    }

    /**
     * @return the entry whose processing failed, or {@code null} when the failure is not tied to one entry
     */
    public String getFailureEntry() {
        return failureEntry;
    }

    /**
     * @return description of the failure, or {@code null} when the plan is complete
     */
    public String getFailureMessage() {
        return failureMessage;
    }

    /**
     * Serializes this plan to JSON with a fixed schema: keys are emitted in a fixed order and entries are
     * sorted, so the output is stable for a given request.
     *
     * @return this plan as a JSON document
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": ").append(SCHEMA_VERSION).append(",\n");
        json.append("  \"complete\": ").append(complete).append(",\n");
        json.append("  \"failure\": ");
        if (failureMessage == null) {
            json.append("null");
        } else {
            json.append("{\n");
            json.append("    \"jar\": ").append(jsonString(failureJar)).append(",\n");
            json.append("    \"entry\": ").append(jsonString(failureEntry)).append(",\n");
            json.append("    \"message\": ").append(jsonString(failureMessage)).append('\n');
            json.append("  }");
        }
        json.append(",\n");
        json.append("  \"jars\": [");
        if (!jars.isEmpty()) {
            json.append('\n');
            for (int i = 0; i < jars.size(); i++) {
                json.append("    ").append(jsonString(jars.get(i)));
                json.append(i + 1 < jars.size() ? ",\n" : "\n");
            }
            json.append("  ");
        }
        json.append("],\n");
        json.append("  \"entries\": [");
        if (!entries.isEmpty()) {
            json.append('\n');
            for (int i = 0; i < entries.size(); i++) {
                appendEntry(json, entries.get(i));
                json.append(i + 1 < entries.size() ? ",\n" : "\n");
            }
            json.append("  ");
        }
        json.append("]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendEntry(StringBuilder json, ShadePlanEntry entry) {
        json.append("    {\n");
        appendField(json, "jar", jsonString(entry.getJar()));
        appendField(json, "path", jsonString(entry.getPath()));
        appendField(json, "kind", jsonString(entry.getKind().name()));
        appendField(json, "outcome", jsonString(entry.getOutcome().name()));
        appendField(json, "filter", jsonString(entry.getFilter()));
        appendField(json, "excluded", jsonString(entry.getExcluded()));
        appendField(json, "relocatedPath", jsonString(entry.getRelocatedPath()));
        appendField(json, "finalPath", jsonString(entry.getFinalPath()));
        appendField(json, "transformer", jsonString(entry.getTransformer()));
        StringBuilder transformerPlan = new StringBuilder();
        if (entry.getTransformerPlan().isEmpty()) {
            transformerPlan.append("null");
        } else {
            transformerPlan.append("{\n");
            int i = 0;
            for (Map.Entry<String, String> attribute :
                    entry.getTransformerPlan().entrySet()) {
                transformerPlan
                        .append("        ")
                        .append(jsonString(attribute.getKey()))
                        .append(": ")
                        .append(jsonString(attribute.getValue()));
                transformerPlan.append(++i < entry.getTransformerPlan().size() ? ",\n" : "\n");
            }
            transformerPlan.append("      }");
        }
        appendField(json, "transformerPlan", transformerPlan);
        appendField(json, "duplicateGroup", jsonString(entry.getDuplicateGroup()));
        appendField(json, "winner", Boolean.toString(entry.isWinner()));
        appendField(json, "compression", jsonString(entry.getCompression()));
        appendField(json, "timestampPolicy", jsonString(entry.getTimestampPolicy()));
        json.append("      \"time\": ").append(entry.getTime()).append('\n');
        json.append("    }");
    }

    private static void appendField(StringBuilder json, String name, CharSequence value) {
        json.append("      \"").append(name).append("\": ").append(value).append(",\n");
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    /**
     * @return a new builder for a {@link ShadePlan}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ShadePlan}.
     */
    public static final class Builder {
        private final List<String> jars = new ArrayList<>();

        private final List<ShadePlanEntry> entries = new ArrayList<>();

        private String failureJar;

        private String failureEntry;

        private String failureMessage;

        private Builder() {}

        public Builder addJar(String jar) {
            this.jars.add(jar);
            return this;
        }

        public Builder addEntry(ShadePlanEntry entry) {
            this.entries.add(entry);
            return this;
        }

        /**
         * Marks the plan as incomplete.
         *
         * @param jar the input JAR whose processing failed
         * @param entry the entry whose processing failed, or {@code null} when the failure is not tied to one entry
         * @param message description of the failure
         * @return this builder
         */
        public Builder incomplete(String jar, String entry, String message) {
            this.failureJar = jar;
            this.failureEntry = entry;
            this.failureMessage = message;
            return this;
        }

        public ShadePlan build() {
            return new ShadePlan(this);
        }
    }
}
