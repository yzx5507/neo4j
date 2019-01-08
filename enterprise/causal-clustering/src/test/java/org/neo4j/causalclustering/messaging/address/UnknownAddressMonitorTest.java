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
package org.neo4j.causalclustering.messaging.address;

import org.junit.Test;

import org.neo4j.causalclustering.identity.MemberId;
import org.neo4j.logging.Log;
import org.neo4j.time.Clocks;
import org.neo4j.time.FakeClock;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.neo4j.causalclustering.identity.RaftTestMember.member;

public class UnknownAddressMonitorTest
{
    @Test
    public void shouldLogFirstFailure() throws Exception
    {
        // given
        Log log = mock( Log.class );
        UnknownAddressMonitor logger = new UnknownAddressMonitor( log, testClock(), 100 );

        // when
        MemberId to = member( 0 );
        logger.logAttemptToSendToMemberWithNoKnownAddress( to );

        // then
        verify( log ).info( format( "No address found for %s, probably because the member has been shut down.", to ) );
    }

    private FakeClock testClock()
    {
        return Clocks.fakeClock( 1_000_000, MILLISECONDS );
    }

    @Test
    public void shouldThrottleLogging() throws Exception
    {
        // given
        Log log = mock( Log.class );
        FakeClock clock = testClock();
        UnknownAddressMonitor logger = new UnknownAddressMonitor( log, clock, 1000 );
        MemberId to = member( 0 );

        // when
        logger.logAttemptToSendToMemberWithNoKnownAddress( to );
        clock.forward( 1, MILLISECONDS );
        logger.logAttemptToSendToMemberWithNoKnownAddress( to );

        // then
        verify( log, times( 1 ) )
                .info( format( "No address found for %s, probably because the member has been shut " + "down.", to ) );
    }

    @Test
    public void shouldResumeLoggingAfterQuietPeriod() throws Exception
    {
        // given
        Log log = mock( Log.class );
        FakeClock clock = testClock();
        UnknownAddressMonitor logger = new UnknownAddressMonitor( log, clock, 1000 );
        MemberId to = member( 0 );

        // when
        logger.logAttemptToSendToMemberWithNoKnownAddress( to );
        clock.forward( 20001, MILLISECONDS );
        logger.logAttemptToSendToMemberWithNoKnownAddress( to );
        clock.forward( 80001, MILLISECONDS );
        logger.logAttemptToSendToMemberWithNoKnownAddress( to );

        // then
        verify( log, times( 3 ) )
                .info( format( "No address found for %s, probably because the member has been shut " + "down.", to ) );
    }
}