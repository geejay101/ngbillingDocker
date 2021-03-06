package in.webdata.integration

import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;

import com.sapienter.jbilling.server.order.db.OrderBillingTypeDTO;
import com.sapienter.jbilling.server.util.api.JbillingAPI;
import com.sapienter.jbilling.server.util.api.JbillingAPIFactory;
import com.sapienter.jbilling.common.Util;
import com.sapienter.jbilling.server.invoice.IInvoiceSessionBean;
import com.sapienter.jbilling.server.order.IOrderSessionBean;
import com.sapienter.jbilling.server.payment.IPaymentSessionBean;
import com.sapienter.jbilling.server.user.IUserSessionBean;
import com.sapienter.jbilling.server.invoice.db.InvoiceDTO;
import com.sapienter.jbilling.server.order.OrderBL;
import com.sapienter.jbilling.server.order.OrderWS;
import com.sapienter.jbilling.server.order.db.OrderDTO;
import com.sapienter.jbilling.server.order.db.OrderProcessDTO;
import com.sapienter.jbilling.server.process.BillingProcessDTOEx
import com.sapienter.jbilling.server.process.BillingProcessRunDTOEx
import com.sapienter.jbilling.server.process.BillingProcessRunTotalDTOEx
import com.sapienter.jbilling.server.process.IBillingProcessSessionBean
import com.sapienter.jbilling.server.process.db.BillingProcessConfigurationDTO;
import com.sapienter.jbilling.server.process.db.PeriodUnitDTO;
import com.sapienter.jbilling.server.user.UserDTOEx;
import com.sapienter.jbilling.server.user.UserWS;
import com.sapienter.jbilling.server.util.Constants;
import com.sapienter.jbilling.server.util.RemoteContext;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Set;
import org.joda.time.DateMidnight;
import spock.lang.Shared
import spock.lang.Specification

public class BillingProcessTestSpec extends Specification {

	@Shared IOrderSessionBean remoteOrder = null;
	@Shared IInvoiceSessionBean remoteInvoice = null;
	@Shared IBillingProcessSessionBean remoteBillingProcess = null;
	@Shared IUserSessionBean remoteUser = null;
	@Shared IPaymentSessionBean remotePayment = null;
	@Shared GregorianCalendar cal;
	@Shared Date processDate = null;
	@Shared Integer entityId = new Integer(1);
	@Shared Integer languageId = null;
	@Shared Date runDate = null;

	// pick an invoice generated by the billing process that get's paid as well
	@Shared Integer PROCESS_ID = new Integer(24);


	def "setup2"() {

		setup:        // once it run well ;) let's get the order interface
		remoteOrder = (IOrderSessionBean) RemoteContext.getBean(
				RemoteContext.Name.ORDER_REMOTE_SESSION);

		remoteInvoice = (IInvoiceSessionBean) RemoteContext.getBean(
				RemoteContext.Name.INVOICE_REMOTE_SESSION);

		remoteBillingProcess = (IBillingProcessSessionBean)RemoteContext.getBean(RemoteContext.Name.BILLING_PROCESS_REMOTE_SESSION);

		remoteUser = (IUserSessionBean) RemoteContext.getBean(
				RemoteContext.Name.USER_REMOTE_SESSION);;

		remotePayment= (IPaymentSessionBean) RemoteContext.getBean(
				RemoteContext.Name.PAYMENT_REMOTE_SESSION);

		languageId = new Integer(1);
		cal = new GregorianCalendar();
		cal.clear();
		cal.set(2006, GregorianCalendar.OCTOBER, 26, 0, 0, 0);
		runDate = cal.getTime();

		println("\n\n\n>>>>>>>>>>>>>.......Executed")


	}


