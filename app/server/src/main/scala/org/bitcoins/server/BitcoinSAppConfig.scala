package org.bitcoins.server

import com.typesafe.config.{Config, ConfigFactory}
import org.bitcoins.chain.config.ChainAppConfig
import org.bitcoins.commons.file.FileUtil
import org.bitcoins.core.util.StartStopAsync
import org.bitcoins.db.AppConfig
import org.bitcoins.dlc.wallet.DLCAppConfig
import org.bitcoins.keymanager.config.KeyManagerAppConfig
import org.bitcoins.node.config.NodeAppConfig
import org.bitcoins.wallet.config.WalletAppConfig

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

/** A unified config class for all submodules of Bitcoin-S
  * that accepts configuration. Thanks to implicit definitions
  * in this case class' companion object an instance
  * of this class can be passed in anywhere a wallet,
  * chain or node config is required.
  *
  * @param directory The data directory of this app configuration
  * @param confs A sequence of optional configuration overrides
  */
case class BitcoinSAppConfig(
    private val directory: Path,
    private val confs: Config*)(implicit ec: ExecutionContext)
    extends StartStopAsync[Unit] {
  lazy val walletConf: WalletAppConfig = WalletAppConfig(directory, confs: _*)
  lazy val nodeConf: NodeAppConfig = NodeAppConfig(directory, confs: _*)
  lazy val chainConf: ChainAppConfig = ChainAppConfig(directory, confs: _*)
  lazy val dlcConf: DLCAppConfig = DLCAppConfig(directory, confs: _*)

  def copyWithConfig(newConfs: Vector[Config]): BitcoinSAppConfig = {
    val configs = newConfs ++ confs
    BitcoinSAppConfig(directory, configs: _*)
  }

  lazy val kmConf: KeyManagerAppConfig =
    KeyManagerAppConfig(directory, confs: _*)

  lazy val bitcoindRpcConf: BitcoindRpcAppConfig =
    BitcoindRpcAppConfig(directory, confs: _*)

  /** Initializes the wallet, node and chain projects */
  override def start(): Future[Unit] = {
    val futures = List(kmConf.start(),
                       walletConf.start(),
                       nodeConf.start(),
                       chainConf.start(),
                       bitcoindRpcConf.start(),
                       dlcConf.start())

    Future.sequence(futures).map(_ => ())
  }

  override def stop(): Future[Unit] = {
    for {
      _ <- nodeConf.stop()
      _ <- walletConf.stop()
      _ <- chainConf.stop()
      _ <- bitcoindRpcConf.stop()
    } yield ()
  }

  /** The underlying config the result of our fields derive from */
  lazy val config: Config = {
    val finalConfig =
      AppConfig.getBaseConfig(baseDatadir = directory, confs.toList)
    val resolved = finalConfig.resolve()

    resolved.checkValid(ConfigFactory.defaultReference(), "bitcoin-s")

    resolved
  }

  def rpcPort: Int = config.getInt("bitcoin-s.server.rpcport")

  def rpcBindOpt: Option[String] = {
    if (config.hasPath("bitcoin-s.server.rpcbind")) {
      Some(config.getString("bitcoin-s.server.rpcbind"))
    } else {
      None
    }
  }

  def exists(): Boolean = {
    directory.resolve("bitcoin-s.conf").toFile.isFile
  }

  def withOverrides(configs: Config*): BitcoinSAppConfig = {
    BitcoinSAppConfig(directory, configs ++ confs: _*)
  }

  /** Zips $HOME/.bitcoin-s
    */
  def zipDatadir(target: Path): Path = {
    FileUtil.zipDirectory(
      source = directory,
      target = target,
      // we don't want to store chaindb.sqlite as these databases are huge
      // skip logs and binaries as these can be large as well
      fileNameFilter =
        Vector(".*chaindb.sqlite$".r, ".*bitcoin-s.log$".r, ".*/binaries/.*".r)
    )
  }
}

/** Implicit conversions that allow a unified configuration
  * to be passed in wherever a specializes one is required
  */
object BitcoinSAppConfig {

  def fromConfig(config: Config)(implicit
      ec: ExecutionContext): BitcoinSAppConfig = {
    val configDataDir: Path = Paths.get(config.getString("bitcoin-s.datadir"))
    BitcoinSAppConfig(configDataDir, config)
  }

  def fromClassPathConfig()(implicit
      ec: ExecutionContext): BitcoinSAppConfig = {
    fromConfig(ConfigFactory.load())
  }

  /** Constructs an app configuration from the default Bitcoin-S
    * data directory and given list of configuration overrides.
    */
  def fromDefaultDatadir(confs: Config*)(implicit
      ec: ExecutionContext): BitcoinSAppConfig =
    BitcoinSAppConfig(AppConfig.DEFAULT_BITCOIN_S_DATADIR, confs: _*)

  import scala.language.implicitConversions

  /** Converts the given implicit config to a wallet config */
  implicit def implicitToWalletConf(implicit
      conf: BitcoinSAppConfig): WalletAppConfig =
    conf.walletConf

  /** Converts the given config to a wallet config */
  implicit def toWalletConf(conf: BitcoinSAppConfig): WalletAppConfig =
    conf.walletConf

  /** Converts the given implicit config to a chain config */
  implicit def implicitToChainConf(implicit
      conf: BitcoinSAppConfig): ChainAppConfig =
    conf.chainConf

  /** Converts the given config to a chain config */
  implicit def toChainConf(conf: BitcoinSAppConfig): ChainAppConfig =
    conf.chainConf

  /** Converts the given implicit config to a node config */
  implicit def implicitToNodeConf(implicit
      conf: BitcoinSAppConfig): NodeAppConfig =
    conf.nodeConf

  /** Converts the given config to a node config */
  implicit def toNodeConf(conf: BitcoinSAppConfig): NodeAppConfig =
    conf.nodeConf

  /** Converts the given implicit config to a bitcoind rpc config */
  implicit def implicitToBitcoindRpcConf(implicit
      conf: BitcoinSAppConfig): BitcoindRpcAppConfig =
    conf.bitcoindRpcConf

  /** Converts the given config to a bitcoind rpc config */
  implicit def toBitcoindRpcConf(
      conf: BitcoinSAppConfig): BitcoindRpcAppConfig =
    conf.bitcoindRpcConf

}
