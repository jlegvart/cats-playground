package twitter.repository

import cats.effect.MonadCancelThrow
import cats.implicits._
import doobie.util.transactor
import twitter.model.Tweet
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import java.sql.Timestamp

case class TwitterRepository[F[_]: MonadCancelThrow](val xa: transactor.Transactor[F]) {

  def insert(tweet: Tweet): F[Tweet] =
    sql"INSERT INTO tweets (tweet, added) values (${tweet.tweet}, ${Timestamp.valueOf(tweet.added)})"
      .update
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => tweet.copy(id = id.some))
      .transact(xa)

}
