/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kiji.scoring;

import java.io.IOException;
import java.util.List;

import org.kiji.annotations.ApiAudience;
import org.kiji.annotations.ApiStability;
import org.kiji.annotations.Inheritance;
import org.kiji.schema.EntityId;
import org.kiji.schema.KijiColumnName;
import org.kiji.schema.KijiDataRequest;
import org.kiji.schema.KijiRowData;
import org.kiji.schema.KijiTableReader;
import org.kiji.scoring.statistics.FreshKijiTableReaderStatistics;

/**
 * Interface for reading freshened data from a Kiji Table.
 *
 * <p>
 *   Utilizes {@link org.kiji.schema.EntityId} and {@link org.kiji.schema.KijiDataRequest}
 *   to return {@link org.kiji.schema.KijiRowData}.
 * </p>
 * <p>
 *   Accessible via {@link org.kiji.scoring.FreshKijiTableReaderBuilder#create()}.
 * </p>
 *
 * <p>
 *   Reads performed with FreshKijiTableReaders pass through freshness filters according to
 *   {@link org.kiji.scoring.KijiFreshnessPolicy}s registered in the
 *   {@link org.kiji.schema.KijiMetaTable} that services the table associated with this reader.
 * </p>
 *
 * <p>
 *   Freshening describes the process of conditionally applying a {@link ScoreFunction} to a row in
 *   response to user queries for data in that row.  Consequently, methods of a FreshKijiTableReader
 *   have the possibility of generating side effect writes to the rows users query.
 * </p>
 *
 * <p>
 *   FreshKijiTableReader get methods are used in the same way as regular KijiTableReader get
 *   methods.
 * </p>
 * <p>
 *   To get the three most recent versions of cell data from a column <code>bar</code> from
 *   the family <code>foo</code>:
 * <pre>
 *   KijiDataRequestBuilder builder = KijiDataRequest.builder()
 *     .newColumnsDef()
 *     .withMaxVersions(3)
 *     .add("foo", "bar");
 *   final KijiDataRequest request = builder.build();
 *
 *   final KijiTableReader freshReader = FreshKijiTableReaderBuilder.create()
 *       .withTable(table)
 *       .withTimeout(100)
 *       .build();
 *   final KijiRowData data = freshReader.get(myEntityId, request);
 * </pre>
 *   This code will return the three most recent values including newly generated values output by
 *   the ScoreFunction if it ran.
 * </p>
 *
 * <p>
 *   Instances of this reader are thread safe and may be used across multiple threads. Because this
 *   class maintains a connection to the underlying KijiTable and other resources, users should call
 *   {@link #close()} when done using a reader.
 * </p>
 *
 * @see org.kiji.scoring.KijiFreshnessPolicy
 * @see org.kiji.scoring.ScoreFunction
 */
@ApiAudience.Public
@ApiStability.Experimental
@Inheritance.Sealed
public interface FreshKijiTableReader extends KijiTableReader {

  /**
   * Freshens data as needed before returning. If freshening has not completed within the
   * configured timeout, will return stale or partially freshened data depending on the
   * configuration of the reader.  Behaves the same as
   * {@link org.kiji.schema.KijiTableReader#get(org.kiji.schema.EntityId,
   * org.kiji.schema.KijiDataRequest)} except for the possibility of freshening.
   *
   * @param entityId EntityId of the row to query.
   * @param dataRequest What data to retrieve.
   * @return The data requested after freshening.
   * @throws IOException in case of an error reading from the table.
   */
  @Override
  KijiRowData get(EntityId entityId, KijiDataRequest dataRequest) throws IOException;

  /**
   * Freshens data as needed before returning. If freshening has not completed within the specified
   * timeout, will return stale or partially freshened data depending on the configuration of the
   * reader.  Behaves the same as
   * {@link org.kiji.schema.KijiTableReader#get(org.kiji.schema.EntityId,
   * org.kiji.schema.KijiDataRequest)} except for the possibility of freshening.
   *
   * @param entityId the EntityId of the row to query.
   * @param dataRequest what data to retrieve.
   * @param timeout how long (in milliseconds) to wait before returning stale or partially fresh
   * data.
   * @return the data requested after freshening.
   * @throws IOException in case of an error reading from the table.
   */
  KijiRowData get(EntityId entityId, KijiDataRequest dataRequest, long timeout) throws IOException;

  /**
   * Attempts to freshen all data requested in parallel before returning. If freshening has not
   * completed within the configured timeout, will return stale or partially freshened data
   * depending on the configuration of the reader.
   *
   * @param entityIds A list of EntityIds for the rows to query.
   * @param dataRequest What data to retrieve from each row.
   * @return a list of KijiRowData corresponding to the EntityIds and data request after
   *   freshening.
   * @throws IOException in case of an error reading from the table.
   */
  @Override
  List<KijiRowData> bulkGet(List<EntityId> entityIds, KijiDataRequest dataRequest)
      throws IOException;

  /**
   * Attempts to freshen all data requested in parallel before returning.  If freshening has not
   * completed with the specified timeout, will return stale or partially freshened data depending
   * on the configuration of the reader.
   *
   * @param entityIds a list of EntityIds for the rows to query.
   * @param dataRequest what data to retrieve from each row.
   * @param timeout the time (in milliseconds) to wait before returning stale or partially freshened
   * data.
   * @return a list of KijiRowData corresponding to the EntityIds and data request after freshening.
   * @throws IOException in case of an error reading from the table.
   */
  List<KijiRowData> bulkGet(List<EntityId> entityIds, KijiDataRequest dataRequest, long timeout)
      throws IOException;

  /**
   * Clear cached Fresheners and reload from the meta table. This method replaces only those
   * Fresheners which have changed since the last call to rereadFreshenerRecords() or the
   * construction of the reader.
   *
   * @throws IOException in case of an error reading from the meta table.
   */
  void rereadFreshenerRecords() throws IOException;

  /**
   * Clear cached Fresheners and reload from the meta table. Replaces existing list of
   * columns to freshen with the given list and instantiates any Fresheners applicable to added
   * columns. This method replaces only those Fresheners which have changed since the last call to
   * rereadFreshenerRecords or the construction of the reader.
   *
   * @param columnsToFreshen the new set of columnsToFreshen.  This list will replace the previous
   *     list permanently.
   * @throws IOException in case of an error reading from the meta table.
   */
  void rereadFreshenerRecords(List<KijiColumnName> columnsToFreshen) throws IOException;

  /**
   * Get all statistics gathered by this reader about its Fresheners.
   *
   * @return all statistics gathered by this reader about its Fresheners.
   */
  FreshKijiTableReaderStatistics getStatistics();
}