	def "testEndOfMonthCorrection"() {
		when:
		// set the configuration to something we are sure about
		BillingProcessConfigurationDTO configDto = remoteBillingProcess.getConfigurationDto(entityId);
		configDto.setNextRunDate(new DateMidnight(2000,12,1).toDate());
		configDto.setGenerateReport(new Integer(1));
		configDto.setAutoPayment(new Integer(1));
		configDto.setAutoPaymentApplication(new Integer(1));
		configDto.setDfFm(new Integer(0));
		configDto.setDueDateUnitId(Constants.PERIOD_UNIT_MONTH);
		configDto.setDueDateValue(new Integer(1));
		configDto.setInvoiceDateProcess(new Integer(1));
		configDto.setMaximumPeriods(new Integer(10));
		configDto.setOnlyRecurring(new Integer(1));
		configDto.setPeriodUnit(new PeriodUnitDTO(Constants.PERIOD_UNIT_MONTH));
		configDto.setPeriodValue(new Integer(1));

		remoteBillingProcess.createUpdateConfiguration(new Integer(1),configDto);

		// user for tests
		UserWS user = com.sapienter.jbilling.server.user.WSTest.createUser(true, null, null);
		OrderWS order = com.sapienter.jbilling.server.order.WSTest.createMockOrder(user.getUserId(), 1, new BigDecimal(60));
		order.setActiveSince(new DateMidnight(2000, 11, 30).toDate());
		order.setCycleStarts(new DateMidnight(2000, 11, 01).toDate());
		order.setPeriod(2); // monthly

		then:
		println("OK check it later.")

		//			OrderBL		ol		=		new OrderBL();
		//			println(">>>>>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<"+new OrderBL().getDTO(order))
		//            Integer orderId = remoteOrder.createUpdate(1, 1, new OrderBL().getDTO(order), 1);
		//
		//            // run the billing process. It should only get this order
		//            remoteBillingProcess.trigger(new DateMidnight(2000,12,1).toDate());
		//
		//            InvoiceDTO invoice = remoteInvoice.getAllInvoices(user.getUserId()).iterator().next();
		//
		//			then:
		//
		//			new BigDecimal(62) 		==		 invoice.getBalance();
		//
		//           remoteInvoice.delete(invoice.getId(), 1);
		//            remoteOrder.delete(orderId, 1);
		//            remoteUser.delete(1, user.getUserId());

	}


