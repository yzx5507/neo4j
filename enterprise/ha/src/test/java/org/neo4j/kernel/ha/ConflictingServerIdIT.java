/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) with the
 * Commons Clause, as found in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * Neo4j object code can be licensed independently from the source
 * under separate terms from the AGPL. Inquiries can be directed to:
 * licensing@neo4j.com
 *
 * More information is also available at:
 * https://neo4j.com/licensing/
 */
package org.neo4j.kernel.ha;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import org.neo4j.cluster.ClusterSettings;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.TestHighlyAvailableGraphDatabaseFactory;
import org.neo4j.test.rule.TestDirectory;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConflictingServerIdIT
{
    @Rule
    public final TestDirectory testDirectory = TestDirectory.testDirectory();

    @Test
    public void testConflictingIdDoesNotSilentlyFail() throws Exception
    {
        HighlyAvailableGraphDatabase master = null, dbWithId21 = null, dbWithId22 = null;
        try
        {

            GraphDatabaseBuilder masterBuilder = new TestHighlyAvailableGraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder( path( 1 ) )
                .setConfig( ClusterSettings.initial_hosts, "127.0.0.1:5002" )
                .setConfig( ClusterSettings.cluster_server, "127.0.0.1:" + ( 5001 + 1 ) )
                .setConfig( ClusterSettings.server_id, "" + 1 )
                .setConfig( HaSettings.ha_server, ":" + ( 8001 + 1 ) )
                .setConfig( HaSettings.tx_push_factor, "0" );
            master = (HighlyAvailableGraphDatabase) masterBuilder.newGraphDatabase();

            GraphDatabaseBuilder db21Builder = new TestHighlyAvailableGraphDatabaseFactory()
                    .newEmbeddedDatabaseBuilder( path( 2 ) )
                    .setConfig( ClusterSettings.initial_hosts, "127.0.0.1:5002,127.0.0.1:5003" )
                    .setConfig( ClusterSettings.cluster_server, "127.0.0.1:" + ( 5001 + 2 ) )
                    .setConfig( ClusterSettings.server_id, "" + 2 )
                    .setConfig( HaSettings.ha_server, ":" + ( 8001 + 2 ) )
                    .setConfig( HaSettings.tx_push_factor, "0" );
            dbWithId21 = (HighlyAvailableGraphDatabase) db21Builder.newGraphDatabase();

            GraphDatabaseBuilder db22Builder = new TestHighlyAvailableGraphDatabaseFactory()
                    .newEmbeddedDatabaseBuilder( path( 3 ) )
                    .setConfig( ClusterSettings.initial_hosts, "127.0.0.1:5002" )
                    .setConfig( ClusterSettings.cluster_server, "127.0.0.1:" + (5001 + 3) )
                    .setConfig( ClusterSettings.server_id, "" + 2 ) // Conflicting with the above
                    .setConfig( HaSettings.ha_server, ":" + ( 8001 + 3 ) )
                    .setConfig( HaSettings.tx_push_factor, "0" );

            try
            {
                dbWithId22 = (HighlyAvailableGraphDatabase) db22Builder.newGraphDatabase();
                fail("Should not be able to startup when a cluster already has my id");
            }
            catch ( Exception e )
            {
                // awesome
            }

            assertTrue( master.isMaster() );
            assertTrue( !dbWithId21.isMaster() );

            try ( Transaction transaction = dbWithId21.beginTx() )
            {
                transaction.success();
            }
        }
        finally
        {
            if ( dbWithId21 != null )
            {
                dbWithId21.shutdown();
            }
            if ( dbWithId22 != null )
            {
                dbWithId22.shutdown();
            }
            if ( master != null )
            {
                master.shutdown();
            }
        }
    }

    private File path( int i )
    {
        return new File( testDirectory.graphDbDir(), "" + i );
    }
}