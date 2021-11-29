package twitter.model

import java.time.temporal.Temporal
import java.time.OffsetDateTime
import java.time.LocalDateTime

final case class Tweet(id: Option[Long] = None, tweetId: Option[String], tweet: String, added: LocalDateTime)