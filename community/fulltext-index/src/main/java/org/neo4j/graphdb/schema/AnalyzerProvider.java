/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.graphdb.schema;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopwordAnalyzerBase;

import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.annotations.api.PublicApi;
import org.neo4j.annotations.service.Service;
import org.neo4j.service.NamedService;

import static java.util.Objects.requireNonNull;

/**
 * This is the base-class for all service-loadable factory classes, that build the Lucene Analyzer instances that are available to the fulltext schema index.
 * The analyzer factory is referenced in the index configuration via its {@code analyzerName} and {@code alternativeNames} that are specific to the constructor
 * of this base class. Sub-classes must have a public no-arg constructor such that they can be service-loaded.
 * <p>
 * Here is an example that implements an analyzer provider for the {@code SwedishAnalyzer} that comes built into Lucene:
 *
 * <pre><code>
 * public class Swedish extends AnalyzerProvider
 * {
 *     public Swedish()
 *     {
 *         super( "swedish" );
 *     }
 *
 *     public Analyzer createAnalyzer()
 *     {
 *         return new SwedishAnalyzer();
 *     }
 * }
 * </code></pre>
 * <p>
 * The {@code jar} that includes this implementation must also contain a {@code META-INF/services/org.neo4j.graphdb.index.fulltext.AnalyzerProvider} file,
 * that contains the fully-qualified class names of all of the {@code AnalyzerProvider} implementations it contains.
 */
@Service
@PublicApi
public abstract class AnalyzerProvider implements NamedService
{
    private final String name;

    /**
     * Sub-classes MUST have a public no-arg constructor, and must call this super-constructor with the names it uses to identify itself.
     * <p>
     * Sub-classes should strive to make these names unique.
     * If the names are not unique among all analyzer providers on the class path, then the indexes may fail to load the correct analyzers that they are
     * configured with.
     *
     * @param name The name of this analyzer provider, which will be used for analyzer settings values for identifying which implementation to use.
     */
    protected AnalyzerProvider( String name )
    {
        this.name = requireNonNull( name );
    }

    @Override
    public String getName()
    {
        return name;
    }

    /**
     * @return A newly constructed {@code Analyzer} instance.
     */
    public abstract Analyzer createAnalyzer();

    /**
     * @return A description of this analyzer.
     */
    public String description()
    {
        return "";
    }

    public List<String> stopwords()
    {
        Analyzer analyzer = createAnalyzer();
        if ( analyzer instanceof StopwordAnalyzerBase )
        {
            StopwordAnalyzerBase stopwordAnalyzer = (StopwordAnalyzerBase) analyzer;
            CharArraySet stopwords = stopwordAnalyzer.getStopwordSet();
            return stopwords.stream().map( obj -> new String( (char[]) obj ) ).collect( Collectors.toList() );
        }
        return List.of();
    }
}
