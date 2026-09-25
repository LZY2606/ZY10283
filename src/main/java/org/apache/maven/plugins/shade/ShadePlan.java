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
import java.util.TreeSet;

/**
 * The result of a dry-run shading: for every entry of every input artifact, the decision the real
 * shading would take, without writing the output JAR.
 * <p>
 * A plan is either {@link Status#COMPLETE} or {@link Status#INCOMPLETE}; in the latter case
 * {@link #getFailure()} describes where the planning stopped and the entries recorded so far are
 * an incomplete prefix of the full plan.
 *
 * @since 3.6.3
 */
public final class ShadePlan {

    /**
     * Version of the fixed JSON schema produced by {@link ShadePlanJsonWriter}.
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Whether the plan covers all input entries.
     */
    public enum Status {
        COMPLETE,
        INCOMPLETE
    }

    /**
     * Location and message of the failure that made a plan incomplete.
     */
    public static final class Failure {
        private final String jar;

        private final String entry;

        private final String error;

        Failure(String jar, String entry, String error) {
            this.jar = jar;
            this.entry = entry;
            this.error = error;
        }

        /**
         * @return path of the input artifact being processed when the failure occurred
         */
        public String getJar() {
            return jar;
        }

        /**
         * @return entry being processed when the failure occurred, or {@code null} if the artifact
         *         itself could not be read
         */
        public String getEntry() {
            return entry;
        }

        /**
         * @return description of the failure
         */
        public String getError() {
            return error;
        }
    }

    private final Status status;

    private final Failure failure;

    private final List<String> jars;

    private final List<ShadePlanEntry> entries;

    private final List<String> outputDirectories;

    private final List<String> transformerOutputs;

    private ShadePlan(Builder builder) {
        this.status = builder.status;
        this.failure = builder.failure;
        this.jars = Collections.unmodifiableList(new ArrayList<>(builder.jars));
        List<ShadePlanEntry> sorted = new ArrayList<>(builder.entries);
        Collections.sort(sorted, ENTRY_ORDER);
        this.entries = Collections.unmodifiableList(sorted);
        this.outputDirectories = sortedList(builder.outputDirectories);
        this.transformerOutputs = sortedList(builder.transformerOutputs);
    }

    private static final Comparator<ShadePlanEntry> ENTRY_ORDER = new Comparator<ShadePlanEntry>() {
        @Override
        public int compare(ShadePlanEntry left, ShadePlanEntry right) {
            int byJar = left.getJar().compareTo(right.getJar());
            return byJar != 0 ? byJar : left.getSource().compareTo(right.getSource());
        }
    };

    private static List<String> sortedList(Set<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(values)));
    }

    /**
     * @return whether the plan covers all input entries
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return {@code true} if every input entry was planned
     */
    public boolean isComplete() {
        return status == Status.COMPLETE;
    }

    /**
     * @return where and why planning stopped, or {@code null} for a complete plan
     */
    public Failure getFailure() {
        return failure;
    }

    /**
     * @return input artifacts in the order they are processed (first one wins duplicates)
     */
    public List<String> getJars() {
        return jars;
    }

    /**
     * @return per-entry decisions, sorted by input artifact and original entry path
     */
    public List<ShadePlanEntry> getEntries() {
        return entries;
    }

    /**
     * @return directory entries the shading synthesizes in the output JAR, sorted
     */
    public List<String> getOutputDirectories() {
        return outputDirectories;
    }

    /**
     * @return entry paths resource transformers declared they will produce when finishing, sorted
     */
    public List<String> getTransformerOutputs() {
        return transformerOutputs;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private Status status = Status.COMPLETE;

        private Failure failure;

        private final Set<String> jars = new LinkedHashSet<>();

        private final List<ShadePlanEntry> entries = new ArrayList<>();

        private final Set<String> outputDirectories = new TreeSet<>();

        private final Set<String> transformerOutputs = new TreeSet<>();

        private Builder() {}

        Builder addJar(String jar) {
            jars.add(jar);
            return this;
        }

        Builder addEntry(ShadePlanEntry entry) {
            entries.add(entry);
            return this;
        }

        Builder addOutputDirectory(String outputDirectory) {
            outputDirectories.add(outputDirectory);
            return this;
        }

        Builder addTransformerOutput(String transformerOutput) {
            transformerOutputs.add(transformerOutput);
            return this;
        }

        Builder incomplete(String jar, String entry, String error) {
            this.status = Status.INCOMPLETE;
            this.failure = new Failure(jar, entry, error);
            return this;
        }

        ShadePlan build() {
            resolveDuplicateGroups();
            return new ShadePlan(this);
        }

        /**
         * Entries claiming the same output path form a duplicate group; the first entry in
         * processing order (the one that gets written) wins, the others are duplicates.
         */
        private void resolveDuplicateGroups() {
            Map<String, List<Integer>> byOutput = new LinkedHashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                ShadePlanEntry entry = entries.get(i);
                if ((entry.getDisposition() == ShadePlanEntry.Disposition.WRITTEN
                                || entry.getDisposition() == ShadePlanEntry.Disposition.DUPLICATE)
                        && entry.getOutput() != null) {
                    byOutput.computeIfAbsent(entry.getOutput(), k -> new ArrayList<>())
                            .add(i);
                }
            }
            for (Map.Entry<String, List<Integer>> group : byOutput.entrySet()) {
                List<Integer> members = group.getValue();
                if (members.size() > 1) {
                    for (Integer member : members) {
                        ShadePlanEntry entry = entries.get(member);
                        entries.set(
                                member.intValue(),
                                entry.withDuplicateMembership(
                                        group.getKey(), entry.getDisposition() == ShadePlanEntry.Disposition.WRITTEN));
                    }
                }
            }
        }
    }
}
