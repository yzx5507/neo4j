/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.test.assertion;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.neo4j.function.ThrowingAction;
import org.neo4j.function.ThrowingSupplier;
import org.neo4j.internal.helpers.ArrayUtil;
import org.neo4j.internal.helpers.Strings;

import static org.hamcrest.Matchers.containsString;

public final class Assert
{
    private Assert()
    {
    }

    public static <E extends Exception> void assertException( ThrowingAction<E> f, Class<?> typeOfException )
    {
        assertException( f, typeOfException, (Matcher<String>) null );
    }

    public static <E extends Exception> void assertException( ThrowingAction<E> f, Class<?> typeOfException,
            String partOfMessage )
    {
        assertException( f, typeOfException, partOfMessage == null ? null : containsString( partOfMessage ) );
    }

    public static <E extends Exception> void assertException( ThrowingAction<E> f, Class<?> typeOfException,
            Matcher<String> messageMatcher )
    {
        try
        {
            f.apply();
            throw new AssertionError( "Expected exception of type " + typeOfException + ", but no exception was thrown" );
        }
        catch ( Exception e )
        {
            if ( typeOfException.isInstance( e ) )
            {
                if ( messageMatcher != null )
                {
                    String message = e.getMessage();
                    if ( !messageMatcher.matches( message ) )
                    {
                        StringBuilder sb = new StringBuilder();
                        Description description = new StringDescription( sb );
                        messageMatcher.describeTo( description );
                        description.appendText( System.lineSeparator() );
                        description.appendText( "but " );
                        messageMatcher.describeMismatch( message, description );
                        throw new AssertionError( "Exception (attached as cause) did not match expected message '" + sb + "'.", e );
                    }
                }
            }
            else
            {
                throw new AssertionError( "Expected exception of type '" + typeOfException + "', but instead got the attached cause.", e );
            }
        }
    }

    public static void assertObjectOrArrayEquals( Object expected, Object actual )
    {
        assertObjectOrArrayEquals( "", expected, actual );
    }

    public static void assertObjectOrArrayEquals( String message, Object expected, Object actual )
    {
        if ( expected.getClass().isArray() )
        {
            if ( !ArrayUtil.equals( expected, actual ) )
            {
                throw newAssertionError( message, expected, actual );
            }
        }
        else
        {
            if ( !Objects.equals( expected, actual ) )
            {
                throw newAssertionError( message, expected, actual );
            }
        }
    }

    public static <T, E extends Exception> void assertEventually(
            ThrowingSupplier<T, E> actual, Matcher<? super T> matcher, long timeout, TimeUnit timeUnit
    ) throws E, InterruptedException
    {
        assertEventually( ignored -> "", actual, matcher, timeout, timeUnit );
    }

    public static <T, E extends Exception> void assertEventually(
            String reason, ThrowingSupplier<T, E> actual, Matcher<? super T> matcher, long timeout, TimeUnit timeUnit
    ) throws E, InterruptedException
    {
        assertEventually( ignored -> reason, actual, matcher, timeout, timeUnit );
    }

    public static <T, E extends Exception> void assertEventually(
            Function<T, String> reason, ThrowingSupplier<T, E> actual, Matcher<? super T> matcher, long timeout, TimeUnit timeUnit
    ) throws E, InterruptedException
    {
        long endTimeMillis = System.currentTimeMillis() + timeUnit.toMillis( timeout );

        T last;
        boolean matched;

        do
        {
            long sampleTime = System.currentTimeMillis();

            last = actual.get();
            matched = matcher.matches( last );

            if ( matched || sampleTime > endTimeMillis )
            {
                break;
            }

            Thread.sleep( 10 );
        } while ( true );

        if ( !matched )
        {
            Description description = new StringDescription();
            description.appendText( reason.apply( last ) )
                    .appendText( "\nExpected: " )
                    .appendDescriptionOf( matcher )
                    .appendText( "\n     but: " );
            matcher.describeMismatch( last, description );

            throw new AssertionError( "Timeout hit (" + timeout + " " + timeUnit.toString().toLowerCase() +
                    ") while waiting for condition to match: " + description.toString() );
        }
    }

    private static AssertionError newAssertionError( String message, Object expected, Object actual )
    {
        return new AssertionError( ((message == null || message.isEmpty()) ? "" : message + "\n") +
                                   "Expected: " + Strings.prettyPrint( expected ) +
                                   ", actual: " + Strings.prettyPrint( actual ) );
    }
}
