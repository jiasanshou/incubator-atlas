/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.store.graph.v1;

import com.google.inject.Inject;
import org.apache.atlas.AtlasErrorCode;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.typedef.AtlasEntityDef;
import org.apache.atlas.repository.Constants;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.repository.store.graph.AtlasEntityDefStore;
import org.apache.atlas.type.AtlasEntityType;
import org.apache.atlas.type.AtlasType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.atlas.typesystem.types.DataTypes.TypeCategory;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * EntityDef store in v1 format.
 */
public class AtlasEntityDefStoreV1 extends AtlasAbstractDefStoreV1 implements AtlasEntityDefStore {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasEntityDefStoreV1.class);

    @Inject
    public AtlasEntityDefStoreV1(AtlasTypeDefGraphStoreV1 typeDefStore, AtlasTypeRegistry typeRegistry) {
        super(typeDefStore, typeRegistry);
    }

    @Override
    public AtlasVertex preCreate(AtlasEntityDef entityDef) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.preCreate({})", entityDef);
        }

        validateType(entityDef);

        AtlasType type = typeRegistry.getType(entityDef.getName());

        if (type.getTypeCategory() != org.apache.atlas.model.TypeCategory.ENTITY) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_MATCH_FAILED, entityDef.getName(), TypeCategory.CLASS.name());
        }

        AtlasVertex ret = typeDefStore.findTypeVertexByName(entityDef.getName());

        if (ret != null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_ALREADY_EXISTS, entityDef.getName());
        }

        ret = typeDefStore.createTypeVertex(entityDef);

        updateVertexPreCreate(entityDef, (AtlasEntityType)type, ret);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.preCreate({}): {}", entityDef, ret);
        }

        return ret;
    }

    @Override
    public AtlasEntityDef create(AtlasEntityDef entityDef, Object preCreateResult) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.create({}, {})", entityDef, preCreateResult);
        }

        AtlasVertex vertex;

        if (preCreateResult == null || !(preCreateResult instanceof AtlasVertex)) {
            vertex = preCreate(entityDef);
        } else {
            vertex = (AtlasVertex)preCreateResult;
        }

        updateVertexAddReferences(entityDef, vertex);

        AtlasEntityDef ret = toEntityDef(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.create({}, {}): {}", entityDef, preCreateResult, ret);
        }

        return ret;
    }

    @Override
    public List<AtlasEntityDef> getAll() throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.getAll()");
        }

        List<AtlasEntityDef>  ret      = new ArrayList<>();
        Iterator<AtlasVertex> vertices = typeDefStore.findTypeVerticesByCategory(TypeCategory.CLASS);

        while (vertices.hasNext()) {
            ret.add(toEntityDef(vertices.next()));
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.getAll(): count={}", ret.size());
        }

        return ret;
    }

    @Override
    public AtlasEntityDef getByName(String name) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.getByName({})", name);
        }

        AtlasVertex vertex = typeDefStore.findTypeVertexByNameAndCategory(name, TypeCategory.CLASS);

        if (vertex == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_NAME_NOT_FOUND, name);
        }

        vertex.getProperty(Constants.TYPE_CATEGORY_PROPERTY_KEY, TypeCategory.class);

        AtlasEntityDef ret = toEntityDef(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.getByName({}): {}", name, ret);
        }

        return ret;
    }

    @Override
    public AtlasEntityDef getByGuid(String guid) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.getByGuid({})", guid);
        }

        AtlasVertex vertex = typeDefStore.findTypeVertexByGuidAndCategory(guid, TypeCategory.CLASS);

        if (vertex == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_GUID_NOT_FOUND, guid);
        }

        AtlasEntityDef ret = toEntityDef(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.getByGuid({}): {}", guid, ret);
        }

        return ret;
    }

    @Override
    public AtlasEntityDef update(AtlasEntityDef entityDef) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.update({})", entityDef);
        }

        validateType(entityDef);

        AtlasEntityDef ret = StringUtils.isNotBlank(entityDef.getGuid()) ? updateByGuid(entityDef.getGuid(), entityDef)
                                                                         : updateByName(entityDef.getName(), entityDef);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.update({}): {}", entityDef, ret);
        }

        return ret;
    }

    @Override
    public AtlasEntityDef updateByName(String name, AtlasEntityDef entityDef) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.updateByName({}, {})", name, entityDef);
        }

        validateType(entityDef);

        AtlasType type = typeRegistry.getType(entityDef.getName());

        if (type.getTypeCategory() != org.apache.atlas.model.TypeCategory.ENTITY) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_MATCH_FAILED, entityDef.getName(), TypeCategory.CLASS.name());
        }

        AtlasVertex vertex = typeDefStore.findTypeVertexByNameAndCategory(name, TypeCategory.CLASS);

        if (vertex == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_NAME_NOT_FOUND, name);
        }

        updateVertexPreUpdate(entityDef, (AtlasEntityType)type, vertex);
        updateVertexAddReferences(entityDef, vertex);

        AtlasEntityDef ret = toEntityDef(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.updateByName({}, {}): {}", name, entityDef, ret);
        }

        return ret;
    }

    @Override
    public AtlasEntityDef updateByGuid(String guid, AtlasEntityDef entityDef) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.updateByGuid({})", guid);
        }

        validateType(entityDef);

        AtlasType type = typeRegistry.getTypeByGuid(guid);

        if (type.getTypeCategory() != org.apache.atlas.model.TypeCategory.ENTITY) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_MATCH_FAILED, entityDef.getName(), TypeCategory.CLASS.name());
        }

        AtlasVertex vertex = typeDefStore.findTypeVertexByGuidAndCategory(guid, TypeCategory.CLASS);

        if (vertex == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_GUID_NOT_FOUND, guid);
        }

        updateVertexPreUpdate(entityDef, (AtlasEntityType)type, vertex);
        updateVertexAddReferences(entityDef, vertex);

        AtlasEntityDef ret = toEntityDef(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.updateByGuid({}): {}", guid, ret);
        }

        return ret;
    }

    @Override
    public AtlasVertex preDeleteByName(String name) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.preDeleteByName({})", name);
        }

        AtlasVertex ret = typeDefStore.findTypeVertexByNameAndCategory(name, TypeCategory.CLASS);

        if (AtlasGraphUtilsV1.typeHasInstanceVertex(name)) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_HAS_REFERENCES, name);
        }

        if (ret == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_NAME_NOT_FOUND, name);
        }

        typeDefStore.deleteTypeVertexOutEdges(ret);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.preDeleteByName({}): {}", name, ret);
        }

        return ret;
    }

    @Override
    public void deleteByName(String name, Object preDeleteResult) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.deleteByName({}, {})", name, preDeleteResult);
        }

        AtlasVertex vertex;

        if (preDeleteResult == null || !(preDeleteResult instanceof AtlasVertex)) {
            vertex = preDeleteByName(name);
        } else {
            vertex = (AtlasVertex)preDeleteResult;
        }

        typeDefStore.deleteTypeVertex(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.deleteByName({}, {})", name, preDeleteResult);
        }
    }

    @Override
    public AtlasVertex preDeleteByGuid(String guid) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.preDeleteByGuid({})", guid);
        }

        AtlasVertex ret = typeDefStore.findTypeVertexByGuidAndCategory(guid, TypeCategory.CLASS);

        String typeName = AtlasGraphUtilsV1.getProperty(ret, Constants.TYPENAME_PROPERTY_KEY, String.class);

        if (AtlasGraphUtilsV1.typeHasInstanceVertex(typeName)) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_HAS_REFERENCES, typeName);
        }

        if (ret == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_GUID_NOT_FOUND, guid);
        }

        typeDefStore.deleteTypeVertexOutEdges(ret);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.preDeleteByGuid({}): {}", guid, ret);
        }

        return ret;
    }

    @Override
    public void deleteByGuid(String guid, Object preDeleteResult) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> AtlasEntityDefStoreV1.deleteByGuid({}, {})", guid, preDeleteResult);
        }

        AtlasVertex vertex;

        if (preDeleteResult == null || !(preDeleteResult instanceof AtlasVertex)) {
            vertex = preDeleteByGuid(guid);
        } else {
            vertex = (AtlasVertex)preDeleteResult;
        }

        typeDefStore.deleteTypeVertex(vertex);

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== AtlasEntityDefStoreV1.deleteByGuid({}, {})", guid, preDeleteResult);
        }
    }

    private void updateVertexPreCreate(AtlasEntityDef entityDef, AtlasEntityType entityType, AtlasVertex vertex) throws AtlasBaseException {
        AtlasStructDefStoreV1.updateVertexPreCreate(entityDef, entityType, vertex, typeDefStore);
    }

    private void updateVertexPreUpdate(AtlasEntityDef entityDef, AtlasEntityType entityType, AtlasVertex vertex)
        throws AtlasBaseException {
        AtlasStructDefStoreV1.updateVertexPreUpdate(entityDef, entityType, vertex, typeDefStore);
    }

    private void updateVertexAddReferences(AtlasEntityDef  entityDef, AtlasVertex vertex) throws AtlasBaseException {
        AtlasStructDefStoreV1.updateVertexAddReferences(entityDef, vertex, typeDefStore);

        typeDefStore.createSuperTypeEdges(vertex, entityDef.getSuperTypes(), TypeCategory.CLASS);
    }

    private AtlasEntityDef toEntityDef(AtlasVertex vertex) throws AtlasBaseException {
        AtlasEntityDef ret = null;

        if (vertex != null && typeDefStore.isTypeVertex(vertex, TypeCategory.CLASS)) {
            ret = new AtlasEntityDef();

            AtlasStructDefStoreV1.toStructDef(vertex, ret, typeDefStore);

            ret.setSuperTypes(typeDefStore.getSuperTypeNames(vertex));
        }

        return ret;
    }
}
