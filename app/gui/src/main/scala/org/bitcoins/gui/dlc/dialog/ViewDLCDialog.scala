package org.bitcoins.gui.dlc.dialog

import org.bitcoins.core.protocol.dlc.models.DLCStatus.Offered
import org.bitcoins.core.protocol.dlc.models._
import org.bitcoins.core.protocol.tlv.{
  EnumOutcome,
  SignedNumericOutcome,
  UnsignedNumericOutcome
}
import org.bitcoins.gui.GlobalData
import org.bitcoins.gui.dlc.{DLCPaneModel, DLCPlotUtil, GlobalDLCData}
import org.bitcoins.gui.util.GUIUtil
import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout.GridPane
import scalafx.stage.Window

object ViewDLCDialog {

  def showAndWait(
      parentWindow: Window,
      status: DLCStatus,
      model: DLCPaneModel): Unit = {
    val dialog = new Dialog[Unit]() {
      initOwner(parentWindow)
      title = "View DLC"
    }

    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    dialog.dialogPane().stylesheets = GlobalData.currentStyleSheets
    dialog.resizable = true

    dialog.dialogPane().content = new GridPane() {
      hgap = 10
      vgap = 10
      padding = Insets(20, 100, 10, 10)

      private var row = 0
      add(new Label("DLC Id:"), 0, row)
      add(new TextField() {
            text = status.dlcId.hex
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("Event Id:"), 0, row)
      add(
        new TextField() {
          text =
            status.oracleInfo.singleOracleInfos.head.announcement.eventTLV.eventId
          editable = false
          minWidth = 300
        },
        columnIndex = 1,
        rowIndex = row
      )

      row += 1
      add(new Label("Initiator:"), 0, row)
      add(new TextField() {
            text = if (status.isInitiator) "Yes" else "No"
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("State:"), 0, row)
      add(new TextField() {
            text = status.statusString
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("Contract Id:"), 0, row)
      val contractId: String = DLCStatus
        .getContractId(status)
        .map(_.toHex)
        .getOrElse("")

      add(new TextField() {
            text = contractId
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("Contract Info:"), 0, row)

      add(new TextField() {
            text = status.contractInfo.toTLV.hex
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      status match {
        case closed: ClosedDLCStatus =>
          row += 1
          add(new Label("My payout:"), 0, row)
          add(new TextField() {
                text = s"${closed.myPayout}"
                editable = false
              },
              columnIndex = 1,
              rowIndex = row)

          row += 1
          add(new Label("Counter party payout:"), 0, row)
          add(new TextField() {
                text = s"${closed.counterPartyPayout}"
                editable = false
              },
              columnIndex = 1,
              rowIndex = row)

          row += 1
          add(new Label("PNL:"), 0, row)
          add(new TextField() {
                text = s"${closed.pnl}"
                editable = false
              },
              columnIndex = 1,
              rowIndex = row)

          row += 1
          add(new Label("Rate of Return:"), 0, row)
          add(new TextField() {
                text = s"${closed.rateOfReturnPrettyPrint}"
                editable = false
              },
              columnIndex = 1,
              rowIndex = row)
        case _: AcceptedDLCStatus | _: Offered =>
        //do nothing as that stats aren't available
      }

      row += 1
      add(new Label("Fee Rate:"), 0, row)
      add(new TextField() {
            text = s"${status.feeRate.toLong} sats/vbyte"
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("Contract Timeout:"), 0, row)
      add(
        new TextField() {
          text = GUIUtil.epochToDateString(status.timeouts.contractTimeout)
          editable = false
        },
        columnIndex = 1,
        rowIndex = row
      )

      row += 1
      add(new Label("Collateral:"), 0, row)
      add(
        new TextField() {
          text = status.totalCollateral.satoshis.toLong.toString
          editable = false
        },
        columnIndex = 1,
        rowIndex = row
      )

      row += 1
      add(new Label("Funding TxId:"), 0, row)
      add(new TextField() {
            text = DLCStatus.getFundingTxId(status).map(_.hex).getOrElse("")
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("Closing TxId:"), 0, row)
      add(new TextField() {
            text = DLCStatus.getClosingTxId(status).map(_.hex).getOrElse("")
            editable = false
          },
          columnIndex = 1,
          rowIndex = row)

      row += 1
      add(new Label("Oracle Signatures:"), 0, row)

      val sigsOpt: Option[String] = DLCStatus
        .getOracleSignatures(status)
        .map(_.map(_.hex).mkString(","))

      val node: Node = sigsOpt match {
        case Some(sigs) =>
          new TextField() {
            text = sigs
            editable = false
          }
        case None =>
          new Button("Execute") {
            onAction = _ => {
              // Set data for this DLC
              GlobalDLCData.lastContractId = contractId
              GlobalDLCData.lastOracleSig = ""
              model.onExecute()
            }
          }
      }
      add(node, columnIndex = 1, rowIndex = row)

      row += 1
      status.contractInfo.contractDescriptor match {
        case _: EnumContractDescriptor => ()
        case descriptor: NumericContractDescriptor =>
          val previewGraphButton: Button = new Button("Preview Graph") {
            onAction = _ => {
              val payoutCurve = if (status.isInitiator) {
                descriptor.outcomeValueFunc
              } else {
                descriptor
                  .flip(status.totalCollateral.satoshis)
                  .outcomeValueFunc
              }

              val outcomeOpt = status match {
                case claimed: ClaimedDLCStatus =>
                  claimed.oracleOutcome.outcome match {
                    case EnumOutcome(_) | SignedNumericOutcome(_, _) => None
                    case UnsignedNumericOutcome(digits) =>
                      Some(digits)
                  }
                case _: Offered | _: AcceptedDLCStatus =>
                  None
              }

              DLCPlotUtil.plotCETs(base = 2,
                                   descriptor.numDigits,
                                   payoutCurve,
                                   status.contractInfo.totalCollateral,
                                   descriptor.roundingIntervals,
                                   outcomeOpt)
              ()
            }
          }

          add(previewGraphButton, 1, row)
      }
    }

    val _ = dialog.showAndWait()
  }
}
