package webcrawler.repository

import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import cats.implicits._
import webcrawler.CrawlerResult

class DoobieRepository[F[_]: MonadCancelThrow](val xa: Transactor[F]) {

  def insert(res: CrawlerResult): F[CrawlerResult] =
    sql"INSERT INTO data (title, url, content) values (${res.title}, ${res.url}, ${res.content})"
      .update
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => res.copy(id = id.some))
      .transact(xa)

  def select = sql"SELECT id, title, url, content FROM data LIMIT 5"
    .query[CrawlerResult]
    .to[List]
    .transact(xa)

}
