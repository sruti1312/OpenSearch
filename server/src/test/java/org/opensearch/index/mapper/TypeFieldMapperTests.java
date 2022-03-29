/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.index.mapper;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.IndexService;
import org.opensearch.index.fielddata.IndexFieldDataCache;
import org.opensearch.index.fielddata.IndexOrdinalsFieldData;
import org.opensearch.index.fielddata.LeafOrdinalsFieldData;
import org.opensearch.index.mapper.MapperService.MergeReason;
import org.opensearch.indices.breaker.NoneCircuitBreakerService;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchSingleNodeTestCase;
import org.opensearch.test.InternalSettingsPlugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class TypeFieldMapperTests extends OpenSearchSingleNodeTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return pluginList(InternalSettingsPlugin.class);
    }

    public void testDocValuesSingleType() throws Exception {
        testDocValues(this::createIndex);
        assertWarnings("[types removal] Using the _type field in queries and aggregations is deprecated, prefer to use a field instead.");
    }

    public static void testDocValues(Function<String, IndexService> createIndex) throws IOException {
        MapperService mapperService = createIndex.apply("test").mapperService();
        DocumentMapper mapper = mapperService.merge("type", new CompressedXContent("{\"type\":{}}"), MergeReason.MAPPING_UPDATE);
        ParsedDocument document = mapper.parse(new SourceToParse("index", "id", new BytesArray("{}"), XContentType.JSON));

        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
        w.addDocument(document.rootDoc());
        DirectoryReader r = DirectoryReader.open(w);
        w.close();

        MappedFieldType ft = mapperService.fieldType(TypeFieldMapper.NAME);
        IndexOrdinalsFieldData fd = (IndexOrdinalsFieldData) ft.fielddataBuilder(
            "test",
            () -> { throw new UnsupportedOperationException(); }
        ).build(new IndexFieldDataCache.None(), new NoneCircuitBreakerService());
        LeafOrdinalsFieldData afd = fd.load(r.leaves().get(0));
        SortedSetDocValues values = afd.getOrdinalsValues();
        assertTrue(values.advanceExact(0));
        assertEquals(0, values.nextOrd());
        assertEquals(SortedSetDocValues.NO_MORE_ORDS, values.nextOrd());
        assertEquals(new BytesRef("type"), values.lookupOrd(0));
        r.close();
        dir.close();
    }

    public void testDefaults() throws IOException {
        Settings indexSettings = Settings.EMPTY;
        MapperService mapperService = createIndex("test", indexSettings).mapperService();
        DocumentMapper mapper = mapperService.merge("type", new CompressedXContent("{\"type\":{}}"), MergeReason.MAPPING_UPDATE);
        ParsedDocument document = mapper.parse(new SourceToParse("index", "id", new BytesArray("{}"), XContentType.JSON));
        assertEquals(Collections.<IndexableField>emptyList(), Arrays.asList(document.rootDoc().getFields(TypeFieldMapper.NAME)));
    }
}
