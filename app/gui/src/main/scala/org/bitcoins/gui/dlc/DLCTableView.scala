package org.bitcoins.gui.dlc

import org.bitcoins.core.dlc.accounting.RateOfReturnUtil
import org.bitcoins.core.protocol.dlc.models.DLCStatus._
import org.bitcoins.core.protocol.dlc.models.{
  AcceptedDLCStatus,
  BroadcastedDLCStatus,
  ClosedDLCStatus,
  DLCStatus
}
import org.bitcoins.gui.util.GUIUtil
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.control.{ContextMenu, MenuItem, TableColumn, TableView}

import java.awt.Toolkit.getDefaultToolkit
import java.awt.datatransfer.StringSelection

class DLCTableView(model: DLCPaneModel) {

  val tableView: TableView[DLCStatus] = {

    val eventIdCol = new TableColumn[DLCStatus, String] {
      text = "Event Id"
      prefWidth = 160
      cellValueFactory = { status =>
        val eventIdStr =
          status.value.oracleInfo.singleOracleInfos.head.announcement.eventTLV.eventId

        new StringProperty(status, "Event Id", eventIdStr)
      }
    }

    val contractIdCol = new TableColumn[DLCStatus, String] {
      text = "Contract Id"
      prefWidth = 100
      cellValueFactory = { status =>
        val contractIdStr = status.value match {
          case _: Offered => ""
          case signed: AcceptedDLCStatus =>
            signed.contractId.toHex
        }

        new StringProperty(status, "Contract Id", contractIdStr)
      }
    }

    val statusCol = new TableColumn[DLCStatus, String] {
      text = "Status"
      prefWidth = 125
      cellValueFactory = { status =>
        new StringProperty(status, "Status", status.value.statusString)
      }
    }

    val collateralCol = new TableColumn[DLCStatus, String] {
      text = "Collateral"
      prefWidth = 110
      cellValueFactory = { status =>
        val amt = GUIUtil.numberFormatter.format(
          status.value.localCollateral.satoshis.toLong)
        new StringProperty(status, "Collateral", s"$amt sats")
      }
    }

    val otherCollateralCol = new TableColumn[DLCStatus, String] {
      text = "Counter Party Collateral"
      prefWidth = 200
      cellValueFactory = { status =>
        val amt = GUIUtil.numberFormatter.format(
          status.value.remoteCollateral.satoshis.toLong)
        new StringProperty(status, "Counter Party Collateral", s"$amt sats")
      }
    }

    val totalCollateralCol = new TableColumn[DLCStatus, String] {
      text = "Total Collateral"
      prefWidth = 125
      cellValueFactory = { status =>
        val amt = GUIUtil.numberFormatter.format(
          status.value.totalCollateral.satoshis.toLong)
        new StringProperty(status, "Total Collateral", s"$amt sats")
      }
    }

    val pnlCol = new TableColumn[DLCStatus, String] {
      text = "Realized PNL"
      prefWidth = 100
      cellValueFactory = { status =>
        status.value match {
          case closed: ClosedDLCStatus =>
            new StringProperty(status, "PNL", s"${closed.pnl}")
          case _: BroadcastedDLCStatus | _: AcceptedDLCStatus | _: Offered =>
            new StringProperty(status, "PNL", "In progress")
        }
      }
    }

    val rorCol = new TableColumn[DLCStatus, String] {
      text = "Rate of Return"
      prefWidth = 125
      cellValueFactory = { status =>
        status.value match {
          case closed: ClosedDLCStatus =>
            new StringProperty(
              status,
              "Rate of Return",
              s"${RateOfReturnUtil.prettyPrint(closed.rateOfReturn)}")
          case _: BroadcastedDLCStatus | _: AcceptedDLCStatus | _: Offered =>
            new StringProperty(status, "Rate of Return", "In progress")
        }
      }
    }

    new TableView[DLCStatus](GlobalDLCData.dlcs) {
      columns ++= Seq(eventIdCol,
                      contractIdCol,
                      statusCol,
                      pnlCol,
                      rorCol,
                      collateralCol,
                      otherCollateralCol,
                      totalCollateralCol)
      margin = Insets(10, 0, 10, 0)

      val infoItem: MenuItem = new MenuItem("View DLC") {
        onAction = _ => {
          val dlc = selectionModel.value.getSelectedItem
          model.viewDLC(dlc)
        }
      }

      val copyIdItem: MenuItem = new MenuItem("Copy Contract Id") {
        onAction = _ => {
          val dlc = selectionModel.value.getSelectedItem
          getContractId(dlc).foreach { id =>
            GlobalDLCData.lastContractId = id.toHex

            val clipboard = getDefaultToolkit.getSystemClipboard
            val sel = new StringSelection(id.toHex)
            clipboard.setContents(sel, sel)
          }
        }
      }

      val cancelDLCItem: MenuItem = new MenuItem("Cancel DLC") {
        onAction = _ => {
          val dlc = selectionModel.value.getSelectedItem
          model.cancelDLC(dlc)
        }
      }

      contextMenu = new ContextMenu() {
        items ++= Vector(infoItem, copyIdItem, cancelDLCItem)
      }
    }
  }
}
