/*
 * Copyright 2018 Google, Inc.
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

package app.tivi.data.sync

import app.tivi.data.daos.EntityDao
import app.tivi.data.entities.TiviEntity
import app.tivi.util.Logger

/**
 * @param NT Network type
 * @param ET local entity type
 * @param NID Network ID type
 */
class ItemSyncer<ET : TiviEntity, NT, NID>(
    private val entryInsertFunc: (ET) -> Long,
    private val entryUpdateFunc: (ET) -> Unit,
    private val entryDeleteFunc: (ET) -> Int,
    private val localEntityToIdFunc: (ET) -> NID,
    private val networkEntityToIdFunc: (NT) -> NID,
    private val networkEntityToLocalEntityMapperFunc: (NT, Long?) -> ET,
    private val logger: Logger? = null
) {
    fun sync(currentValues: Collection<ET>, networkValues: Collection<NT>) {
        val currentDbEntities = ArrayList(currentValues)

        networkValues.forEach { networkEntity ->
            val remoteId = networkEntityToIdFunc(networkEntity)
            val dbEntityForId = currentDbEntities.find { localEntityToIdFunc(it) == remoteId }

            if (dbEntityForId != null) {
                // This is currently in the DB, so lets merge it with the saved version and update it
                entryUpdateFunc(networkEntityToLocalEntityMapperFunc(networkEntity, dbEntityForId.id))
                logger?.d("Updated entry with remote id: $remoteId")

                // Remove it from the list so that it is not deleted
                currentDbEntities.remove(dbEntityForId)
            } else {
                // Not currently in the DB, so lets insert
                entryInsertFunc(networkEntityToLocalEntityMapperFunc(networkEntity, null))
                logger?.d("Insert entry with remote id: $remoteId")
            }
        }

        // Anything left in the set needs to be deleted from the database
        currentDbEntities.forEach {
            logger?.d("Remove entry with remote id: $it")
            entryDeleteFunc(it)
        }
    }
}

fun <ET : TiviEntity, NT, NID> syncerForEntity(
    entityDao: EntityDao<ET>,
    localEntityToIdFunc: (ET) -> NID,
    networkEntityToIdFunc: (NT) -> NID,
    networkEntityToLocalEntityMapperFunc: (NT, Long?) -> ET,
    logger: Logger? = null
) = ItemSyncer(
        entityDao::insert,
        entityDao::update,
        entityDao::delete,
        localEntityToIdFunc,
        networkEntityToIdFunc,
        networkEntityToLocalEntityMapperFunc,
        logger
)