/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.streampipes.storage.couchdb.dao;

import org.lightcouch.CouchDbClient;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class FindAllCommand<T> extends DbCommand<List<T>, T> {

  private String viewName;

  public FindAllCommand(Supplier<CouchDbClient> couchDbClient,
                        Class<T> clazz,
                        String viewName) {
    super(couchDbClient, clazz);
    this.viewName = viewName;
  }

  @Override
  protected List<T> executeCommand(CouchDbClient couchDbClient) {
    List<T> allResults = couchDbClient
            .view(viewName)
            .includeDocs(true)
            .query(clazz);

    return allResults != null ? allResults : Collections.emptyList();
  }
}