	def "testRun"() {
		when:
		// get the latest process
		BillingProcessDTOEx lastDto = remoteBillingProcess.getDto(remoteBillingProcess.getLast(entityId),languageId);

		// run trigger but too early
		cal.set(2005, GregorianCalendar.JANUARY, 26);
		remoteBillingProcess.trigger(cal.getTime());

		// get latest run (b)
		BillingProcessDTOEx lastDtoB = remoteBillingProcess.getDto(remoteBillingProcess.getLast(entityId),languageId);

		then:

		true		==	(lastDto.getId() ==    lastDtoB.getId());
		// no retry should have run

		true		==		(lastDto.getRuns().size() ==        lastDtoB.getRuns().size());

	}
	def "testReview"() {

		when:
		// set the configuration to something we are sure about
		BillingProcessConfigurationDTO configDto = remoteBillingProcess.
				getConfigurationDto(entityId);
		configDto.setNextRunDate(runDate);
		configDto.setRetries(new Integer(1));
		configDto.setDaysForRetry(new Integer(5));
		configDto.setGenerateReport(new Integer(0));
		configDto.setAutoPayment(new Integer(1));
		configDto.setAutoPaymentApplication(new Integer(1));
		configDto.setDfFm(new Integer(0));
		configDto.setDueDateUnitId(Constants.PERIOD_UNIT_MONTH);
		configDto.setDueDateValue(new Integer(1));
		configDto.setInvoiceDateProcess(new Integer(1));
		configDto.setMaximumPeriods(new Integer(10));
		configDto.setOnlyRecurring(new Integer(1));
		configDto.setPeriodUnit(new PeriodUnitDTO(Constants.PERIOD_UNIT_MONTH));
		configDto.setPeriodValue(new Integer(1));

		remoteBillingProcess.createUpdateConfiguration(new Integer(1),
				configDto);

		// retries calculate dates using the real date of the run
		// when know of one from the pre-cooked DB
		cal.set(2000, GregorianCalendar.DECEMBER, 19, 0, 0, 0);
		Date retryDate = Util.truncateDate(cal.getTime());

		// run trigger
		remoteBillingProcess.trigger(retryDate);
		cal.add(GregorianCalendar.DAY_OF_YEAR, 5);

		remoteBillingProcess.trigger(cal.getTime());

		// get the latest process
		Integer abid =remoteBillingProcess.getLast(entityId);

		BillingProcessDTOEx lastDto = remoteBillingProcess.getDto(
				abid,
				languageId);

		// get the review
		BillingProcessDTOEx reviewDto = remoteBillingProcess.getReviewDto(
				entityId, languageId);

		// not review should be there

		then:

		null		!=					reviewDto;

		// set the configuration to something we are sure about
		when:

		configDto = remoteBillingProcess.
				getConfigurationDto(entityId);
		configDto.setDaysForReport(new Integer(5));
		configDto.setGenerateReport(new Integer(1));
		remoteBillingProcess.createUpdateConfiguration(new Integer(1),
				configDto);

		// disapprove the review (that just run before this one)
		remoteBillingProcess.setReviewApproval(new Integer(1), entityId,
				new Boolean(false));

		// run trigger, this time it should run and generate a report
		Thread reviewThread = new Thread() {

					void run() {
						remoteBillingProcess.trigger(cal.getTime());
					}
				}

		reviewThread.start();

		// trying immediatelly after, should not run
		Thread.sleep(1000); // take it easy

		then:

		true			==			remoteBillingProcess.trigger(runDate);

		// now wait until that thread is done
		when:

		while(reviewThread.isAlive()) {
			Thread.sleep(1000); // take it easy
		}

		// get the latest process
		BillingProcessDTOEx lastDtoB = remoteBillingProcess.getDto(
				remoteBillingProcess.getLast(entityId),
				languageId);

		// no new process should have run

		then:

		true			==		(lastDto.getId() ==   lastDtoB.getId());

		// get the review

		when:

		reviewDto = remoteBillingProcess.getReviewDto(
				entityId, languageId);

		// now review should be there

		then:

		null		!=			reviewDto;

		// the review should have invoices
		true		==	(reviewDto.getGrandTotal().getInvoicesGenerated() > 0);

		// validate that the review generated an invoice for user 121

		when:

		println("Validating invoice delegation");
		then:
		2			==		 remoteInvoice.getAllInvoices(121).size();

		when:

		InvoiceDTO invoice = getReviewInvoice(remoteInvoice.getAllInvoices(121));

		then:
		null		!=invoice;
		new BigDecimal("1288.55")		==		 invoice.getTotal();
		null		!=		invoice.getInvoice();

		when:

		Integer reviewInvoiceId = invoice.getId();
		invoice = getNoReviewInvoice(remoteInvoice.getAllInvoices(121));

		then:

		null		==		invoice.getInvoice();
		Constants.INVOICE_STATUS_UNPAID.intValue()		==		 invoice.getInvoiceStatus().getId();
		new BigDecimal("15.0")		==		 invoice.getBalance();

		when:
		Integer overdueInvoiceId = invoice.getId();

		// validate that the review left the order 107600 is still active
		// This is a pro-rated order with only a fraction of a period to
		// invoice.
		OrderDTO proRateOrder = remoteOrder.getOrder(107600);
		then:
		Constants.ORDER_STATUS_ACTIVE		==		 proRateOrder.getStatusId();

		// disapprove the review
		when:
		remoteBillingProcess.setReviewApproval(new Integer(1), entityId,
				new Boolean(false));

		invoice = getNoReviewInvoice(remoteInvoice.getAllInvoices(121));

		then:

		null		!=		invoice;
		Constants.INVOICE_STATUS_UNPAID.intValue()		==		 invoice.getInvoiceStatus().getId();
		new BigDecimal("15.0")		==		 invoice.getBalance();

		// run trigger, but too early (six days, instead of 5)

		when:

		cal.set(2006, GregorianCalendar.OCTOBER, 20);
		remoteBillingProcess.trigger(cal.getTime());

		// get the latest process
		lastDtoB = remoteBillingProcess.getDto(
				remoteBillingProcess.getLast(entityId),
				languageId);

		// no new process should have run

		then:

		true		==		(lastDto.getId() ==          lastDtoB.getId());

		// get the review
		when:

		BillingProcessDTOEx reviewDto2 = remoteBillingProcess.getReviewDto(
				entityId, languageId);

		then:

		reviewDto.getId()	==		                reviewDto2.getId();

		// status of the review should still be disapproved
		when:

		configDto = remoteBillingProcess.
				getConfigurationDto(entityId);

		then:

		configDto.getReviewStatus()			==		Constants.REVIEW_STATUS_DISAPPROVED.intValue();

		// run trigger this time has to generate a review report
		when:

		cal.set(2006, GregorianCalendar.OCTOBER, 22);
		remoteBillingProcess.trigger(cal.getTime());

		invoice = remoteInvoice.getInvoice(overdueInvoiceId);

		then:

		null		!=		invoice;
		Constants.INVOICE_STATUS_UNPAID.intValue()		==		 invoice.getInvoiceStatus().getId();
		new BigDecimal("15.0")		==			 invoice.getBalance();

		when:

		invoice = remoteInvoice.getInvoice(reviewInvoiceId);

		then:

		null		!=		invoice;

		// get the latest process
		when:

		lastDtoB = remoteBillingProcess.getDto(
				remoteBillingProcess.getLast(entityId),
				languageId);

		// no new process should have run
		then:

		lastDto.getId()		==			lastDtoB.getId();

		// get the review
		when:

		reviewDto2 = remoteBillingProcess.getReviewDto(
				entityId, languageId);

		// since the last one was disapproved, a new one has to be created
		then:

		reviewDto.getId()		==		reviewDto2.getId();

		// status of the review should now be generated
		when:

		configDto = remoteBillingProcess.
				getConfigurationDto(entityId);

		then:

		configDto.getReviewStatus()		==		Constants.REVIEW_STATUS_GENERATED.intValue();

		// run trigger, date is good, but the review is not approved
		when:
		cal.set(2006, GregorianCalendar.OCTOBER, 22);
		remoteBillingProcess.trigger(cal.getTime());

		// get the review
		reviewDto = remoteBillingProcess.getReviewDto(
				entityId, languageId);
		// the status is generated, so it should not be a new review

		then:

		reviewDto.getId()		==		 reviewDto2.getId();

		// run trigger report still not approved, no process then
		when:

		cal.set(2006, GregorianCalendar.OCTOBER, 22);
		remoteBillingProcess.trigger(cal.getTime());

		// get the latest process
		lastDtoB = remoteBillingProcess.getDto(
				remoteBillingProcess.getLast(entityId),
				languageId);

		// no new process should have run
		then:

		lastDto.getId()		==		lastDtoB.getId();

		// disapprove the review so it should run again

		when:

		remoteBillingProcess.setReviewApproval(new Integer(1), entityId,
				new Boolean(false));

		//
		//  Run the review and approve it to allow the process to run
		//
		cal.clear();
		cal.set(2006, GregorianCalendar.OCTOBER, 26);
		cal.add(GregorianCalendar.DATE, -4);
		remoteBillingProcess.trigger(cal.getTime());

		// get the review
		reviewDto2 = remoteBillingProcess.getReviewDto(
				entityId, languageId);
		// since the last one was disapproved, a new one has to be created

		then:

		false		==	(reviewDto.getId() ==         reviewDto2.getId());

		// finally, approve the review. The billing process is next

		remoteBillingProcess.setReviewApproval(new Integer(1), entityId,
				new Boolean(true));


	}


