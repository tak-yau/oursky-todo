package com.oursky.todo.db

import com.augustnagro.magnum.*
import javax.sql.DataSource
import scala.util.Using
import java.sql.Connection

class DB(dataSource: DataSource):

  def transact[T](f: DbCon ?=> T): T =
    Using.Manager { use =>
      val conn = use(dataSource.getConnection)
      conn.setAutoCommit(false)
      try
        given DbCon = createDbCon(conn)
        val result = f
        conn.commit()
        result
      catch
        case e: Throwable =>
          conn.rollback()
          throw e
    }.get

  def transactEither[E, T](f: DbCon ?=> Either[E, T]): Either[E, T] =
    Using.Manager { use =>
      val conn = use(dataSource.getConnection)
      conn.setAutoCommit(false)
      try
        given DbCon = createDbCon(conn)
        f match
          case Right(result) =>
            conn.commit()
            Right(result)
          case Left(error) =>
            conn.rollback()
            Left(error)
      catch
        case e: Throwable =>
          conn.rollback()
          throw e
    }.get

  private def createDbCon(conn: Connection): DbCon =
    try
      val constructor = classOf[DbCon].getDeclaredConstructor(classOf[Connection], classOf[SqlLogger])
      constructor.setAccessible(true)
      constructor.newInstance(conn, SqlLogger.NoOp)
    catch
      case e: Exception =>
        throw new RuntimeException(
          "Failed to create DbCon. This may occur if Magnum version changed its internal API. " +
          "Consider upgrading to a newer Magnum version with public DbCon creation API.",
          e
        )

object DB:
  def create(dataSource: DataSource, initSql: Option[String] = None): DB =
    initSql.foreach { sql =>
      Using(dataSource.getConnection) { conn =>
        conn.createStatement().execute(sql)
      }
    }
    new DB(dataSource)
