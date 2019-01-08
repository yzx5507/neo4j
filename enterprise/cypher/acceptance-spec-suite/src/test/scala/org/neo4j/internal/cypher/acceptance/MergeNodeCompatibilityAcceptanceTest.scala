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
package org.neo4j.internal.cypher.acceptance

import org.neo4j.cypher.internal.helpers.{NodeKeyConstraintCreator, UniquenessConstraintCreator}
import org.neo4j.cypher.javacompat.internal.GraphDatabaseCypherService
import org.neo4j.cypher.{ExecutionEngineFunSuite, MergeConstraintConflictException, NewPlannerTestSupport, QueryStatisticsTestSupport}
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.config.Setting
import org.neo4j.test.TestEnterpriseGraphDatabaseFactory

import scala.collection.JavaConverters._
import scala.collection.Map

class MergeNodeCompatibilityAcceptanceTest extends ExecutionEngineFunSuite with QueryStatisticsTestSupport
  with NewPlannerTestSupport {

  override protected def createGraphDatabase(config: Map[Setting[_], String] = databaseConfig()): GraphDatabaseCypherService = {
    new GraphDatabaseCypherService(new TestEnterpriseGraphDatabaseFactory().newImpermanentDatabase(config.asJava))
  }

  Seq(UniquenessConstraintCreator, NodeKeyConstraintCreator).foreach { constraintCreator =>

    test(s"$constraintCreator: should_match_on_merge_using_multiple_unique_indexes_if_only_found_single_node_for_both_indexes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "Person", "mail")

      createLabeledNode(Map("id" -> 23, "mail" -> "emil@neo.com"), "Person")

      // when
      val results =
        updateWithCompatibilityAndAssertSimilarPlans("merge (a:Person {id: 23, mail: 'emil@neo.com'}) on match set a.country='Sweden' return a")
      val result = results.columnAs("a").next().asInstanceOf[Node]

      // then
      countNodes() should equal(1)
      graph.inTx {
        result.getProperty("id") should equal(23)
        result.getProperty("mail") should equal("emil@neo.com")
        result.getProperty("country") should equal("Sweden")
      }
    }

    test(s"$constraintCreator: should_match_on_merge_using_multiple_unique_indexes_and_labels_if_only_found_single_node_for_both_indexes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "User", "mail")

      createLabeledNode(Map("id" -> 23, "mail" -> "emil@neo.com"), "Person", "User")

      // when
      val results =
        updateWithCompatibilityAndAssertSimilarPlans("merge (a:Person:User {id: 23, mail: 'emil@neo.com'}) on match set a.country='Sweden' return a")
      val result = results.columnAs("a").next().asInstanceOf[Node]

      // then
      countNodes() should equal(1)
      graph.inTx {
        result.getProperty("id") should equal(23)
        result.getProperty("mail") should equal("emil@neo.com")
        result.getProperty("country") should equal("Sweden")
      }
    }

    test(s"$constraintCreator: should_match_on_merge_using_multiple_unique_indexes_using_same_key_if_only_found_single_node_for_both_indexes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "User", "id")

      createLabeledNode(Map("id" -> 23), "Person", "User")

      // when
      val results =
        updateWithCompatibilityAndAssertSimilarPlans("merge (a:Person:User {id: 23}) on match set a.country='Sweden' return a")
      val result = results.columnAs("a").next().asInstanceOf[Node]

      // then
      countNodes() should equal(1)
      graph.inTx {
        result.getProperty("id") should equal(23)
        result.getProperty("country") should equal("Sweden")
      }
    }

    test(s"$constraintCreator: should_fail_on_merge_using_multiple_unique_indexes_using_same_key_if_found_different_nodes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "User", "id")

      createLabeledNode(Map("id" -> 23), "Person")
      createLabeledNode(Map("id" -> 23), "User")

      // when + then
      intercept[MergeConstraintConflictException](updateWithCompatibilityAndAssertSimilarPlans("merge (a:Person:User {id: 23}) return a"))
      countNodes() should equal(2)
    }

    test(s"$constraintCreator: should_create_on_merge_using_multiple_unique_indexes_if_found_no_nodes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "Person", "mail")

      // when
      val results =
        updateWithCompatibilityAndAssertSimilarPlans("merge (a:Person {id: 23, mail: 'emil@neo.com'}) on create set a.country='Sweden' return a")
      val result = results.columnAs("a").next().asInstanceOf[Node]

      // then
      countNodes() should equal(1)
      labels(result) should equal(Set("Person"))
      graph.inTx {
        result.getProperty("id") should equal(23)
        result.getProperty("country") should equal("Sweden")
        result.getProperty("mail") should equal("emil@neo.com")
      }
    }

    test(s"$constraintCreator: should_create_on_merge_using_multiple_unique_indexes_and_labels_if_found_no_nodes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "User", "mail")

      // when
      val results =
        updateWithCompatibilityAndAssertSimilarPlans("merge (a:Person:User {id: 23, mail: 'emil@neo.com'}) on create set a.country='Sweden' return a")
      val result = results.columnAs("a").next().asInstanceOf[Node]

      // then
      countNodes() should equal(1)
      labels(result) should equal(Set("Person", "User"))
      graph.inTx {
        result.getProperty("id") should equal(23)
        result.getProperty("country") should equal("Sweden")
        result.getProperty("mail") should equal("emil@neo.com")
      }
    }

    test(s"$constraintCreator: should_fail_on_merge_using_multiple_unique_indexes_if_found_different_nodes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "Person", "mail")

      createLabeledNode(Map("id" -> 23, "mail" -> "dummy"), "Person")
      createLabeledNode(Map("id" -> -1, "mail" -> "emil@neo.com"), "Person")

      expectMergeConstraintConflictException(
        "merge (a:Person {id: 23, mail: 'emil@neo.com'}) return a", Seq(
          "Merge did not find a matching node",
          "can not create a new node due to conflicts with existing unique nodes"
        ))

      countNodes() should equal(2)
    }

    def expectMergeConstraintConflictException(query: String, messages: Seq[String]): Unit = {
      Seq("2.3", "3.1", "3.2").foreach { version =>
        val exception = intercept[MergeConstraintConflictException] {
          innerExecute(s"CYPHER $version $query")
        }
        messages.foreach { message =>
          exception.getMessage should include(message)
        }
      }
    }

    test(s"$constraintCreator: should_fail_on_merge_using_multiple_unique_indexes_if_it_found_a_node_matching_single_property_only") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "Person", "mail")

      createLabeledNode(Map("id" -> 23, "mail" -> "dummy"), "Person")

      // when + then
      expectMergeConstraintConflictException(
        "merge (a:Person {id: 23, mail: 'emil@neo.com'}) return a", Seq(
          "Merge did not find a matching node",
          "can not create a new node due to conflicts",
          "unique nodes"
        ))

      countNodes() should equal(1)
    }

    test(s"$constraintCreator: should_fail_on_merge_using_multiple_unique_indexes_and_labels_if_found_different_nodes") {
      // given
      constraintCreator.createConstraint(graph, "Person", "id")
      constraintCreator.createConstraint(graph, "User", "mail")

      createLabeledNode(Map("id" -> 23), "Person")
      createLabeledNode(Map("mail" -> "emil@neo.com"), "User")

      // when
      expectMergeConstraintConflictException(
        "merge (a:Person:User {id: 23, mail: 'emil@neo.com'}) return a", Seq(
          "Merge did not find a matching node",
          "can not create a new node due to conflicts with existing unique nodes"
        ))

      // then
      countNodes() should equal(2)
    }
  }
}