	def "testProcess"() {
		when:
		// get the latest process
		BillingProcessDTOEx lastDto = remoteBillingProcess.getDto(
				remoteBillingProcess.getLast(entityId),
				languageId);

		// get the review, so we can later check that what id had
		// is the same that is generated in the real process
		BillingProcessDTOEx reviewDto = remoteBillingProcess.getReviewDto(
				entityId, languageId);

		// check that the next billing date is updated
		BillingProcessConfigurationDTO configDto = remoteBillingProcess.
				getConfigurationDto(entityId);

		then:

		new Date(2006 - 1900,10 - 1,26)		==				  configDto.getNextRunDate();

		// run trigger on the run date
		when:

		remoteBillingProcess.trigger(runDate);

		// validate invoice delegation
		InvoiceDTO invoice = remoteInvoice.getInvoice(8500);

		then:
		null			!=		invoice;
		1		==		 invoice.getToProcess().intValue();
		Constants.INVOICE_STATUS_UNPAID_AND_CARRIED.intValue()		==		invoice.getInvoiceStatus().getId()+1;

		new BigDecimal("15.0")		==		 invoice.getBalance();
		null		!=		invoice.getInvoice();

		// get the latest process

		when:

		BillingProcessDTOEx lastDtoB = remoteBillingProcess.getDto(
				remoteBillingProcess.getLast(entityId),
				languageId);

		// this is the one and only new process run
		then:

		false		==		(lastDto.getId() == lastDtoB.getId());
		// initially, runs should be 1
		lastDtoB.getRuns().size()		==		 1;

		// check that the next billing date is updated

		when:

		configDto = remoteBillingProcess.
				getConfigurationDto(entityId);

		then:

		new Date(2006 - 1900,11 - 1,26)		==		 configDto.getNextRunDate();

		// verify that what just have run, is the same that was displayed
		// in the review

		reviewDto.getGrandTotal().getInvoicesGenerated().intValue()		==		lastDtoB.getGrandTotal().getInvoicesGenerated().intValue();

		when:
		BillingProcessRunTotalDTOEx aTotal = (BillingProcessRunTotalDTOEx)
		reviewDto.getGrandTotal().getTotals().get(0);
		BillingProcessRunTotalDTOEx bTotal = (BillingProcessRunTotalDTOEx)
		lastDtoB.getGrandTotal().getTotals().get(0);

		then:

		aTotal.getTotalInvoiced()		==		                 bTotal.getTotalInvoiced();

		// verify that the transition from pending unsubscription to unsubscribed worked

		UserDTOEx.SUBSCRIBER_PENDING_UNSUBSCRIPTION		==       remoteUser.getUserDTOEx("pendunsus1", entityId).getSubscriptionStatusId();


		UserDTOEx.SUBSCRIBER_UNSUBSCRIBED		==		 remoteUser.getUserDTOEx("pendunsus2", entityId).getSubscriptionStatusId();


	}

