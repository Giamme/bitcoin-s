package org.bitcoins.server.routes

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import org.bitcoins.core.config._
import org.bitcoins.core.util.{EnvUtil, StartStopAsync}
import org.bitcoins.db.AppConfig
import org.bitcoins.db.AppConfig.safePathToString
import org.bitcoins.server.util.DatadirUtil

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Properties

trait BitcoinSRunner extends StartStopAsync[Unit] with Logging {

  protected def args: Array[String]

  implicit def system: ActorSystem

  implicit lazy val ec: ExecutionContext = system.dispatcher

  lazy val argsWithIndex: Vector[(String, Int)] = args.toVector.zipWithIndex

  /** The ip address we are binding the server to */
  lazy val rpcBindOpt: Option[String] = {
    val rpcbindOpt = argsWithIndex.find(_._1.toLowerCase == "--rpcbind")
    rpcbindOpt.map { case (_, idx) =>
      args(idx + 1)
    }
  }

  lazy val rpcPortOpt: Option[Int] = {
    val portOpt = argsWithIndex.find(_._1.toLowerCase == "--rpcport")
    portOpt.map { case (_, idx) =>
      args(idx + 1).toInt
    }
  }

  lazy val networkOpt: Option[BitcoinNetwork] = {
    val netOpt = argsWithIndex.find(_._1.toLowerCase == "--network")
    netOpt.map { case (_, idx) =>
      val string = args(idx + 1)
      string.toLowerCase match {
        case "mainnet"  => MainNet
        case "main"     => MainNet
        case "testnet3" => TestNet3
        case "testnet"  => TestNet3
        case "test"     => TestNet3
        case "regtest"  => RegTest
        case "signet"   => SigNet
        case "sig"      => SigNet
        case _: String =>
          throw new IllegalArgumentException(s"Invalid network $string")
      }
    }
  }

  lazy val forceChainWorkRecalc: Boolean =
    args.exists(_.toLowerCase == "--force-recalc-chainwork")

  private lazy val dataDirIndexOpt: Option[(String, Int)] = {
    argsWithIndex.find(_._1.toLowerCase == "--datadir")
  }

  /** Sets the default data dir, overridden by the --datadir option */
  private lazy val datadirPath: Path = dataDirIndexOpt match {
    case None => AppConfig.DEFAULT_BITCOIN_S_DATADIR
    case Some((_, dataDirIndex)) =>
      val str = args(dataDirIndex + 1)
      val usableStr = str.replace("~", Properties.userHome)
      Paths.get(usableStr)
  }

  private lazy val configIndexOpt: Option[Int] = {
    argsWithIndex.find(_._1.toLowerCase == "--conf").map(_._2)
  }

  lazy val datadirConfig: Config =
    ConfigFactory.parseString(
      s"bitcoin-s.datadir = ${safePathToString(datadirPath)}")

  lazy val networkConfig: Config = networkOpt match {
    case Some(network) =>
      val networkStr = DatadirUtil.networkStrToDirName(network.name)
      ConfigFactory.parseString(s"bitcoin-s.network = $networkStr")
    case None => ConfigFactory.empty()
  }

  lazy val baseConfig: Config = configIndexOpt match {
    case None =>
      AppConfig
        .getBaseConfig(datadirPath, List(networkConfig))
        .withFallback(datadirConfig)
        .resolve()
    case Some(configIndex) =>
      val str = args(configIndex + 1)
      val usableStr = str.replace("~", Properties.userHome)
      val path = Paths.get(usableStr)
      ConfigFactory
        .parseFile(path.toFile)
        .withFallback(datadirConfig)
        .resolveWith(networkConfig)
  }

  /** Base directory for all bitcoin-s data. This is the resulting datadir from
    * the --datadir option and all configuration files.
    */
  lazy val datadir: Path =
    Paths.get(baseConfig.getString("bitcoin-s.datadir"))

  // start everything!
  final def run(customFinalDirOpt: Option[String] = None): Unit = {

    /** Directory specific for current network or custom dir */
    val usedDir: Path =
      DatadirUtil.getFinalDatadir(datadir, baseConfig, customFinalDirOpt)

    //We need to set the system property before any logger instances
    //are in instantiated. If we don't do this, we will not log to
    //the correct location
    //see: https://github.com/bitcoin-s/bitcoin-s/issues/2496
    System.setProperty("bitcoins.log.location", usedDir.toAbsolutePath.toString)

    logger.info(s"version=${EnvUtil.getVersion}")

    logger.info(s"using directory ${usedDir.toAbsolutePath.toString}")
    val runner: Future[Unit] = start()
    runner.failed.foreach { err =>
      logger.error(s"Failed to startup server!", err)
    }(scala.concurrent.ExecutionContext.Implicits.global)
  }
}
