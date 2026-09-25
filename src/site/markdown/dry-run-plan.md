---
title: Dry-Run Shade Plan
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Dry-Run Shade Plan

For build audits it is often necessary to know *what would happen* during shading without
actually producing the uber JAR: where does every entry of every input artifact end up after
filters, relocations and resource transformers, and who wins when several artifacts contribute
the same entry?

Setting `-Dshade.dryRun=true` answers exactly that. The plugin computes the plan of the shading
for the main shaded artifact with the same decision logic as the real shading and writes it as
JSON, by default to `${project.build.directory}/shade-plan.json` (configurable via the
`shadePlanFile` parameter / `-Dshade.planFile`).

```shell
mvn package -Dshade.dryRun=true
```

A dry run never creates the output JAR and never lets resource transformers finish (or otherwise
mutate) the output. Transformers are only asked for a read-only plan contribution, so a real
shading in a later execution is not affected by the dry run.

## Winners and stable fields

Input artifacts are processed in the order they are listed in the plan's `jars` array. When
several entries claim the same output path, they form a duplicate group and **the entry from the
artifact that comes first in processing order wins**; the other entries of the group are dropped.
Entries consumed by a resource transformer are not part of a duplicate group: the transformer
decides how to merge them.

Reordering the input artifacts only changes the `jars` array, the membership of duplicate groups
and the `winner` flags. Every other per-entry field (`kind`, `disposition`, `filteredBy`,
`excludedReason`, `output`, `transformer`, `transformerDetail`, `compression`,
`timestampPolicy`) is stable and does not depend on the processing order.

## Plan format

The JSON document has a fixed schema (version `1`) and a deterministic ordering: entries are
sorted by input artifact and original entry path, and all derived lists are sorted
lexicographically, so two runs over the same inputs produce byte-identical documents.

- `schemaVersion`: version of the plan schema, currently `1`.
- `status`: `COMPLETE` when every input entry was planned, `INCOMPLETE` otherwise.
- `failure`: for an incomplete plan, the `jar`, the `entry` (if any) and the `error` where
  planning stopped, e.g. an illegal class file or a transformer that failed to describe its
  plan contribution. `null` for a complete plan.
- `jars`: the input artifacts in processing order.
- `entries`: one object per input entry with:
  - `jar`, `source`: the input artifact and the original entry path.
  - `kind`: `DIRECTORY`, `MANIFEST`, `SERVICE`, `SIGNATURE`, `MODULE_INFO`, `MULTI_RELEASE`,
    `CLASS`, `JAVA_SOURCE` or `RESOURCE`.
  - `disposition`: `WRITTEN`, `DUPLICATE`, `TRANSFORMED`, `FILTERED`, `EXCLUDED` or `SKIPPED`.
  - `filteredBy`: the filter that removed the entry, if any.
  - `excludedReason`: why the shader itself dropped the entry (e.g. `module-info.class`), if any.
  - `output`: the final path in the output JAR after relocation.
  - `transformer` / `transformerDetail`: the resource transformer consuming the entry and its
    read-only plan contribution, if any.
  - `duplicateGroup` / `winner`: the shared output path and whether this entry wins it.
  - `compression`: `STORED`, `DEFLATED` or `TRANSFORMER_DEFINED` for the output entry.
  - `timestampPolicy`: `SOURCE_ENTRY_TIME` or `TRANSFORMER_DEFINED` for the output entry.
- `outputDirectories`: the directory entries the shading synthesizes in the output JAR.
- `transformerOutputs`: the entry paths resource transformers declared they will produce when
  finishing the output.

Note that the plan describes entry names, dispositions and policies - never the compressed bytes
of the output JAR, which are not part of the contract.