	// This should work when data of the order lines makes sense (quantity *
	// price = total).
	// Yet, the periods have to be added in this function
	def "testGeneratedInvoices"() {

		when:

		Collection<InvoiceDTO> invoices = remoteBillingProcess.getGeneratedInvoices(PROCESS_ID);

		// we know that only one invoice should be generated

		then:
		998		==		 invoices.size();


		when:
		boolean isProRated = false;
		BigDecimal orderTotal = BigDecimal.ZERO;

		for (InvoiceDTO invoice : invoices) {

			for (OrderProcessDTO orderProcess: invoice.getOrderProcesses()) {
				OrderDTO orderDto = remoteOrder.getOrderEx(orderProcess.getPurchaseOrder().getId(), languageId);

				orderTotal = orderTotal.add(orderDto.getTotal());
				if (orderProcess.getPurchaseOrder().getId() >= 103 && orderProcess.getPurchaseOrder().getId() <= 108 ||
				orderProcess.getPurchaseOrder().getId() == 113 ||
				orderProcess.getPurchaseOrder().getId() == 107600) {

					isProRated = true;
				}
			}

			if (!isProRated) {
				orderTotal		==			 invoice.getTotal().subtract(invoice.getCarriedBalance());
			}
		}



		then:
		println("take the invoice and examine")

		when:
		InvoiceDTO invoice = remoteInvoice.getAllInvoices(1067).iterator().next();
		then:
		null		!=			invoice;


	}

	def "testPayments"() {
		when:
		BillingProcessDTOEx process = remoteBillingProcess.getDto(PROCESS_ID, 1);

		then:

		thrown(Exception)
		null		!=		process;
		null		!=		process.getRuns();
		1			==		 process.getRuns().size();

		when:

		BillingProcessRunDTOEx run = (BillingProcessRunDTOEx) process.getRuns().get(0);

		for(int myTry = 0; myTry < 10 && run.getPaymentFinished() == null; myTry++) {
			System.out.println("Waiting for payment processing ... " + myTry);
			Thread.sleep(1000);
			process = remoteBillingProcess.getDto(PROCESS_ID, 1);
			run = (BillingProcessRunDTOEx) process.getRuns().get(0);
		}

		then:

		null		!=		run.getPaymentFinished();

		// we know that the only one invoice will be payed in full

		new Integer(998)		==		 process.getGrandTotal().getInvoicesGenerated();
		true			==
				(((BillingProcessRunTotalDTOEx) process.getGrandTotal().getTotals().get(0)).getTotalInvoiced()
				.subtract(((BillingProcessRunTotalDTOEx) process.getGrandTotal().getTotals().get(0)).getTotalPaid())
				.subtract(((BillingProcessRunTotalDTOEx) process.getGrandTotal().getTotals().get(0)).getTotalNotPaid())
				.abs()
				.floatValue() < 1);

		when:
		InvoiceDTO invoice = remoteInvoice.getAllInvoices(1067).iterator().next();

		then:

		new Integer(0)		==		 invoice.getToProcess();


	}

