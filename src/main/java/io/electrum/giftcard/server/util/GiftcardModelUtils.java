package io.electrum.giftcard.server.util;

import io.electrum.giftcard.api.model.ActivationConfirmation;
import io.electrum.giftcard.api.model.ActivationRequest;
import io.electrum.giftcard.api.model.ActivationResponse;
import io.electrum.giftcard.api.model.ActivationReversal;
import io.electrum.giftcard.api.model.Card;
import io.electrum.giftcard.api.model.ErrorDetail;
import io.electrum.giftcard.api.model.ErrorDetail.ErrorType;
import io.electrum.giftcard.api.model.GiftcardAmounts;
import io.electrum.giftcard.api.model.LoadConfirmation;
import io.electrum.giftcard.api.model.LoadRequest;
import io.electrum.giftcard.api.model.LoadResponse;
import io.electrum.giftcard.api.model.LoadReversal;
import io.electrum.giftcard.api.model.LookupRequest;
import io.electrum.giftcard.api.model.LookupResponse;
import io.electrum.giftcard.api.model.PosInfo;
import io.electrum.giftcard.api.model.Product;
import io.electrum.giftcard.api.model.RedemptionConfirmation;
import io.electrum.giftcard.api.model.RedemptionRequest;
import io.electrum.giftcard.api.model.RedemptionResponse;
import io.electrum.giftcard.api.model.RedemptionReversal;
import io.electrum.giftcard.api.model.SlipData;
import io.electrum.giftcard.api.model.VoidConfirmation;
import io.electrum.giftcard.api.model.VoidRequest;
import io.electrum.giftcard.api.model.VoidResponse;
import io.electrum.giftcard.api.model.VoidReversal;
import io.electrum.giftcard.server.api.GiftcardTestServer;
import io.electrum.giftcard.server.api.model.DetailMessage;
import io.electrum.giftcard.server.api.model.FormatError;
import io.electrum.giftcard.server.backend.db.MockGiftcardDb;
import io.electrum.giftcard.server.backend.records.ActivationConfirmationRecord;
import io.electrum.giftcard.server.backend.records.ActivationRecord;
import io.electrum.giftcard.server.backend.records.ActivationReversalRecord;
import io.electrum.giftcard.server.backend.records.CardRecord;
import io.electrum.giftcard.server.backend.records.LoadConfirmationRecord;
import io.electrum.giftcard.server.backend.records.LoadReversalRecord;
import io.electrum.giftcard.server.backend.records.RedemptionConfirmationRecord;
import io.electrum.giftcard.server.backend.records.RedemptionReversalRecord;
import io.electrum.giftcard.server.backend.records.VoidConfirmationRecord;
import io.electrum.giftcard.server.backend.records.VoidRecord;
import io.electrum.giftcard.server.backend.records.VoidReversalRecord;
import io.electrum.vas.model.BasicAdvice;
import io.electrum.vas.model.Institution;
import io.electrum.vas.model.LedgerAmount;
import io.electrum.vas.model.Merchant;
import io.electrum.vas.model.Originator;
import io.electrum.vas.model.SlipLine;
import io.electrum.vas.model.Tender;
import io.electrum.vas.model.ThirdPartyIdentifier;
import io.electrum.vas.model.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.internal.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GiftcardModelUtils {
   private static List<SlipLine> messageLines;
   private static final Logger log = LoggerFactory.getLogger(GiftcardTestServer.class.getPackage().getName());

   public static ActivationResponse activationRspFromReq(MockGiftcardDb giftcardDb, ActivationRequest req) {
      Product product = req.getProduct();
      Card card = req.getCard();
      if (product == null && card == null) {
         throw new IllegalStateException("Card and product must be populated in an ActivationRequest message.");
      }
      ActivationResponse rsp = new ActivationResponse();
      // id
      rsp.setId(req.getId());
      // time
      rsp.setTime(req.getTime());
      // originator
      rsp.setOriginator(req.getOriginator());
      // client
      rsp.setClient(req.getClient());
      // settlement entity
      Institution settlementEntity = req.getSettlementEntity();
      if (settlementEntity == null) {
         settlementEntity = new Institution();
         settlementEntity.setId("33333333");
         settlementEntity.setName("TransactionsRUs");
      }
      rsp.setSettlementEntity(settlementEntity);
      // receiver
      Institution receiver = req.getReceiver();
      if (receiver == null) {
         receiver = new Institution();
         receiver.setId("44444444");
         receiver.setName("GlobalGiftcards");
      }
      rsp.setReceiver(receiver);
      // thirdPartyIdentifiers
      List<ThirdPartyIdentifier> thirdPartyIds = req.getThirdPartyIdentifiers();
      if (thirdPartyIds == null) {
         new ArrayList<ThirdPartyIdentifier>();
      }
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(settlementEntity.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(receiver.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      rsp.setThirdPartyIdentifiers(thirdPartyIds);
      // amounts
      GiftcardAmounts amounts = req.getAmounts();
      CardRecord cardRecord = giftcardDb.getCardTable().getRecord(card.getPan());
      LedgerAmount balance = new LedgerAmount().amount(cardRecord.getBalance().getAmount()).currency("710");
      LedgerAmount availableBalance =
            new LedgerAmount().amount(cardRecord.getAvailableBalance().getAmount()).currency("710");
      if (amounts == null) {
         // no load amount requested
         amounts = new GiftcardAmounts();
      } else {
         // requestAmount = load amount
         LedgerAmount requestAmount = amounts.getRequestAmount();
         // approvedAmount = load amount
         amounts.setApprovedAmount(requestAmount);
         // balance = starting balance + load amount
      }
      amounts.setBalanceAmount(balance);
      amounts.setAvailableBalance(availableBalance);
      rsp.setAmounts(amounts);
      // card
      rsp.setCard(card);
      // posInfo
      rsp.setPosInfo(req.getPosInfo());
      // product
      rsp.setProduct(product);
      // slipData
      messageLines = new ArrayList<SlipLine>();
      messageLines.add(new SlipLine().text("Congratulations!"));
      messageLines.add(new SlipLine().text("You can redeem your giftcard for cool stuff."));
      messageLines.add(new SlipLine().text("For queries quote the issuer reference below:"));
      SlipData slipData = new SlipData();
      slipData.setMessageLines(messageLines);
      slipData.setIssuerReference(RandomData.random09AZ((int) ((Math.random() * 20) + 1)));
      rsp.setSlipData(slipData);
      return rsp;
   }

   public static LoadResponse loadRspFromReq(MockGiftcardDb giftcardDb, LoadRequest req) {
      Product product = req.getProduct();
      Card card = req.getCard();
      if (card == null) {
         throw new IllegalStateException("Card must be populated in a LoadRequest message.");
      }
      LoadResponse rsp = new LoadResponse();
      // id
      rsp.setId(req.getId());
      // time
      rsp.setTime(req.getTime());
      // originator
      rsp.setOriginator(req.getOriginator());
      // client
      rsp.setClient(req.getClient());
      // settlement entity
      Institution settlementEntity = req.getSettlementEntity();
      if (settlementEntity == null) {
         settlementEntity = new Institution();
         settlementEntity.setId("33333333");
         settlementEntity.setName("TransactionsRUs");
      }
      rsp.setSettlementEntity(settlementEntity);
      // receiver
      Institution receiver = req.getReceiver();
      if (receiver == null) {
         receiver = new Institution();
         receiver.setId("44444444");
         receiver.setName("GlobalGiftcards");
      }
      rsp.setReceiver(receiver);
      // thirdPartyIdentifiers
      List<ThirdPartyIdentifier> thirdPartyIds = req.getThirdPartyIdentifiers();
      if (thirdPartyIds == null) {
         new ArrayList<ThirdPartyIdentifier>();
      }
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(settlementEntity.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(receiver.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      rsp.setThirdPartyIdentifiers(thirdPartyIds);
      // amounts
      GiftcardAmounts amounts = req.getAmounts();
      CardRecord cardRecord = giftcardDb.getCardTable().getRecord(card.getPan());
      LedgerAmount balance = new LedgerAmount().amount(cardRecord.getBalance().getAmount()).currency("710");
      LedgerAmount availableBalance =
            new LedgerAmount().amount(cardRecord.getAvailableBalance().getAmount()).currency("710");
      // requestAmount = load amount
      LedgerAmount requestAmount = amounts.getRequestAmount();
      if (requestAmount == null) {
         throw new IllegalStateException("Amounts.requestAmount must be populated in a LoadRequest message.");
      }
      // approvedAmount = load amount
      amounts.setApprovedAmount(requestAmount);
      amounts.setBalanceAmount(balance);
      amounts.setAvailableBalance(availableBalance);
      rsp.setAmounts(amounts);
      // card
      rsp.setCard(card);
      // posInfo
      rsp.setPosInfo(req.getPosInfo());
      // product
      rsp.setProduct(product);
      // slipData
      messageLines = new ArrayList<SlipLine>();
      messageLines.add(new SlipLine().text("Congratulations!"));
      messageLines.add(new SlipLine().text("Your card has been loaded."));
      messageLines.add(new SlipLine().text("For queries quote the issuer reference below:"));
      SlipData slipData = new SlipData();
      slipData.setMessageLines(messageLines);
      slipData.setIssuerReference(RandomData.random09AZ((int) ((Math.random() * 20) + 1)));
      rsp.setSlipData(slipData);
      return rsp;
   }

   public static BasicAdvice loadConfirmResponse(LoadConfirmation adviceRequest) {
      BasicAdvice ba =
            new BasicAdvice().id(adviceRequest.getId())
                  .requestId(adviceRequest.getRequestId())
                  .time(adviceRequest.getTime())
                  .transactionIdentifiers(adviceRequest.getThirdPartyIdentifiers());

      return ba;
   }

   public static LookupResponse lookupRspFromReq(MockGiftcardDb giftcardDb, LookupRequest req) {
      Card card = req.getCard();
      if (card == null) {
         throw new IllegalStateException("Card must be populated in a LookupRequest message.");
      }
      Product product = null;
      CardRecord cardRecord = giftcardDb.getCardRecord(card);
      if (cardRecord.getProductId() != null) {
         giftcardDb.getProductTable().getRecord(cardRecord.getProductId()).getProduct();
      }
      LedgerAmount balance = cardRecord.getBalance();
      LedgerAmount availableBalance = cardRecord.getAvailableBalance();
      LookupResponse rsp = new LookupResponse();
      // id
      rsp.setId(req.getId());
      // time
      rsp.setTime(req.getTime());
      // originator
      rsp.setOriginator(req.getOriginator());
      // client
      rsp.setClient(req.getClient());
      // settlement entity
      Institution settlementEntity = req.getSettlementEntity();
      if (settlementEntity == null) {
         settlementEntity = new Institution();
         settlementEntity.setId("33333333");
         settlementEntity.setName("TransactionsRUs");
      }
      rsp.setSettlementEntity(settlementEntity);
      // receiver
      Institution receiver = req.getReceiver();
      if (receiver == null) {
         receiver = new Institution();
         receiver.setId("44444444");
         receiver.setName("GlobalGiftcards");
      }
      rsp.setReceiver(receiver);
      // thirdPartyIdentifiers
      List<ThirdPartyIdentifier> thirdPartyIds = req.getThirdPartyIdentifiers();
      if (thirdPartyIds == null) {
         new ArrayList<ThirdPartyIdentifier>();
      }
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(settlementEntity.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(receiver.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      rsp.setThirdPartyIdentifiers(thirdPartyIds);
      // amounts
      GiftcardAmounts amounts = new GiftcardAmounts();
      amounts.setBalanceAmount(balance);
      amounts.setAvailableBalance(availableBalance);
      rsp.setAmounts(amounts);
      // card
      rsp.setCard(card);
      // posInfo
      rsp.setPosInfo(req.getPosInfo());
      // product
      rsp.setProduct(product);
      // slipData
      messageLines = new ArrayList<SlipLine>();
      messageLines.add(new SlipLine().text("For queries quote the issuer reference below:"));
      SlipData slipData = new SlipData();
      slipData.setMessageLines(messageLines);
      slipData.setIssuerReference(RandomData.random09AZ((int) ((Math.random() * 20) + 1)));
      rsp.setSlipData(slipData);
      return rsp;
   }

   public static RedemptionResponse redemptionRspFromReq(MockGiftcardDb giftcardDb, RedemptionRequest req) {
      Product product = req.getProduct();
      Card card = req.getCard();
      if (card == null) {
         throw new IllegalStateException("Card must be populated in a RedemptionRequest message.");
      }
      RedemptionResponse rsp = new RedemptionResponse();
      // id
      rsp.setId(req.getId());
      // time
      rsp.setTime(req.getTime());
      // originator
      rsp.setOriginator(req.getOriginator());
      // client
      rsp.setClient(req.getClient());
      // settlement entity
      Institution settlementEntity = req.getSettlementEntity();
      if (settlementEntity == null) {
         settlementEntity = new Institution();
         settlementEntity.setId("33333333");
         settlementEntity.setName("TransactionsRUs");
      }
      rsp.setSettlementEntity(settlementEntity);
      // receiver
      Institution receiver = req.getReceiver();
      if (receiver == null) {
         receiver = new Institution();
         receiver.setId("44444444");
         receiver.setName("GlobalGiftcards");
      }
      rsp.setReceiver(receiver);
      // thirdPartyIdentifiers
      List<ThirdPartyIdentifier> thirdPartyIds = req.getThirdPartyIdentifiers();
      if (thirdPartyIds == null) {
         new ArrayList<ThirdPartyIdentifier>();
      }
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(settlementEntity.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(receiver.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      rsp.setThirdPartyIdentifiers(thirdPartyIds);
      // amounts
      GiftcardAmounts amounts = req.getAmounts();
      CardRecord cardRecord = giftcardDb.getCardTable().getRecord(card.getPan());
      LedgerAmount balance = new LedgerAmount().amount(cardRecord.getBalance().getAmount()).currency("710");
      LedgerAmount availableBalance =
            new LedgerAmount().amount(cardRecord.getAvailableBalance().getAmount()).currency("710");
      // requestAmount = load amount
      LedgerAmount requestAmount = amounts.getRequestAmount();
      if (requestAmount == null) {
         throw new IllegalStateException("Amounts.requestAmount must be populated in a RedemptionRequest message.");
      }
      // approvedAmount = load amount
      amounts.setApprovedAmount(requestAmount);
      // balance = cardRecord's balance since it's updated in the redemption request, not confirmation (unlike for
      // loads)
      amounts.setBalanceAmount(balance);
      amounts.setAvailableBalance(availableBalance);
      rsp.setAmounts(amounts);
      // card
      rsp.setCard(card);
      // posInfo
      rsp.setPosInfo(req.getPosInfo());
      // product
      rsp.setProduct(product);
      // slipData
      messageLines = new ArrayList<SlipLine>();
      messageLines.add(new SlipLine().text("Congratulations!"));
      messageLines.add(new SlipLine().text("Your purchase was successful."));
      messageLines.add(new SlipLine().text("For queries quote the issuer reference below:"));
      SlipData slipData = new SlipData();
      slipData.setMessageLines(messageLines);
      slipData.setIssuerReference(RandomData.random09AZ((int) ((Math.random() * 20) + 1)));
      rsp.setSlipData(slipData);
      return rsp;
   }

   public static VoidResponse voidRspFromReq(MockGiftcardDb giftcardDb, VoidRequest req) {
      Card card = req.getCard();
      if (card == null) {
         throw new IllegalStateException("Card must be populated in an VoidRequest message.");
      }
      CardRecord cardRecord = giftcardDb.getCardRecord(card);
      VoidResponse rsp = new VoidResponse();
      // id
      rsp.setId(req.getId());
      // time
      rsp.setTime(req.getTime());
      // originator
      rsp.setOriginator(req.getOriginator());
      // client
      rsp.setClient(req.getClient());
      // settlement entity
      Institution settlementEntity = req.getSettlementEntity();
      if (settlementEntity == null) {
         settlementEntity = new Institution();
         settlementEntity.setId("33333333");
         settlementEntity.setName("TransactionsRUs");
      }
      rsp.setSettlementEntity(settlementEntity);
      // receiver
      Institution receiver = req.getReceiver();
      if (receiver == null) {
         receiver = new Institution();
         receiver.setId("44444444");
         receiver.setName("GlobalGiftcards");
      }
      rsp.setReceiver(receiver);
      // thirdPartyIdentifiers
      List<ThirdPartyIdentifier> thirdPartyIds = req.getThirdPartyIdentifiers();
      if (thirdPartyIds == null) {
         new ArrayList<ThirdPartyIdentifier>();
      }
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(settlementEntity.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      thirdPartyIds.add(new ThirdPartyIdentifier().institutionId(receiver.getId()).transactionIdentifier(
            RandomData.random09AZ((int) ((Math.random() * 20) + 1))));
      rsp.setThirdPartyIdentifiers(thirdPartyIds);
      // amounts
      GiftcardAmounts amounts = new GiftcardAmounts();
      LedgerAmount balance = new LedgerAmount().amount(cardRecord.getBalance().getAmount()).currency("710");
      LedgerAmount availableBalance =
            new LedgerAmount().amount(cardRecord.getAvailableBalance().getAmount()).currency("710");
      amounts.setBalanceAmount(balance);
      amounts.setAvailableBalance(availableBalance);
      rsp.setAmounts(amounts);
      // card
      rsp.setCard(card);
      // posInfo
      rsp.setPosInfo(req.getPosInfo());
      // product
      String productId = cardRecord.getProductId();
      Product product = giftcardDb.getProductTable().getRecord(productId).getProduct();
      rsp.setProduct(product);
      // slipData
      messageLines = new ArrayList<SlipLine>();
      messageLines.add(new SlipLine().text("Your card has been voided."));
      messageLines.add(new SlipLine().text("For queries quote the issuer reference below:"));
      SlipData slipData = new SlipData();
      slipData.setMessageLines(messageLines);
      slipData.setIssuerReference(RandomData.random09AZ((int) ((Math.random() * 20) + 1)));
      rsp.setSlipData(slipData);
      return rsp;
   }

   public static ErrorDetail unableToLocateRecord(BasicAdvice advice) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) advice)));
      setErrorDetailIds(advice, errorDetail);
      errorDetail.setErrorType(ErrorType.UNABLE_TO_LOCATE_RECORD);
      errorDetail.setErrorMessage("No request record.");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate the original request for the given advice.");
      detailMessage.setRequestId(advice.getRequestId());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail duplicateRequest(Object req, String uuid) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.DUPLICATE_RECORD);
      errorDetail.setErrorMessage("Repeated UUID");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("A message has already been submitted with the given UUID:" + uuid);
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail exceptionResponse(Object req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Server Error");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("A server error occured while processing the request.");
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail generalError(Object req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Server Error");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("A server error occured while processing the request.");
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail requestBeingProcessed(BasicAdvice advice) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) advice)));
      setErrorDetailIds(advice, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Request in progress");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The request associated with this advice is currently being processed "
            + "and the advice cannot be processed. The advice should be resubmitted. "
            + "Note that this is conceivable for timeout reversals but if this is in response "
            + "to a confirmation it signifies a potential bug in the client since a "
            + "confirmation was submitted prior to receiving a response to the original " + "request.");
      detailMessage.setRequestId(advice.getRequestId());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestFailed(BasicAdvice advice) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) advice)));
      setErrorDetailIds(advice, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Request failed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original request which this advice pertains to failed. "
            + "This is okay for reversals but if this is in response to "
            + "a confirmation it signifies a potential bug in the "
            + "client since a confirmation was submitted without "
            + "receiving a successful response to the original request.");
      detailMessage.setRequestId(advice.getRequestId());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestConfirmed(
         ActivationReversal reversal,
         ActivationConfirmationRecord confirmationRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) reversal)));
      setErrorDetailIds(reversal, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_ACTIVATED);
      errorDetail.setErrorMessage("Request confirmed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original ActivationRequest which this ActivationReversal pertains to has already been confirmed.");
      detailMessage.setRequestId(reversal.getRequestId());
      detailMessage.setReversalId(reversal.getId());
      detailMessage.setReversalTime(reversal.getTime().toString());
      detailMessage.setConfirmationId(confirmationRecord.getActivationConfirmation().getId());
      detailMessage.setConfirmDate(confirmationRecord.getActivationConfirmation().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestReversed(
         ActivationConfirmation confirmation,
         ActivationReversalRecord reversalRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) confirmation)));
      setErrorDetailIds(confirmation, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Request reversed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original ActivationRequest which this ActivationConfirmation pertains to has already been reversed.");
      detailMessage.setRequestId(confirmation.getRequestId());
      detailMessage.setReversalId(confirmation.getId());
      detailMessage.setReversalTime(confirmation.getTime().toString());
      detailMessage.setConfirmationId(reversalRecord.getActivationReversal().getId());
      detailMessage.setConfirmDate(reversalRecord.getActivationReversal().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestConfirmed(LoadReversal reversal, LoadConfirmationRecord confirmationRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) reversal)));
      setErrorDetailIds(reversal, errorDetail);
      errorDetail.setErrorType(ErrorType.TRANSACTION_NOT_SUPPORTED);
      errorDetail.setErrorMessage("Request confirmed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original LoadRequest which this LoadReversal pertains to has already been confirmed.");
      detailMessage.setRequestId(reversal.getRequestId());
      detailMessage.setReversalId(reversal.getId());
      detailMessage.setReversalTime(reversal.getTime().toString());
      detailMessage.setConfirmationId(confirmationRecord.getLoadConfirmation().getId());
      detailMessage.setConfirmDate(confirmationRecord.getLoadConfirmation().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestReversed(LoadConfirmation confirmation, LoadReversalRecord reversalRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) confirmation)));
      setErrorDetailIds(confirmation, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Request reversed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original LoadRequest which this LoadConfirmation pertains to has already been reversed.");
      detailMessage.setRequestId(confirmation.getRequestId());
      detailMessage.setReversalId(confirmation.getId());
      detailMessage.setReversalTime(confirmation.getTime().toString());
      detailMessage.setConfirmationId(reversalRecord.getLoadReversal().getId());
      detailMessage.setConfirmDate(reversalRecord.getLoadReversal().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestConfirmed(
         RedemptionReversal reversal,
         RedemptionConfirmationRecord confirmationRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) reversal)));
      setErrorDetailIds(reversal, errorDetail);
      errorDetail.setErrorType(ErrorType.TRANSACTION_NOT_SUPPORTED);
      errorDetail.setErrorMessage("Request confirmed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original RedemptionRequest which this RedemptionReversal pertains to has already been confirmed.");
      detailMessage.setRequestId(reversal.getRequestId());
      detailMessage.setReversalId(reversal.getId());
      detailMessage.setReversalTime(reversal.getTime().toString());
      detailMessage.setConfirmationId(confirmationRecord.getRedemptionConfirmation().getId());
      detailMessage.setConfirmDate(confirmationRecord.getRedemptionConfirmation().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestReversed(
         RedemptionConfirmation confirmation,
         RedemptionReversalRecord reversalRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) confirmation)));
      setErrorDetailIds(confirmation, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Request reversed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original RedemptionRequest which this RedemptionConfirmation pertains to has already been reversed.");
      detailMessage.setRequestId(confirmation.getRequestId());
      detailMessage.setReversalId(confirmation.getId());
      detailMessage.setReversalTime(confirmation.getTime().toString());
      detailMessage.setConfirmationId(reversalRecord.getRedemptionReversal().getId());
      detailMessage.setConfirmDate(reversalRecord.getRedemptionReversal().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestConfirmed(VoidReversal reversal, VoidConfirmationRecord confirmationRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) reversal)));
      setErrorDetailIds(reversal, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_VOIDED);
      errorDetail.setErrorMessage("Request confirmed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original VoidRequest which this VoidReversal pertains to has already been confirmed.");
      detailMessage.setRequestId(reversal.getRequestId());
      detailMessage.setReversalId(reversal.getId());
      detailMessage.setReversalTime(reversal.getTime().toString());
      detailMessage.setConfirmationId(confirmationRecord.getVoidConfirmation().getId());
      detailMessage.setConfirmDate(confirmationRecord.getVoidConfirmation().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail originalRequestReversed(VoidConfirmation confirmation, VoidReversalRecord reversalRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) confirmation)));
      setErrorDetailIds(confirmation, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Request reversed");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The original VoidRequest which this VoidConfirmation pertains to has already been reversed.");
      detailMessage.setRequestId(confirmation.getRequestId());
      detailMessage.setReversalId(confirmation.getId());
      detailMessage.setReversalTime(confirmation.getTime().toString());
      detailMessage.setConfirmationId(reversalRecord.getVoidReversal().getId());
      detailMessage.setConfirmDate(reversalRecord.getVoidReversal().getTime().toString());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardIsNotYetActive(
         Object req,
         CardRecord cardRecord,
         ActivationRecord activationRecord,
         ActivationReversalRecord activationReversalRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_NOT_ACTIVATED);
      errorDetail.setErrorMessage("Not active");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to perform action because the card is not in an active state.");
      detailMessage.setCard(cardRecord.getCard());
      if (activationRecord != null) {
         detailMessage.setActivationId(activationRecord.getActivationRequest().getId());
         detailMessage.setRequestTime(activationRecord.getActivationRequest().getTime().toString());
      }
      if (activationReversalRecord != null) {
         detailMessage.setActivationId(activationReversalRecord.getActivationReversal().getId());
         detailMessage.setReversalTime(activationReversalRecord.getActivationReversal().getTime().toString());
      }
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardIsActive(Object req, CardRecord cardRecord, ActivationRecord activationRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_ACTIVATED);
      errorDetail.setErrorMessage("Card active");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to perform action because the card is already in an active state.");
      detailMessage.setCard(cardRecord.getCard());
      detailMessage.setActivationId(activationRecord.getActivationRequest().getId());
      String lastConfirmationId = activationRecord.getLastConfirmationId();
      if (lastConfirmationId != null) {
         detailMessage.setConfirmationId(lastConfirmationId);
      }
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardIsVoided(Object req, CardRecord cardRecord, VoidRecord voidRecord) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_VOIDED);
      errorDetail.setErrorMessage("Card voided");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to perform action because the card is already in a voided state.");
      detailMessage.setCard(cardRecord.getCard());
      detailMessage.setVoidId(voidRecord.getVoidRequest().getId());
      String lastConfirmationId = voidRecord.getLastConfirmationId();
      if (lastConfirmationId != null) {
         detailMessage.setConfirmationId(lastConfirmationId);
      }
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardNotFound(ActivationRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INVALID_CARD_NUMBER);
      errorDetail.setErrorMessage("Unknown card");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate card using supplied details.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardNotFound(LoadRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INVALID_CARD_NUMBER);
      errorDetail.setErrorMessage("Unknown card");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate card using supplied details.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardNotFound(LookupRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INVALID_CARD_NUMBER);
      errorDetail.setErrorMessage("Unknown card");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate card using supplied details.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardNotFound(RedemptionRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INVALID_CARD_NUMBER);
      errorDetail.setErrorMessage("Unknown card");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate card using supplied details.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardNotFound(VoidRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INVALID_CARD_NUMBER);
      errorDetail.setErrorMessage("Unknown card");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate card using supplied details.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpired(ActivationRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("This card has expired.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpired(LoadRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("This card has expired.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpired(RedemptionRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("This card has expired.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpired(VoidRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("This card has expired.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpiryInvalid(CardRecord cardRecord, ActivationRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The card expiry date provided does not match the expiry date on record. The expiry date on record is "
            + cardRecord.getCard().getExpiryDate());
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpiryInvalid(CardRecord cardRecord, LoadRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The card expiry date provided does not match the expiry date on record. The expiry date on record is "
            + cardRecord.getCard().getExpiryDate());
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpiryInvalid(CardRecord cardRecord, RedemptionRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.CARD_EXPIRED);
      errorDetail.setErrorMessage("Card expired");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The card expiry date provided does not match the expiry date on record. The expiry date on record is "
            + cardRecord.getCard().getExpiryDate());
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail cardExpiryInvalid(CardRecord cardRecord, VoidRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.GENERAL_ERROR);
      errorDetail.setErrorMessage("Wrong expiry date");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The card expiry date provided does not match the expiry date on record. The expiry date on record is "
            + cardRecord.getCard().getExpiryDate());
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(req.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail productNotFound(ActivationRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INVALID_PRODUCT);
      errorDetail.setErrorMessage("Unknown product");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("Unable to locate product using supplied details.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setProduct(req.getProduct());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail insufficientFunds(CardRecord cardRecord, RedemptionRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INSUFFICIENT_FUNDS);
      errorDetail.setErrorMessage("Insufficient funds");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString("The card does not have enough funds to approve the request.");
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(cardRecord.getCard());
      GiftcardAmounts reqAmounts = req.getAmounts();
      GiftcardAmounts dmAmounts = new GiftcardAmounts();
      String currency = reqAmounts.getRequestAmount().getCurrency();
      dmAmounts.setApprovedAmount(new LedgerAmount().amount(0l).currency(currency));
      dmAmounts.setRequestAmount(new LedgerAmount().amount(reqAmounts.getRequestAmount().getAmount())
            .currency(currency));
      dmAmounts.setBalanceAmount(new LedgerAmount().amount(cardRecord.getBalance().getAmount()).currency(currency));
      detailMessage.setAmounts(dmAmounts);
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   public static ErrorDetail incorrectPin(CardRecord cardRecord, RedemptionRequest req) {
      ErrorDetail errorDetail = new ErrorDetail();
      errorDetail.setRequestType(getTransactionRequestType(((Object) req)));
      setErrorDetailIds(req, errorDetail);
      errorDetail.setErrorType(ErrorType.INCORRECT_PIN);
      errorDetail.setErrorMessage("Incorrect PIN");
      DetailMessage detailMessage = new DetailMessage();
      detailMessage.setFreeString(String.format(
            "The PIN attempt was incorrect. " + "You submitted %s (clear) or %s (encrypted). "
                  + "The expected details are shown in the card field.",
            req.getCard().getClearPin(),
            req.getCard().getEncryptedPin()));
      detailMessage.setRequestId(req.getId());
      detailMessage.setCard(cardRecord.getCard());
      errorDetail.setDetailMessage(detailMessage);
      return errorDetail;
   }

   private static <T> Set<ConstraintViolation<T>> validate(T tInstance) {
      if (tInstance == null) {
         return new HashSet<ConstraintViolation<T>>();
      }
      Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      Set<ConstraintViolation<T>> violations = validator.validate(tInstance);
      return violations;
   }

   public static Response validateActivationRequest(ActivationRequest activationRequest) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(activationRequest));
      if (activationRequest != null) {
         // id
         violations.addAll(validate(activationRequest.getId()));
         // time
         violations.addAll(validate(activationRequest.getTime()));
         // originator
         Originator originator = activationRequest.getOriginator();
         violations.addAll(validate(originator));
         if (originator != null) {
            violations.addAll(validate(originator.getInstitution()));
            violations.addAll(validate(originator.getTerminalId()));
            Merchant merchant = originator.getMerchant();
            violations.addAll(validate(merchant));
            if (merchant != null) {
               violations.addAll(validate(merchant.getMerchantId()));
               violations.addAll(validate(merchant.getMerchantType()));
               violations.addAll(validate(merchant.getMerchantName()));
            }
         }
         // client
         violations.addAll(validate(activationRequest.getClient()));
         // settlement entity
         violations.addAll(validate(activationRequest.getSettlementEntity()));
         // receiver
         violations.addAll(validate(activationRequest.getReceiver()));
         // thirdPartyIdentifiers
         List<ThirdPartyIdentifier> thirdPartyIdentifiers = activationRequest.getThirdPartyIdentifiers();
         violations.addAll(validate(thirdPartyIdentifiers));
         if (thirdPartyIdentifiers != null && thirdPartyIdentifiers.size() > 0) {
            for (ThirdPartyIdentifier thirdPartyIdentifier : thirdPartyIdentifiers) {
               violations.addAll(validate(thirdPartyIdentifier));
            }
         }
         // amounts
         GiftcardAmounts amounts = activationRequest.getAmounts();
         violations.addAll(validate(amounts));
         if (amounts != null) {
            violations.addAll(validate(amounts.getRequestAmount()));
         }
         // card
         violations.addAll(validate(activationRequest.getCard()));
         // posInfo
         PosInfo posInfo = activationRequest.getPosInfo();
         violations.addAll(validate(posInfo));
         if (posInfo != null) {
            violations.addAll(validate(posInfo.getEntryMode()));
         }
         // product
         violations.addAll(validate(activationRequest.getProduct()));
      }
      return buildFormatErrorRsp(violations);
   }

   public static Response validateActivationReversal(ActivationReversal activationReversal) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(activationReversal));
      violations.addAll(validate(activationReversal.getId()));
      violations.addAll(validate(activationReversal.getRequestId()));
      violations.addAll(validate(activationReversal.getReversalReason()));
      violations.addAll(validate(activationReversal.getThirdPartyIdentifiers()));
      violations.addAll(validate(activationReversal.getTime()));
      return buildFormatErrorRsp(violations);
   }

   public static Response validateActivationConfirmation(ActivationConfirmation activationConfirmation) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(activationConfirmation));
      violations.addAll(validate(activationConfirmation.getId()));
      violations.addAll(validate(activationConfirmation.getRequestId()));
      violations.addAll(validate(activationConfirmation.getThirdPartyIdentifiers()));
      violations.addAll(validate(activationConfirmation.getTime()));
      List<Tender> tenders = activationConfirmation.getTenders();
      violations.addAll(validate(tenders));
      for (Tender tender : tenders) {
         violations.addAll(validate(tender));
      }
      return buildFormatErrorRsp(violations);
   }

   public static Response validateLoadRequest(LoadRequest loadRequest) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(loadRequest));
      if (loadRequest != null) {
         // id
         violations.addAll(validate(loadRequest.getId()));
         // time
         violations.addAll(validate(loadRequest.getTime()));
         // originator
         Originator originator = loadRequest.getOriginator();
         violations.addAll(validate(originator));
         if (originator != null) {
            violations.addAll(validate(originator.getInstitution()));
            violations.addAll(validate(originator.getTerminalId()));
            Merchant merchant = originator.getMerchant();
            violations.addAll(validate(merchant));
            if (merchant != null) {
               violations.addAll(validate(merchant.getMerchantId()));
               violations.addAll(validate(merchant.getMerchantType()));
               violations.addAll(validate(merchant.getMerchantName()));
            }
         }
         // client
         violations.addAll(validate(loadRequest.getClient()));
         // settlement entity
         violations.addAll(validate(loadRequest.getSettlementEntity()));
         // receiver
         violations.addAll(validate(loadRequest.getReceiver()));
         // thirdPartyIdentifiers
         List<ThirdPartyIdentifier> thirdPartyIdentifiers = loadRequest.getThirdPartyIdentifiers();
         violations.addAll(validate(thirdPartyIdentifiers));
         if (thirdPartyIdentifiers != null && thirdPartyIdentifiers.size() > 0) {
            for (ThirdPartyIdentifier thirdPartyIdentifier : thirdPartyIdentifiers) {
               violations.addAll(validate(thirdPartyIdentifier));
            }
         }
         // card
         violations.addAll(validate(loadRequest.getCard()));
         // posInfo
         PosInfo posInfo = loadRequest.getPosInfo();
         violations.addAll(validate(posInfo));
         if (posInfo != null) {
            violations.addAll(validate(posInfo.getEntryMode()));
         }
         // product
         violations.addAll(validate(loadRequest.getProduct()));
      }
      return buildFormatErrorRsp(violations);
   }

   public static Response validateLoadReversal(LoadReversal loadReversal) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(loadReversal));
      violations.addAll(validate(loadReversal.getId()));
      violations.addAll(validate(loadReversal.getRequestId()));
      violations.addAll(validate(loadReversal.getReversalReason()));
      violations.addAll(validate(loadReversal.getThirdPartyIdentifiers()));
      violations.addAll(validate(loadReversal.getTime()));
      return buildFormatErrorRsp(violations);
   }

   public static Response validateLoadConfirmation(LoadConfirmation loadConfirmation) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(loadConfirmation));
      violations.addAll(validate(loadConfirmation.getId()));
      violations.addAll(validate(loadConfirmation.getRequestId()));
      violations.addAll(validate(loadConfirmation.getThirdPartyIdentifiers()));
      violations.addAll(validate(loadConfirmation.getTime()));
      return buildFormatErrorRsp(violations);
   }

   public static Response validateLookupRequest(LookupRequest lookupRequest) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(lookupRequest));
      if (lookupRequest != null) {
         // id
         violations.addAll(validate(lookupRequest.getId()));
         // time
         violations.addAll(validate(lookupRequest.getTime()));
         // originator
         Originator originator = lookupRequest.getOriginator();
         violations.addAll(validate(originator));
         if (originator != null) {
            violations.addAll(validate(originator.getInstitution()));
            violations.addAll(validate(originator.getTerminalId()));
            Merchant merchant = originator.getMerchant();
            violations.addAll(validate(merchant));
            if (merchant != null) {
               violations.addAll(validate(merchant.getMerchantId()));
               violations.addAll(validate(merchant.getMerchantType()));
               violations.addAll(validate(merchant.getMerchantName()));
            }
         }
         // client
         violations.addAll(validate(lookupRequest.getClient()));
         // settlement entity
         violations.addAll(validate(lookupRequest.getSettlementEntity()));
         // receiver
         violations.addAll(validate(lookupRequest.getReceiver()));
         // thirdPartyIdentifiers
         List<ThirdPartyIdentifier> thirdPartyIdentifiers = lookupRequest.getThirdPartyIdentifiers();
         violations.addAll(validate(thirdPartyIdentifiers));
         if (thirdPartyIdentifiers != null && thirdPartyIdentifiers.size() > 0) {
            for (ThirdPartyIdentifier thirdPartyIdentifier : thirdPartyIdentifiers) {
               violations.addAll(validate(thirdPartyIdentifier));
            }
         }
         // card
         violations.addAll(validate(lookupRequest.getCard()));
         // posInfo
         PosInfo posInfo = lookupRequest.getPosInfo();
         violations.addAll(validate(posInfo));
         if (posInfo != null) {
            violations.addAll(validate(posInfo.getEntryMode()));
         }
      }
      return buildFormatErrorRsp(violations);
   }

   public static Response validateRedemptionRequest(RedemptionRequest redemptionRequest) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(redemptionRequest));
      if (redemptionRequest != null) {
         // id
         violations.addAll(validate(redemptionRequest.getId()));
         // time
         violations.addAll(validate(redemptionRequest.getTime()));
         // originator
         Originator originator = redemptionRequest.getOriginator();
         violations.addAll(validate(originator));
         if (originator != null) {
            violations.addAll(validate(originator.getInstitution()));
            violations.addAll(validate(originator.getTerminalId()));
            Merchant merchant = originator.getMerchant();
            violations.addAll(validate(merchant));
            if (merchant != null) {
               violations.addAll(validate(merchant.getMerchantId()));
               violations.addAll(validate(merchant.getMerchantType()));
               violations.addAll(validate(merchant.getMerchantName()));
            }
         }
         // client
         violations.addAll(validate(redemptionRequest.getClient()));
         // settlement entity
         violations.addAll(validate(redemptionRequest.getSettlementEntity()));
         // receiver
         violations.addAll(validate(redemptionRequest.getReceiver()));
         // thirdPartyIdentifiers
         List<ThirdPartyIdentifier> thirdPartyIdentifiers = redemptionRequest.getThirdPartyIdentifiers();
         violations.addAll(validate(thirdPartyIdentifiers));
         if (thirdPartyIdentifiers != null && thirdPartyIdentifiers.size() > 0) {
            for (ThirdPartyIdentifier thirdPartyIdentifier : thirdPartyIdentifiers) {
               violations.addAll(validate(thirdPartyIdentifier));
            }
         }
         // card
         violations.addAll(validate(redemptionRequest.getCard()));
         // posInfo
         PosInfo posInfo = redemptionRequest.getPosInfo();
         violations.addAll(validate(posInfo));
         if (posInfo != null) {
            violations.addAll(validate(posInfo.getEntryMode()));
         }
         // product
         violations.addAll(validate(redemptionRequest.getProduct()));
      }
      return buildFormatErrorRsp(violations);
   }

   public static Response validateRedemptionReversal(RedemptionReversal redemptionReversal) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(redemptionReversal));
      violations.addAll(validate(redemptionReversal.getId()));
      violations.addAll(validate(redemptionReversal.getRequestId()));
      violations.addAll(validate(redemptionReversal.getReversalReason()));
      violations.addAll(validate(redemptionReversal.getThirdPartyIdentifiers()));
      violations.addAll(validate(redemptionReversal.getTime()));
      return buildFormatErrorRsp(violations);
   }

   public static Response validateRedemptionConfirmation(RedemptionConfirmation redemptionConfirmation) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(redemptionConfirmation));
      violations.addAll(validate(redemptionConfirmation.getId()));
      violations.addAll(validate(redemptionConfirmation.getRequestId()));
      violations.addAll(validate(redemptionConfirmation.getThirdPartyIdentifiers()));
      violations.addAll(validate(redemptionConfirmation.getTime()));
      return buildFormatErrorRsp(violations);
   }

   public static Response validateVoidRequest(VoidRequest voidRequest) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(voidRequest));
      if (voidRequest != null) {
         // id
         violations.addAll(validate(voidRequest.getId()));
         // time
         violations.addAll(validate(voidRequest.getTime()));
         // originator
         Originator originator = voidRequest.getOriginator();
         violations.addAll(validate(originator));
         if (originator != null) {
            violations.addAll(validate(originator.getInstitution()));
            violations.addAll(validate(originator.getTerminalId()));
            Merchant merchant = originator.getMerchant();
            violations.addAll(validate(merchant));
            if (merchant != null) {
               violations.addAll(validate(merchant.getMerchantId()));
               violations.addAll(validate(merchant.getMerchantType()));
               violations.addAll(validate(merchant.getMerchantName()));
            }
         }
         // client
         violations.addAll(validate(voidRequest.getClient()));
         // settlement entity
         violations.addAll(validate(voidRequest.getSettlementEntity()));
         // receiver
         violations.addAll(validate(voidRequest.getReceiver()));
         // thirdPartyIdentifiers
         List<ThirdPartyIdentifier> thirdPartyIdentifiers = voidRequest.getThirdPartyIdentifiers();
         violations.addAll(validate(thirdPartyIdentifiers));
         if (thirdPartyIdentifiers != null && thirdPartyIdentifiers.size() > 0) {
            for (ThirdPartyIdentifier thirdPartyIdentifier : thirdPartyIdentifiers) {
               violations.addAll(validate(thirdPartyIdentifier));
            }
         }
         // card
         violations.addAll(validate(voidRequest.getCard()));
         // posInfo
         PosInfo posInfo = voidRequest.getPosInfo();
         violations.addAll(validate(posInfo));
         if (posInfo != null) {
            violations.addAll(validate(posInfo.getEntryMode()));
         }
         // product
         violations.addAll(validate(voidRequest.getProduct()));
      }
      return buildFormatErrorRsp(violations);
   }

   public static Response validateVoidReversal(VoidReversal voidReversal) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(voidReversal));
      violations.addAll(validate(voidReversal.getId()));
      violations.addAll(validate(voidReversal.getRequestId()));
      violations.addAll(validate(voidReversal.getReversalReason()));
      violations.addAll(validate(voidReversal.getThirdPartyIdentifiers()));
      violations.addAll(validate(voidReversal.getTime()));
      return buildFormatErrorRsp(violations);
   }

   public static Response validateVoidConfirmation(VoidConfirmation voidConfirmation) {
      Set<ConstraintViolation<?>> violations = new HashSet<ConstraintViolation<?>>();
      violations.addAll(validate(voidConfirmation));
      violations.addAll(validate(voidConfirmation.getId()));
      violations.addAll(validate(voidConfirmation.getRequestId()));
      violations.addAll(validate(voidConfirmation.getThirdPartyIdentifiers()));
      violations.addAll(validate(voidConfirmation.getTime()));
      return buildFormatErrorRsp(violations);
   }

   private static Response buildFormatErrorRsp(Set<ConstraintViolation<?>> violations) {
      if (violations.size() == 0) {
         return null;
      }
      List<FormatError> formatErrors = new ArrayList<FormatError>(violations.size());
      int i = 0;
      for (ConstraintViolation violation : violations) {
         System.out.println(i);
         formatErrors.add(new FormatError().msg(violation.getMessage())
               .field(violation.getPropertyPath().toString())
               .value(violation.getInvalidValue() == null ? "null" : violation.getInvalidValue().toString()));
         i++;
      }
      ErrorDetail errorDetail =
            new ErrorDetail().errorType(ErrorType.FORMAT_ERROR)
                  .errorMessage("Bad formatting")
                  .detailMessage(new DetailMessage().formatErrors(formatErrors));
      return Response.status(400).entity(errorDetail).build();
   }

   public static Response isUuidConsistent(String uuid, ActivationRequest activationReq) {
      Response rsp = null;
      String pathId = uuid;
      String objectId = activationReq.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setRequestId(objectId);
         rsp = Response.status(400).entity(errorDetail).build();
      }
      return rsp;
   }

   public static Response isUuidConsistent(String requestUuid, String reversalUuid, ActivationReversal activationRev) {
      String pathId = reversalUuid;
      String objectId = activationRev.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setReversalId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(
         String requestUuid,
         String confirmationUuid,
         ActivationConfirmation activationConfirmation) {
      String pathId = confirmationUuid;
      String objectId = activationConfirmation.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setConfirmationId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(String uuid, LoadRequest loadReq) {
      Response rsp = null;
      String pathId = uuid;
      String objectId = loadReq.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setRequestId(objectId);
         rsp = Response.status(400).entity(errorDetail).build();
      }
      return rsp;
   }

   public static Response isUuidConsistent(String requestUuid, String reversalUuid, LoadReversal loadRev) {
      String pathId = reversalUuid;
      String objectId = loadRev.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setReversalId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(
         String requestUuid,
         String confirmationUuid,
         LoadConfirmation loadConfirmation) {
      String pathId = confirmationUuid;
      String objectId = loadConfirmation.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setConfirmationId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(String uuid, LookupRequest lookupReq) {
      Response rsp = null;
      String pathId = uuid;
      String objectId = lookupReq.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setRequestId(objectId);
         rsp = Response.status(400).entity(errorDetail).build();
      }
      return rsp;
   }

   public static Response isUuidConsistent(String uuid, RedemptionRequest redemptionReq) {
      Response rsp = null;
      String pathId = uuid;
      String objectId = redemptionReq.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setRequestId(objectId);
         rsp = Response.status(400).entity(errorDetail).build();
      }
      return rsp;
   }

   public static Response isUuidConsistent(String requestUuid, String reversalUuid, RedemptionReversal redemptionRev) {
      String pathId = reversalUuid;
      String objectId = redemptionRev.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setReversalId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(
         String requestUuid,
         String confirmationUuid,
         RedemptionConfirmation redemptionConfirmation) {
      String pathId = confirmationUuid;
      String objectId = redemptionConfirmation.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setConfirmationId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(String uuid, VoidRequest voidReq) {
      Response rsp = null;
      String pathId = uuid;
      String objectId = voidReq.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setRequestId(objectId);
         rsp = Response.status(400).entity(errorDetail).build();
      }
      return rsp;
   }

   public static Response isUuidConsistent(String requestUuid, String reversalUuid, VoidReversal voidRev) {
      String pathId = reversalUuid;
      String objectId = voidRev.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setReversalId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static Response isUuidConsistent(
         String requestUuid,
         String confirmationUuid,
         VoidConfirmation voidConfirmation) {
      String pathId = confirmationUuid;
      String objectId = voidConfirmation.getId();
      ErrorDetail errorDetail = isUuidConsistent(pathId, objectId);
      if (errorDetail != null) {
         DetailMessage detailMessage = (DetailMessage) errorDetail.getDetailMessage();
         detailMessage.setConfirmationId(objectId);
         return Response.status(400).entity(errorDetail).build();
      }
      return null;
   }

   public static ErrorDetail isUuidConsistent(String pathId, String objectId) {
      ErrorDetail errorDetail = null;
      if (!pathId.equals(objectId)) {
         errorDetail = new ErrorDetail().errorType(ErrorType.FORMAT_ERROR).errorMessage("UUID inconsistent");
         DetailMessage detailMessage = new DetailMessage();
         detailMessage.setPathId(pathId);
         detailMessage.setFreeString("The ID path parameter is not the same as the object's ID.");
         errorDetail.setDetailMessage(detailMessage);
      }
      return errorDetail;
   }

   public static String getAuthString(String authHeader) {
      if (authHeader == null || authHeader.isEmpty() || !authHeader.startsWith("Basic ")) {
         return null;
      }
      String credsSubstring = authHeader.substring("Basic ".length());
      String usernameAndPassword = Base64.decodeAsString(credsSubstring);
      return usernameAndPassword;
   }

   public static String getUsernameFromAuth(String authString) {
      String username = "null";
      if (authString != null && !authString.isEmpty()) {
         username = authString.substring(0, authString.indexOf(':'));
      }
      return username;
   }

   public static String getPasswordFromAuth(String authString) {
      String password = "null";
      if (authString != null && !authString.isEmpty()) {
         password = authString.substring(authString.indexOf(':') + 1);
      }
      return password;
   }

   public static void setErrorDetailIds(Object req, ErrorDetail errorDetail) {
      if (req instanceof BasicAdvice) {
         errorDetail.setId(((BasicAdvice) req).getId());
         errorDetail.setOriginalId(((BasicAdvice) req).getRequestId());
      } else {
         errorDetail.setId(((Transaction) req).getId());
      }
   }

   public static ErrorDetail.RequestType getTransactionRequestType(Object obj) {
      if (obj instanceof Transaction) {
         return getTransactionRequestType((Transaction) obj);
      } else {
         return getTransactionRequestType((BasicAdvice) obj);
      }
   }

   public static ErrorDetail.RequestType getTransactionRequestType(Transaction transaction) {
      if (transaction instanceof ActivationRequest)
         return ErrorDetail.RequestType.ACTIVATION_REQUEST;
      else if (transaction instanceof LoadRequest)
         return ErrorDetail.RequestType.LOAD_REQUEST;
      else if (transaction instanceof LookupRequest)
         return ErrorDetail.RequestType.LOOKUP_REQUEST;
      else if (transaction instanceof RedemptionRequest)
         return ErrorDetail.RequestType.REDEMPTION_REQUEST;
      else
         return ErrorDetail.RequestType.VOID_REQUEST;
   }

   public static ErrorDetail.RequestType getTransactionRequestType(BasicAdvice transaction) {
      if (transaction instanceof ActivationConfirmation)
         return ErrorDetail.RequestType.ACTIVATION_CONFIRMATION;
      else if (transaction instanceof ActivationReversal)
         return ErrorDetail.RequestType.ACTIVATION_REVERSAL;
      else if (transaction instanceof LoadConfirmation)
         return ErrorDetail.RequestType.LOAD_CONFIRMATION;
      else if (transaction instanceof LoadReversal)
         return ErrorDetail.RequestType.LOAD_REVERSAL;
      else if (transaction instanceof RedemptionConfirmation)
         return ErrorDetail.RequestType.REDEMPTION_CONFIRMATION;
      else if (transaction instanceof RedemptionReversal)
         return ErrorDetail.RequestType.REDEMPTION_REVERSAL;
      else if (transaction instanceof VoidConfirmation)
         return ErrorDetail.RequestType.VOID_CONFIRMATION;
      else
         return ErrorDetail.RequestType.VOID_REVERSAL;
   }
}
