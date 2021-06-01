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
package org.apache.streampipes.node.controller.storage;

import org.apache.streampipes.node.controller.config.NodeConfiguration;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MapDBImpl implements CRUDStorage {

    private final DB db;
    private final ConcurrentMap<String, Object> map;

    public MapDBImpl(File dbFile) {
        if("true".equals(System.getenv("SP_DEBUG"))) {
            db = DBMaker
                    .memoryDB()
                    .closeOnJvmShutdown()
                    .make();
        } else {
            db = DBMaker
                    .fileDB(NodeConfiguration.getNodeStoragePath() + dbFile)
                    .transactionEnable()
                    .closeOnJvmShutdown()
                    .make();
        }
        map = db.hashMap("nodectlcache", Serializer.STRING, Serializer.JAVA)
                .createOrOpen();
    }

    @Override
    public <T> void create(String id, T value) {
        map.put(id, value);
        db.commit();
    }

    @Override
    public <T> T retrieve(String id) {
        return (T) map.get(id);
    }

    @Override
    public <T> List<T> retrieveAll() {
        return map.values().stream().map(v -> (T) v).collect(Collectors.toList());
    }

    @Override
    public <T> void update(String id, T value) {
        map.put(id, value);
        db.commit();
    }

    @Override
    public void delete(String id) {
        map.remove(id);
        db.commit();
    }

    public void close() {
        db.close();
    }
}