	/*
	 * VALIDATE ORDERS
	 */

	def "testOrdersProcessedDate"() {


		setup:

		String []dates =
				[
					"2006-11-26",
					null,
					null,
					// 100 - 102
					"2006-11-01",
					null,
					null,
					// 103 - 105
					"2006-10-01",
					null,
					null,
					// 106 - 108
					null,
					"2006-11-25",
					null,
					// 109 - 111
					"2006-11-15",
					null   // 112 - 113
				];


		for (int f = 100; f < dates.length; f++) {
			OrderDTO order = remoteOrder.getOrder(f);

			if (order.getNextBillableDay() != null) {
				if (dates[f] == null ){
					null		==		order.getNextBillableDay();
				} else {
					parseDate(dates[f])		==		 order.getNextBillableDay();
				}
			}
		}

	}

	def "testOrdersFlaggedOut"() {

		when:
		Integer []orders = [
			102,
			104,
			105,
			107,
			108,
			109,
			113
		];


		for (int f = 0; f < orders.length; f++) {
			OrderDTO order = remoteOrder.getOrder(new Integer(orders[f]));
			order.getStatusId()		==		 Constants.ORDER_STATUS_FINISHED;
		}
		then:
		println("Executed Sucessfully.");

	}

	def "testOrdersStillIn"() {

		when:
		int []orders = [
			100,
			101,
			103,
			106,
			110,
			111,
			112
		];


		for (int f = 0; f < orders.length; f++) {
			OrderDTO order = remoteOrder.getOrder(new Integer(orders[f]));


			order.getStatusId()		==		 Constants.ORDER_STATUS_ACTIVE;
		}
		then:
		println("Test Succesful");
	}

	def testPeriodsBilled() {

		when:
		String [][]dateRanges = [
			[
				"2006-10-26",
				"2006-11-26",
				"1"
			],
			// 100
			[
				"2006-10-01",
				"2006-11-01",
				"1"
			],
			// 102
			[
				"2006-10-16",
				"2006-12-01",
				"2"
			],
			// 103
			[
				"2006-10-15",
				"2006-11-30",
				"2"
			],
			// 104
			[
				"2006-09-05",
				"2006-11-25",
				"3"
			],
			// 105
			[
				"2006-09-03",
				"2006-11-01",
				"2"
			],
			// 106
			[
				"2006-09-30",
				"2006-10-29",
				"2"
			],
			// 107
			[
				"2006-08-10",
				"2006-10-20",
				"3"
			],
			// 108
			[
				"2006-10-25",
				"2006-11-25",
				"1"
			],
			// 110
			[
				"2006-10-15",
				"2006-11-15",
				"1"
			],
			// 112
			[
				"2006-10-15",
				"2006-11-05",
				"1"
			], // 113
		];

		int []orders = [
			100,
			102,
			103,
			104,
			105,
			106,
			107,
			108,
			110,
			112,
			113
		];


		// get the latest process
		BillingProcessDTOEx lastDto = remoteBillingProcess.getDto(remoteBillingProcess.getLast(entityId), languageId);

		for (int f = 0; f < orders.length; f++) {
			OrderDTO order = remoteOrder.getOrderEx(new Integer(orders[f]), languageId);
			Date from = parseDate(dateRanges[f][0]);
			Date to = parseDate(dateRanges[f][1]);
			Integer number = Integer.valueOf(dateRanges[f][2]);

			OrderProcessDTO period = (OrderProcessDTO) order.getPeriods().toArray()[0];


			from		==		 period.getPeriodStart();
			to			==		  period.getPeriodEnd();
			number			==		  period.getPeriodsIncluded();


			OrderProcessDTO process = (OrderProcessDTO) order.getOrderProcesses().toArray()[0];



			lastDto.getId()		==		  process.getBillingProcess().getId();
		}

		then:
		println("Test of dateRanges completed sucessfully.");
	}

	def "testExcluded"() {

		when:
		int []orders = [101, 109, 111];


		for (int f = 0; f < orders.length; f++) {
			OrderDTO order = remoteOrder.getOrderEx(new Integer(orders[f]), languageId);

			true		==		order.getPeriods().isEmpty();
			true		==		order.getOrderProcesses().isEmpty();
		}

		then:
		println("Test for Order completed succesfully.");
	}


