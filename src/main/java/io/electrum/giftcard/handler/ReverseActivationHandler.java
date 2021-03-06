package io.electrum.giftcard.handler;

import io.electrum.giftcard.api.model.ActivationReversal;
import io.electrum.giftcard.server.api.GiftcardTestServer;
import io.electrum.giftcard.server.backend.db.MockGiftcardDb;
import io.electrum.giftcard.server.backend.records.ActivationConfirmationRecord;
import io.electrum.giftcard.server.backend.records.ActivationRecord;
import io.electrum.giftcard.server.backend.records.ActivationReversalRecord;
import io.electrum.giftcard.server.backend.records.CardRecord;
import io.electrum.giftcard.server.backend.records.CardRecord.Status;
import io.electrum.giftcard.server.backend.records.RequestRecord.State;
import io.electrum.giftcard.server.backend.records.VoidRecord;
import io.electrum.giftcard.server.backend.tables.ActivationReversalsTable;
import io.electrum.giftcard.server.util.GiftcardModelUtils;
import io.electrum.vas.model.BasicAdviceResponse;
import io.electrum.vas.model.LedgerAmount;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReverseActivationHandler {
   private static final Logger log = LoggerFactory.getLogger(GiftcardTestServer.class.getPackage().getName());

   public Response handle(String requestId, String reversalId, ActivationReversal reversal, HttpHeaders httpHeaders) {
      try {
         // check its a valid request
         Response rsp = GiftcardModelUtils.validateActivationReversal(reversal);
         if (rsp != null) {
            return rsp;
         }
         rsp = GiftcardModelUtils.isUuidConsistent(requestId, reversalId, reversal);
         if (rsp != null) {
            return rsp;
         }
         // get the DB for this user
         String authString = GiftcardModelUtils.getAuthString(httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION));
         String username = GiftcardModelUtils.getUsernameFromAuth(authString);
         String password = GiftcardModelUtils.getPasswordFromAuth(authString);
         MockGiftcardDb giftcardDb = GiftcardTestServer.getBackend().getDbForUser(username, password);
         // record request
         if (giftcardDb.doesUuidExist(reversalId)) {
            return Response.status(400).entity(GiftcardModelUtils.duplicateRequest(reversal, reversalId)).build();
         }
         ActivationReversalsTable activationReversalsTable = giftcardDb.getActivationReversalsTable();
         ActivationReversalRecord activationReversalRecord = new ActivationReversalRecord(reversalId);
         activationReversalRecord.setRequestId(requestId);
         activationReversalRecord.setActivationReversal(reversal);
         activationReversalsTable.putRecord(activationReversalRecord);
         ActivationRecord activationRecord = giftcardDb.getActivationsTable().getRecord(requestId);
         if (activationRecord != null) {
            activationRecord.addReversalId(reversal.getId());
         }
         // process request
         rsp = canReverseActivation(reversal, giftcardDb);
         if (rsp != null) {
            return rsp;
         }
         BasicAdviceResponse adviceResponse = reverseActivation(giftcardDb, reversal);
         // respond
         return Response.accepted().entity(adviceResponse).build();
      } catch (Exception e) {
         log.debug("error processing ActivationReversal", e);
         Response rsp = Response.serverError().entity(e.getMessage()).build();
         return rsp;
      }
   }

   private Response canReverseActivation(ActivationReversal reversal, MockGiftcardDb giftcardDb) {
      ActivationRecord activationRecord = giftcardDb.getActivationsTable().getRecord(reversal.getRequestId());
      if (activationRecord == null) {
         return Response.status(404).entity(GiftcardModelUtils.unableToLocateRecord(reversal)).build();
      } else if (!activationRecord.isResponded()) {
         // means we're actually still processing the request
         return Response.status(400).entity(GiftcardModelUtils.requestBeingProcessed(reversal)).build();
      } else if (activationRecord.getActivationResponse() == null) {
         // means the original activation failed.
         return Response.status(400).entity(GiftcardModelUtils.originalRequestFailed(reversal)).build();
      }
      if (activationRecord.getState() == State.CONFIRMED) {
         ActivationConfirmationRecord confirmationRecord =
               giftcardDb.getActivationConfirmationsTable().getRecord(activationRecord.getLastConfirmationId());
         return Response.status(400)
               .entity(GiftcardModelUtils.originalRequestConfirmed(reversal, confirmationRecord))
               .build();
      }
      CardRecord cardRecord = giftcardDb.getCardRecord(activationRecord.getActivationRequest().getCard());
      switch (cardRecord.getStatus()) {
      case ACTIVATED_CONFIRMED:
         return Response.status(400)
               .entity(GiftcardModelUtils.cardIsActive(reversal, cardRecord, activationRecord))
               .build();
      case VOIDED:
      case VOIDED_CONFIRMED:
         VoidRecord voidRecord = giftcardDb.getVoidsTable().getRecord(cardRecord.getVoidId());
         return Response.status(400).entity(GiftcardModelUtils.cardIsVoided(reversal, cardRecord, voidRecord)).build();
      default:
         return null;
      }
   }

   private BasicAdviceResponse reverseActivation(MockGiftcardDb giftcardDb, ActivationReversal reversal) {
      String requestId = reversal.getRequestId();
      ActivationRecord activationRecord = giftcardDb.getActivationsTable().getRecord(requestId);
      if (activationRecord != null) {
         activationRecord.setState(State.REVERSED);
         CardRecord cardRecord =
               giftcardDb.getCardTable().getRecord(activationRecord.getActivationRequest().getCard().getPan());
         cardRecord.setStatus(Status.NEW);
         cardRecord.setProductId(null);
         LedgerAmount balance = new LedgerAmount().amount(0l).currency("710");
         LedgerAmount availableBalance = new LedgerAmount().amount(0l).currency("710");
         cardRecord.setBalance(balance);
         cardRecord.setAvailableBalance(availableBalance);
         cardRecord.getCard().setClearPin(cardRecord.getOrigClearPin());
         cardRecord.getCard().setEncryptedPin(cardRecord.getOrigEncPin());
      }

      return new BasicAdviceResponse().id(reversal.getId())
            .requestId(reversal.getRequestId())
            .time(reversal.getTime())
            .transactionIdentifiers(reversal.getThirdPartyIdentifiers());
   }
}
