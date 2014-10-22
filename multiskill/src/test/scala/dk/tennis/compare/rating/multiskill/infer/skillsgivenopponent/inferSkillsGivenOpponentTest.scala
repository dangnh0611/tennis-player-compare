package dk.tennis.compare.rating.multiskill.infer.skillsgivenopponent

import org.junit._
import Assert._
import dk.tennis.compare.rating.multiskill.model.perfdiff.skillsfactor.cov.opponent.OpponentCovFunc
import scala.math._
import dk.tennis.compare.rating.multiskill.model.perfdiff.skillsfactor.cov.opponenttype.OpponentType
import dk.tennis.compare.rating.multiskill.matchloader.generateMatches
import com.typesafe.scalalogging.slf4j.Logging
import dk.tennis.compare.rating.multiskill.model.perfdiff.Score
import dk.tennis.compare.rating.multiskill.scoresim.ScoresSimulator
import dk.tennis.compare.rating.multiskill.learn.PlayerCovFuncFactory
import dk.tennis.compare.rating.multiskill.model.perfdiff.skillsfactor.cov.opponent.PlayerSkill
import dk.tennis.compare.rating.multiskill.model.perfdiff.skillsfactor.cov.CovFunc
import dk.tennis.compare.rating.multiskill.model.perfdiff.skillsfactor.cov.opponent.OpponentOverTimeCovFunc
import scala.util.Random
import dk.tennis.compare.rating.multiskill.model.perfdiff.Player
class inferSkillsGivenOpponentTest extends Logging {

  logger.info("Generating match results")
  val opponentMap = Map(
    "p1" -> OpponentType("p1", true), "p2" -> OpponentType("p2", true),
    "p3" -> OpponentType("p3", false), "p4" -> OpponentType("p4", false))
  val matchResults = generateMatches(opponentMap.keys.toList, rounds = 10)

  logger.info("All matches:" + matchResults.size)

  logger.info("Simulating scores")
  val realScores: Array[Score] = Score.toScores(matchResults)

  logger.info("All players:" + realScores.map(s => s.player1.playerName).distinct.size)

  logger.info("Simulating scores...")
  val scoresSimulator = ScoresSimulator()
  val (scores, trueLoglik) = scoresSimulator.simulate(realScores, opponentMap)

  val players = opponentMap.keys.toList

  val skillCovParams = Array(log(1), log(2),
    log(0.3), log(30), log(1), log(365))

  val logPerfStdDev = 2.3

  val skillCovFactory = OpponentSkillCovFactory()

  val priorSkillsGivenOpponent = SkillsGivenOpponent(calcPriorSkillsGivenOpponent(scores.map(s => s.player1)), calcPriorSkillsGivenOpponent(scores.map(s => s.player2)))

  @Test def test {
    val skillsGivenOpponent = inferSkillsGivenOpponent(priorSkillsGivenOpponent, scores, scoresSimulator.skillMeanFunc, skillCovParams, skillCovFactory, logPerfStdDev)

    val playerCovFunc = OpponentCovFunc(Array(log(1), log(2)), skillsGivenOpponent.skillsOnServeGivenOpponent, skillsGivenOpponent.skillsOnReturnGivenOpponent)

    val m = playerCovFunc.opponentOnReturnSimMatrix(players)

    println(m)
  }

  /**
   * Returns Map[opponent name, player skills against opponent]
   */
  def calcPriorSkillsGivenOpponent(playersGivenOpponent: Seq[Player]): Map[String, Seq[PlayerSkill]] = {

    val rand = new Random()
    val allPlayers = playersGivenOpponent.map(p => p.playerName).distinct

    val skillsGivenOpponentMap = allPlayers.map { playerKey =>

      val skills = playersGivenOpponent.map(p => PlayerSkill(rand.nextDouble * 0.1, p.copy(opponentName = playerKey))).toSeq
      (playerKey, skills)
    }.toMap

    skillsGivenOpponentMap
  }

  case class OpponentSkillCovFactory extends PlayerCovFuncFactory {
    def create(params: Seq[Double], skillsOnServeGivenOpponent: Map[String, Seq[PlayerSkill]], skillsOnReturnGivenOpponent: Map[String, Seq[PlayerSkill]]): CovFunc = {
      OpponentOverTimeCovFunc(params, skillsOnServeGivenOpponent, skillsOnReturnGivenOpponent)
    }
  }

}