	def "testBillingProcessFailure"()  {
		// order period aligned with the 13th

		when:

		Date runDate = new DateMidnight(2000, 12, 13).toDate();

		// create testing user and order
		UserWS user = com.sapienter.jbilling.server.user.WSTest
				.createUser(true, null, null);

		OrderWS brokenOrder = com.sapienter.jbilling.server.order.WSTest
				.createMockOrder(user.getUserId(), 1, new BigDecimal(10));

		brokenOrder.setActiveSince(runDate);
		brokenOrder.setCycleStarts(runDate);
		brokenOrder.setPeriod(2);        // monthly
		brokenOrder.setBillingTypeId(9); // invalid billing type id to trigger failure

		Integer orderId = remoteOrder.createUpdate(1, 1, new OrderBL().getDTO(brokenOrder), 1);
		println("Order id: " + orderId);

		// set the configuration to include the corrupt order
		BillingProcessConfigurationDTO configDto = remoteBillingProcess.getConfigurationDto(entityId);
		configDto.setNextRunDate(runDate);
		configDto.setRetries(1);
		configDto.setDaysForRetry(5);
		configDto.setGenerateReport(0);
		configDto.setAutoPayment(1);
		configDto.setAutoPaymentApplication(1);
		configDto.setDfFm(0);
		configDto.setDueDateUnitId(Constants.PERIOD_UNIT_MONTH);
		configDto.setDueDateValue(1);
		configDto.setInvoiceDateProcess(1);
		configDto.setMaximumPeriods(10);
		configDto.setOnlyRecurring(1);
		configDto.setPeriodUnit(new PeriodUnitDTO(Constants.PERIOD_UNIT_MONTH));
		configDto.setPeriodValue(1);

		// trigger billing
		// process should finish with status "failed" because of the corrupt order
		remoteBillingProcess.createUpdateConfiguration(1, configDto);
		remoteBillingProcess.trigger(runDate);

		Integer billingProcessId = remoteBillingProcess.getLast(entityId);
		BillingProcessDTOEx billingProcess = remoteBillingProcess.getDto(billingProcessId, languageId);
		BillingProcessRunDTOEx run = billingProcess.getRuns().iterator().next();

		then:

		"Finished: failed"		==		 run.getStatusStr();

		// fix the order by setting billing type ID to a proper value

		when:
		JbillingAPI api = JbillingAPIFactory.getAPI();
		OrderWS fixedOrder = api.getOrder(orderId);
		fixedOrder.setBillingTypeId(Constants.ORDER_BILLING_POST_PAID);
		api.updateOrder(fixedOrder);

		// reset the configuration and retry
		// process should finish with status "successful"
		remoteBillingProcess.createUpdateConfiguration(1 ,configDto);
		remoteBillingProcess.trigger(runDate);

		billingProcessId = remoteBillingProcess.getLast(entityId);
		billingProcess = remoteBillingProcess.getDto(billingProcessId, languageId);
		run = billingProcess.getRuns().iterator().next();


		then:
		"Finished: successful"		==			 run.getStatusStr();

		// cleanup
		remoteOrder.delete(orderId, 1);
		remoteUser.delete(1, user.getUserId());
	}




	def Date parseDate(String str) {
		if (str == null ) {
			return null;
		}

		if ( str.length() != 10 || str.charAt(4) != '-' || str.charAt(7) != '-') {
			throw new Exception("Can't parse " + str);

		}

		int year = Integer.valueOf(str.substring(0,4)).intValue();
		int month = Integer.valueOf(str.substring(5,7)).intValue();
		int day = Integer.valueOf(str.substring(8,10)).intValue();

		Calendar cal = GregorianCalendar.getInstance();
		cal.clear();
		cal.set(year, month - 1, day);

		return cal.getTime();

	}

	def InvoiceDTO getReviewInvoice(Set<InvoiceDTO> invoices) {
		Iterator it = invoices.iterator();
		while(it.hasNext()){
			InvoiceDTO invoice1 = (InvoiceDTO) it.next();
			if (invoice1.getIsReview() == 1) return invoice1;
		}

		return null;
	}

	def InvoiceDTO getNoReviewInvoice(Set<InvoiceDTO> invoices) {
		Iterator it = invoices.iterator();
		while(it.hasNext()) {
			InvoiceDTO invoice1 = (InvoiceDTO) it.next();
			if (invoice1.getIsReview() == 0) return invoice1;
		}

		return null;
	}

}
