/*
 jBilling - The Enterprise Open Source Billing System
 Copyright (C) 2003-2011 Enterprise jBilling Software Ltd. and Emiliano Conde

 This file is part of jbilling.

 jbilling is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 jbilling is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with jbilling.  If not, see <http://www.gnu.org/licenses/>.

 This source was modified by Web Data Technologies LLP (www.webdatatechnologies.in) since 15 Nov 2015.
 You may download the latest source from webdataconsulting.github.io.

 */

/*
 * Created on Jul 14, 2004
 *
 */
package com.sapienter.jbilling.client.process;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import com.sapienter.jbilling.common.FormatLogger;
import com.sapienter.jbilling.common.SessionInternalError;
import com.sapienter.jbilling.common.Util;
import com.sapienter.jbilling.server.invoice.IInvoiceSessionBean;
import com.sapienter.jbilling.server.order.IOrderSessionBean;
import com.sapienter.jbilling.server.process.IBillingProcessSessionBean;
import com.sapienter.jbilling.server.user.IUserSessionBean;
import com.sapienter.jbilling.server.util.Context;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

/**
 * @author Emil
 *
 */
public class Trigger implements Job {

    private static final FormatLogger LOG = new FormatLogger(Trigger.class);

    /**
     * Initialize tool Trigger. Load properties from jbilling.properties and set up Quartz job/trigger
     * 
     *  process.time=YYYYMMDD-HHmm (a full date followed by HH is the hours in 24hs format and mm the minutes).
     *  process.frequency=X (where X is an integer >= 0 and < 1440
     *  
     *  The fist property indicates at what time of the day the trigger has to happen for the very first time. After this first 
     *  run you will need X minutes (specified by 'process.frequency') to run the trigger again. Since only the billing process 
     *  can run more than once per day, we need some logic in the ejbTimout method to verify if the call is the first one of the 
     *  day (where it runs all the services) or not (where it runs only the billing process).
     *  
     *  The first property is optional. If it is not present, or its value is null, then the next trigger will happen at 
     *  startup + minutes indicated in 'process.frequency'.
     *
     */
    public static void Initialize() {
        // Load properties from jbilling.properties
        String time = null;
        String frequency = null;

        try {
            time = Util.getSysProp("process.time");
            frequency = Util.getSysProp("process.frequency");
        } catch (Exception e) {
        // just eat it
        }

        // both null or empty, log one message and return
        if ((time == null || time.length() == 0) && (frequency == null || frequency.length() == 0)) {
            LOG.info("No schedule information found.");
            return;
        }

        Date startTime = null;
        int interval = 0;

        // if process.time absents and frequency is zero, fire every 0:00AM
        GregorianCalendar cal = new GregorianCalendar();
        if ((time == null || time.length() == 0) && "0".equals(frequency)) {
            cal.add(Calendar.DATE, 1);
            startTime = Util.truncateDate(cal.getTime());
            interval = 24 * 60;
        } else if (time == null || time.length() == 0) { // process.time is not present, start at startup + frequency
            try {
                interval = Integer.parseInt(frequency);
            } catch (NumberFormatException e) {
                LOG.debug(e);
                LOG.info("Error: %s Schedule does not start.", e.getMessage());

                // Leave
                return;
            }
            cal.add(Calendar.MINUTE, interval);
            startTime = Util.truncateDate(cal.getTime());
        } else { // Its normal one
            DateTimeFormatter df = DateTimeFormat.forPattern("yyyyMMdd-HHmm");
            try {
                interval = Integer.parseInt(frequency);
                if (interval == 0) {
                    LOG.info("The frequency can not be zero when time is specified.");
                    return;
                }
                startTime = df.parseDateTime(time).toDate();
            }
            catch (IllegalArgumentException e) { // This block catches both NumberFormatException and IllegalArgumentException in case of invalid parsing
                LOG.debug(e);
                LOG.info("Error: %s  Schedule does not start.", e.getMessage());
                // Leave
                return;
            }
        }

        // set up trigger
        try {
            JobDetailImpl jbillingJob = new JobDetailImpl("jbilling", Scheduler.DEFAULT_GROUP, Trigger.class);

            SimpleTriggerImpl trigger = new SimpleTriggerImpl("jbillingTrigger",
                    Scheduler.DEFAULT_GROUP,
                    startTime,
                    null,
                    SimpleTrigger.REPEAT_INDEFINITELY,
                    interval * 60 * 1000);

            trigger.setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT);

            JobScheduler.getInstance().getScheduler().scheduleJob(jbillingJob, trigger);
            
        } catch (SchedulerException e) {
            LOG.error("An exception occurred scheduling the jBilling batch processes.",e);
        }
    }

    public static void main(String[] args) {

    }

    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        IBillingProcessSessionBean remoteBillingProcess = null;

        try {
            // get a session for the remote interfaces
            IUserSessionBean remoteUser = (IUserSessionBean) Context.getBean(
                    Context.Name.USER_SESSION);
            IOrderSessionBean remoteOrder = (IOrderSessionBean) Context.getBean(
                    Context.Name.ORDER_SESSION);
            IInvoiceSessionBean remoteInvoice = (IInvoiceSessionBean) 
                    Context.getBean(Context.Name.INVOICE_SESSION);
            
            // determine the date for this run
            Date today = Calendar.getInstance().getTime();
            today = Util.truncateDate(today);

            // Determine if this call first time of today
            boolean firstOfToday = true;
            Date lastFire = ctx.getPreviousFireTime();

            if (lastFire == null) { // Should be first time call
                firstOfToday = true;
            } else {
                lastFire = Util.truncateDate(lastFire);
                firstOfToday = lastFire.before(today);
            }


            // now the ageing process
            if (firstOfToday) {
                if (Util.getSysPropBooleanTrue("process.run_order_expire")) {
                    // finally the orders about to expire notification
                    LOG.info("Starting order notification at %s", Calendar.getInstance().getTime());
                    remoteOrder.reviewNotifications(today);
                    LOG.info("Ended order notification at %s", Calendar.getInstance().getTime());
                }
                if (Util.getSysPropBooleanTrue("process.run_invoice_reminder")) {
                    // the invoice reminders
                    LOG.info("Starting invoice reminders at %s", Calendar.getInstance().getTime());
                    remoteInvoice.sendReminders(today);
                    LOG.info("Ended invoice reminders at %s", Calendar.getInstance().getTime());
                }
                if (Util.getSysPropBooleanTrue("process.run_cc_expire")) {
                    // send credit card expiration emails
                    LOG.info("Starting credit card expiration at %s", Calendar.getInstance().getTime());
                    remoteUser.notifyCreditCardExpiration(today);
                    LOG.info("Ended credit card expiration at %s", Calendar.getInstance().getTime());
                }
            }

        } catch (SessionInternalError e) {
            LOG.debug(e);
        }

    }
}
