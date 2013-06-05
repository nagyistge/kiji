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

package org.kiji.express.music

import com.google.common.collect.Lists
import com.twitter.scalding.JobTest
import scala.collection.mutable.Buffer

import org.kiji.express._
import org.kiji.express.DSL._
import org.kiji.express.EntityId
import org.kiji.express.KijiSlice

class SongRecommenderSuite extends KijiSuite {

  // Get a Kiji to use for the test and record the Kiji URI of the users and songs tables we'll
  // test against.
  val kiji = makeTestKiji("SongRecommenderSuite")
  val usersURI = kiji.getURI().toString + "/users"
  val songsURI = kiji.getURI().toString + "/songs"

  // Execute the DDL shell commands in music-schema.ddl to create the tables for the music
  // tutorial.
  executeDDLResource(kiji, "org/kiji/express/music/music-schema.ddl")

  // Create some data (track plays and topSongs) for two users.

  val songFour: AvroRecord = AvroRecord("songId" -> "song-4", "count" -> 10L)
  val topSongsForSong1 = AvroRecord("topSongs" -> AvroList(List(songFour)))

  val songFive: AvroRecord = AvroRecord("songId" -> "song-5", "count" -> 9L)
  val topSongsForSong2 = AvroRecord("topSongs" -> AvroList(List(songFive)))

  val testUserInput =
    (EntityId(usersURI)("user-1"),
      slice("info:track_plays", (2L, "song-2"), (3L, "song-1"))) ::
      (EntityId(usersURI)("user-2"),
        slice("info:track_plays", (8L, "song-1"), (9L, "song-3"), (10L, "song-2"))) ::
      Nil
   val testSongInput = (EntityId(songsURI)("song-1"),
        slice("info:top_next_songs", (1L, topSongsForSong1))) ::
      (EntityId(songsURI)("song-2"),
        slice("info:top_next_songs", (1L, topSongsForSong2))) ::
      Nil
  /**
   * Validates the output generated by a test of the songRecommender.
   *
   * This function accepts the output of a test as a buffer of tuples,
   * where the first tuple element is an entity id for a row that was written to by the job,
   * and the second tuple element is a KijiSlice of song id's, in the form of Strings. We validate
   * that each user in our table gets recommended the most popular next song for the song they have
   * most recently played.
   *
   * @param recommended contains a tuple where the first field is the user id and the second field
   *                    is a KijiSlice containing the recommended song.
   */
  def validateTest(recommended: Buffer[(EntityId, KijiSlice[String])]) {
    val recommendedSongsForEachUser = recommended
      .map { case(entityId, slice) =>
        (entityId(0).toString, slice) }
      .map { case(id, slice) => (id, slice.getFirstValue()) }

    recommendedSongsForEachUser.foreach {
      case ("user-1", recommendedSong) => {
        assert("song-4" === recommendedSong)
      }
      case ("user-2", recommendedSong) => {
        assert("song-5" === recommendedSong)
      }
    }
  }

  val userSource: KijiSource = KijiInput(usersURI)("info:track_plays" -> 'trackPlays)
  val songSource: KijiSource = KijiInput(songsURI)("info:top_next_songs" -> 'topNextSongs)
  val userSourceOut: KijiSource = KijiOutput(usersURI)('nextSong -> "info:next_song_rec")

  test("songRecommender computes a recommendation for the next song to listen to. (Local)") {
    JobTest(new SongRecommender(_))
      .arg("users-table", usersURI)
      .arg("songs-table", songsURI)
      .source(userSource, testUserInput)
      .source(songSource, testSongInput)
      .sink(KijiOutput(usersURI)('nextSong -> "info:next_song_rec")) { validateTest }
      .run
      .finish
  }

  test("songRecommender computes a recommendation for the next song to listen to. (Hadoop)") {
    JobTest(new SongRecommender(_))
      .arg("users-table", usersURI)
      .arg("songs-table", songsURI)
      .source(userSource, testUserInput)
      .source(songSource, testSongInput)
      .sink(userSourceOut) { validateTest }
      .runHadoop
      .finish
  }
}
